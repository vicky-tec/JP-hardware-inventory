package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.example.data.InsufficientStockException
import com.example.data.ProductInput
import com.example.data.PurchaseLineInput
import com.example.data.SaleLineInput
import com.example.data.ShopRepository
import com.example.domain.model.AdjustmentReason
import com.example.domain.model.HardwareCategory
import com.example.domain.model.PaymentMode
import com.example.domain.model.Product
import com.example.domain.model.ProductUnit
import com.example.domain.model.Purchase
import com.example.domain.model.Sale
import com.example.domain.model.StockAdjustment
import com.example.ui.TradeMode
import com.example.ui.screens.AlertsScreen
import com.example.ui.screens.BuySellScreen
import com.example.ui.screens.ProductDetailSheet
import com.example.ui.screens.ProductFormSheet
import com.example.ui.theme.DukaanStockTheme
import com.example.util.FinancialCalculator
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date
import java.util.UUID

private class FakeShopRepository : ShopRepository {
    val productsFlow = MutableStateFlow<List<Product>>(emptyList())
    val purchasesFlow = MutableStateFlow<List<Purchase>>(emptyList())
    val salesFlow = MutableStateFlow<List<Sale>>(emptyList())
    val adjustmentsFlow = MutableStateFlow<List<StockAdjustment>>(emptyList())
    private val processedTxIds = HashSet<String>()

    override fun observeProducts(userId: String): Flow<List<Product>> =
        productsFlow.map { list -> list.filter { it.userId == userId } }

    override fun observePurchases(userId: String): Flow<List<Purchase>> =
        purchasesFlow.map { list -> list.filter { it.userId == userId } }

    override fun observeSales(userId: String): Flow<List<Sale>> =
        salesFlow.map { list -> list.filter { it.userId == userId } }

    override fun observeStockAdjustments(userId: String): Flow<List<StockAdjustment>> =
        adjustmentsFlow.map { list -> list.filter { it.userId == userId } }

    override suspend fun saveProduct(
        userId: String,
        input: ProductInput,
        existingProducts: List<Product>
    ): Result<String> {
        val dup = existingProducts.firstOrNull {
            it.itemCode.equals(input.itemCode, ignoreCase = true) && it.id != input.id
        }
        if (dup != null) return Result.failure(IllegalArgumentException("Duplicate item code"))

        val id = input.id ?: UUID.randomUUID().toString()
        val now = Timestamp(Date())
        val existing = existingProducts.firstOrNull { it.id == id }
        val updated = if (existing != null) {
            existing.copy(
                itemCode = input.itemCode,
                name = input.name,
                brand = input.brand,
                sizeSpec = input.sizeSpec,
                category = input.category.label,
                unit = input.unit.label,
                purchasePrice = input.purchasePrice,
                sellingPrice = input.sellingPrice,
                lowStockLimit = input.lowStockLimit,
                description = input.description,
                imageDataUrl = input.imageDataUrl,
                updatedAt = now
                // stockQty intentionally NEVER overwritten in edits
            )
        } else {
            Product(
                id = id,
                userId = userId,
                itemCode = input.itemCode,
                name = input.name,
                brand = input.brand,
                sizeSpec = input.sizeSpec,
                category = input.category.label,
                unit = input.unit.label,
                purchasePrice = input.purchasePrice,
                averageCost = input.purchasePrice,
                sellingPrice = input.sellingPrice,
                stockQty = input.stockQty,
                lowStockLimit = input.lowStockLimit,
                description = input.description,
                imageDataUrl = input.imageDataUrl,
                archived = false,
                isDemo = false,
                createdAt = now,
                updatedAt = now
            )
        }
        productsFlow.value = productsFlow.value.filterNot { it.id == id } + updated
        return Result.success(id)
    }

    override suspend fun setProductArchived(userId: String, productId: String, archived: Boolean): Result<Unit> {
        productsFlow.value = productsFlow.value.map {
            if (it.id == productId && it.userId == userId) it.copy(archived = archived) else it
        }
        return Result.success(Unit)
    }

    override suspend fun deleteProduct(userId: String, productId: String): Result<Unit> {
        productsFlow.value = productsFlow.value.filterNot { it.id == productId }
        return Result.success(Unit)
    }

    override suspend fun adjustStock(
        userId: String,
        productId: String,
        adjustmentDelta: Double,
        reason: AdjustmentReason,
        notes: String,
        transactionId: String
    ): Result<Unit> {
        if (!processedTxIds.add("adj_$transactionId")) return Result.success(Unit)
        val prod = productsFlow.value.firstOrNull { it.id == productId && it.userId == userId }
            ?: return Result.failure(IllegalStateException("Missing product"))
        val resulting = prod.stockQty + adjustmentDelta
        if (resulting < 0.0) {
            return Result.failure(InsufficientStockException(prod.stockQty, prod.unit, prod.name))
        }
        productsFlow.value = productsFlow.value.map {
            if (it.id == productId) it.copy(stockQty = resulting) else it
        }
        adjustmentsFlow.value = adjustmentsFlow.value + StockAdjustment(
            id = transactionId,
            userId = userId,
            productId = productId,
            productName = prod.name,
            itemCode = prod.itemCode,
            unit = prod.unit,
            previousQty = prod.stockQty,
            adjustmentDelta = adjustmentDelta,
            resultingQty = resulting,
            reason = reason.label,
            notes = notes,
            transactionAt = Timestamp(Date()),
            isDemo = false,
            createdAt = Timestamp(Date())
        )
        return Result.success(Unit)
    }

    override suspend fun recordPurchase(
        userId: String,
        productId: String,
        quantity: Double,
        unitPrice: Double,
        partyName: String,
        paymentMode: PaymentMode,
        transactionId: String
    ): Result<Unit> = recordPurchaseBill(
        userId, listOf(PurchaseLineInput(productId, quantity, unitPrice)), partyName, paymentMode, "", listOf(transactionId)
    )

    override suspend fun recordPurchaseBill(
        userId: String,
        items: List<PurchaseLineInput>,
        partyName: String,
        paymentMode: PaymentMode,
        billGroupId: String,
        transactionIds: List<String>
    ): Result<Unit> {
        val currentMap = productsFlow.value.associateBy { it.id }.toMutableMap()
        val newPurchases = mutableListOf<Purchase>()
        items.forEachIndexed { idx, line ->
            val txId = transactionIds[idx]
            if (!processedTxIds.add("pur_$txId")) return@forEachIndexed
            val prod = currentMap[line.productId] ?: return Result.failure(IllegalStateException("Missing product"))
            val newAvg = FinancialCalculator.computeWeightedAverageCost(prod.stockQty, prod.averageCost, line.quantity, line.unitPrice)
            val newQty = prod.stockQty + line.quantity
            currentMap[prod.id] = prod.copy(stockQty = newQty, purchasePrice = line.unitPrice, averageCost = newAvg)
            newPurchases.add(
                Purchase(
                    id = txId,
                    userId = userId,
                    productId = prod.id,
                    productName = prod.name,
                    itemCode = prod.itemCode,
                    unit = prod.unit,
                    quantity = line.quantity,
                    unitPrice = line.unitPrice,
                    totalAmount = line.quantity * line.unitPrice,
                    partyName = partyName,
                    paymentMode = paymentMode.label,
                    billGroupId = billGroupId,
                    transactionAt = Timestamp(Date()),
                    isDemo = false,
                    createdAt = Timestamp(Date())
                )
            )
        }
        productsFlow.value = currentMap.values.toList()
        purchasesFlow.value = purchasesFlow.value + newPurchases
        return Result.success(Unit)
    }

    override suspend fun recordSale(
        userId: String,
        productId: String,
        quantity: Double,
        unitSellingPrice: Double,
        partyName: String,
        paymentMode: PaymentMode,
        transactionId: String
    ): Result<Unit> = recordSaleBill(
        userId, listOf(SaleLineInput(productId, quantity, unitSellingPrice)), partyName, paymentMode, "", listOf(transactionId)
    )

    override suspend fun recordSaleBill(
        userId: String,
        items: List<SaleLineInput>,
        partyName: String,
        paymentMode: PaymentMode,
        billGroupId: String,
        transactionIds: List<String>
    ): Result<Unit> {
        val currentMap = productsFlow.value.associateBy { it.id }.toMutableMap()
        // Validate ALL items atomically first
        for (line in items) {
            val prod = currentMap[line.productId] ?: return Result.failure(IllegalStateException("Missing product"))
            if (line.quantity > prod.stockQty) {
                return Result.failure(InsufficientStockException(prod.stockQty, prod.unit, prod.name))
            }
        }
        val newSales = mutableListOf<Sale>()
        items.forEachIndexed { idx, line ->
            val txId = transactionIds[idx]
            if (!processedTxIds.add("sale_$txId")) return@forEachIndexed
            val prod = currentMap[line.productId]!!
            val newQty = prod.stockQty - line.quantity
            currentMap[prod.id] = prod.copy(stockQty = newQty)
            newSales.add(
                Sale(
                    id = txId,
                    userId = userId,
                    productId = prod.id,
                    productName = prod.name,
                    itemCode = prod.itemCode,
                    unit = prod.unit,
                    quantity = line.quantity,
                    unitSellingPrice = line.unitSellingPrice,
                    unitCostAtSale = prod.averageCost,
                    totalAmount = line.quantity * line.unitSellingPrice,
                    estimatedProfit = FinancialCalculator.computeSaleProfit(line.unitSellingPrice, prod.averageCost, line.quantity),
                    partyName = partyName,
                    paymentMode = paymentMode.label,
                    billGroupId = billGroupId,
                    transactionAt = Timestamp(Date()),
                    isDemo = false,
                    createdAt = Timestamp(Date())
                )
            )
        }
        productsFlow.value = currentMap.values.toList()
        salesFlow.value = salesFlow.value + newSales
        return Result.success(Unit)
    }

    override suspend fun loadSampleShopData(userId: String): Result<Boolean> {
        if (productsFlow.value.any { it.userId == userId && it.isDemo }) return Result.success(false)
        val now = Timestamp(Date())
        val demo = (1..8).map { idx ->
            Product(
                id = "demo_$idx",
                userId = userId,
                itemCode = "DEMO-10$idx",
                name = "Hardware Item $idx",
                category = HardwareCategory.PIPES_PLUMBING.label,
                unit = ProductUnit.PIECE.label,
                purchasePrice = 100.0,
                averageCost = 100.0,
                sellingPrice = 150.0,
                stockQty = if (idx <= 2) 0.0 else if (idx <= 4) 3.0 else 25.0,
                lowStockLimit = 5.0,
                isDemo = true,
                demoBatchId = "demo_batch_test",
                createdAt = now,
                updatedAt = now
            )
        }
        productsFlow.value = productsFlow.value + demo
        return Result.success(true)
    }

    override suspend fun resetDemoData(userId: String): Result<Int> {
        val before = productsFlow.value.size
        productsFlow.value = productsFlow.value.filterNot { it.userId == userId && it.isDemo }
        purchasesFlow.value = purchasesFlow.value.filterNot { it.userId == userId && it.isDemo }
        salesFlow.value = salesFlow.value.filterNot { it.userId == userId && it.isDemo }
        adjustmentsFlow.value = adjustmentsFlow.value.filterNot { it.userId == userId && it.isDemo }
        return Result.success(before - productsFlow.value.size)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DukaanUiRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `insufficient stock blocks sale and disables confirm button`() {
        val product = Product(
            id = "p1",
            userId = "u1",
            itemCode = "PVC-101",
            name = "Astral PVC Pipe",
            unit = ProductUnit.PIECE.label,
            purchasePrice = 180.0,
            averageCost = 180.0,
            sellingPrice = 240.0,
            stockQty = 5.0,
            lowStockLimit = 10.0
        )

        composeRule.setContent {
            DukaanStockTheme {
                BuySellScreen(
                    products = listOf(product),
                    tradeMode = TradeMode.SALE,
                    selectedProductId = "p1",
                    isSubmitting = false,
                    onTradeModeChange = {},
                    onSelectProduct = {},
                    onConfirmMultiTrade = { _, _, _, _, _, _ -> }
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("trade_quantity_input").performTextClearance()
        composeRule.onNodeWithTag("trade_quantity_input").performTextInput("8")
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("insufficient_stock_warning").assertIsDisplayed()
        composeRule.onNodeWithTag("confirm_trade_button").assertIsNotEnabled()
    }

    @Test
    fun `multi-item sale bill updates all products atomically and rolls back if one lacks stock`() = kotlinx.coroutines.test.runTest {
        val repo = FakeShopRepository()
        val uid = "owner_1"
        val p1 = repo.saveProduct(
            uid,
            ProductInput(
                itemCode = "PVC-101",
                name = "PVC Pipe",
                sizeSpec = "1 inch",
                category = HardwareCategory.PIPES_PLUMBING,
                unit = ProductUnit.PIECE,
                purchasePrice = 100.0,
                sellingPrice = 140.0,
                stockQty = 10.0,
                lowStockLimit = 2.0,
                description = "",
                imageDataUrl = ""
            ),
            repo.productsFlow.value
        ).getOrThrow()

        val p2 = repo.saveProduct(
            uid,
            ProductInput(
                itemCode = "TAP-302",
                name = "Brass Tap",
                sizeSpec = "1/2 inch",
                category = HardwareCategory.TAPS_SANITARY,
                unit = ProductUnit.PIECE,
                purchasePrice = 200.0,
                sellingPrice = 280.0,
                stockQty = 3.0,
                lowStockLimit = 2.0,
                description = "",
                imageDataUrl = ""
            ),
            repo.productsFlow.value
        ).getOrThrow()

        // First multi-item bill where p2 requests 5 > stock 3 -> entire bill must fail atomically
        val failRes = repo.recordSaleBill(
            userId = uid,
            items = listOf(SaleLineInput(p1, 2.0, 140.0), SaleLineInput(p2, 5.0, 280.0)),
            partyName = "Contractor",
            paymentMode = PaymentMode.CASH,
            billGroupId = "bill_1",
            transactionIds = listOf("tx1", "tx2")
        )
        assertTrue(failRes.isFailure)
        assertEquals(10.0, repo.productsFlow.value.first { it.id == p1 }.stockQty, 0.001)
        assertEquals(3.0, repo.productsFlow.value.first { it.id == p2 }.stockQty, 0.001)

        // Valid multi-item bill -> both items deducted and billGroupId recorded
        val okRes = repo.recordSaleBill(
            userId = uid,
            items = listOf(SaleLineInput(p1, 2.0, 140.0), SaleLineInput(p2, 2.0, 280.0)),
            partyName = "Contractor",
            paymentMode = PaymentMode.UPI,
            billGroupId = "bill_2",
            transactionIds = listOf("tx3", "tx4")
        )
        assertTrue(okRes.isSuccess)
        assertEquals(8.0, repo.productsFlow.value.first { it.id == p1 }.stockQty, 0.001)
        assertEquals(1.0, repo.productsFlow.value.first { it.id == p2 }.stockQty, 0.001)
        assertEquals(2, repo.salesFlow.value.size)
        assertTrue(repo.salesFlow.value.all { it.billGroupId == "bill_2" })
    }

    @Test
    fun `edit product preserves stockQty and verbatim description and adjust stock logs audit`() = kotlinx.coroutines.test.runTest {
        val repo = FakeShopRepository()
        val uid = "owner_1"
        val verbatimDesc = "  Shelf B-2\nAsk Ramesh before bulk discount.  "
        val pId = repo.saveProduct(
            uid,
            ProductInput(
                itemCode = "ROD-808",
                name = "Iron Ring Rod",
                brand = "Kamdhenu",
                sizeSpec = "8mm",
                category = HardwareCategory.IRON_RODS_STEEL,
                unit = ProductUnit.KG,
                purchasePrice = 60.0,
                sellingPrice = 75.0,
                stockQty = 50.0,
                lowStockLimit = 10.0,
                description = verbatimDesc,
                imageDataUrl = ""
            ),
            emptyList()
        ).getOrThrow()

        // Attempt edit passing different stockQty -> must preserve 50.0 and verbatim description
        repo.saveProduct(
            uid,
            ProductInput(
                id = pId,
                itemCode = "ROD-808",
                name = "TMT Iron Ring Rod",
                brand = "Kamdhenu",
                sizeSpec = "8mm Fe500",
                category = HardwareCategory.IRON_RODS_STEEL,
                unit = ProductUnit.KG,
                purchasePrice = 62.0,
                sellingPrice = 78.0,
                stockQty = 999.0,
                lowStockLimit = 10.0,
                description = verbatimDesc,
                imageDataUrl = ""
            ),
            repo.productsFlow.value
        ).getOrThrow()

        val afterEdit = repo.productsFlow.value.first()
        assertEquals(50.0, afterEdit.stockQty, 0.001)
        assertEquals(verbatimDesc, afterEdit.description)

        // Audited stock adjustment (-5 Damaged Goods) succeeds; (-60) rejected
        assertTrue(
            repo.adjustStock(uid, pId, -5.0, AdjustmentReason.DAMAGED_GOODS, "Rusted bundle", "adj1").isSuccess
        )
        assertEquals(45.0, repo.productsFlow.value.first().stockQty, 0.001)
        assertEquals(1, repo.adjustmentsFlow.value.size)

        assertTrue(
            repo.adjustStock(uid, pId, -60.0, AdjustmentReason.PHYSICAL_CORRECTION, "", "adj2").isFailure
        )
        assertEquals(45.0, repo.productsFlow.value.first().stockQty, 0.001)
    }

    @Test
    fun `product form edit mode disables stock field and product detail sheet adjusts stock`() {
        val product = Product(
            id = "p1",
            userId = "u1",
            itemCode = "CEM-050",
            name = "UltraTech Cement",
            brand = "UltraTech",
            sizeSpec = "50 Kg",
            unit = ProductUnit.PIECE.label,
            purchasePrice = 360.0,
            averageCost = 360.0,
            sellingPrice = 410.0,
            stockQty = 20.0,
            lowStockLimit = 5.0,
            description = "Dry platform 1 — exact verbatim note"
        )

        composeRule.setContent {
            DukaanStockTheme {
                ProductFormSheet(
                    editingProduct = product,
                    suggestedCode = "HW-1001",
                    isSubmitting = false,
                    onDismiss = {},
                    onSave = { _, _ -> }
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("product_stock_readonly").assertIsNotEnabled()
    }

    @Test
    fun `product detail sheet opens adjust stock dialog and shows verbatim description`() {
        val product = Product(
            id = "p1",
            userId = "u1",
            itemCode = "CEM-050",
            name = "UltraTech Cement",
            brand = "UltraTech",
            unit = ProductUnit.PIECE.label,
            stockQty = 20.0,
            description = "Dry platform 1 — exact verbatim note"
        )
        var capturedDelta = 0.0
        var capturedReason: AdjustmentReason? = null

        composeRule.setContent {
            DukaanStockTheme {
                ProductDetailSheet(
                    product = product,
                    sales = emptyList(),
                    purchases = emptyList(),
                    adjustments = emptyList(),
                    isSubmitting = false,
                    onDismiss = {},
                    onEditClick = {},
                    onRecordSaleClick = {},
                    onBuyStockClick = {},
                    onArchiveToggle = {},
                    onDeleteDemoProduct = null,
                    onAdjustStock = { delta, reason, _, onSuccess, _ ->
                        capturedDelta = delta
                        capturedReason = reason
                        onSuccess()
                    }
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("detail_lazy_column").performScrollToNode(hasTestTag("detail_description_text"))
        composeRule.onNodeWithTag("detail_description_text").assertIsDisplayed()
        composeRule.onNodeWithTag("detail_lazy_column").performScrollToNode(hasTestTag("adjust_stock_button"))
        composeRule.onNodeWithTag("adjust_stock_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("adjust_decrease_chip").performScrollTo().performClick()
        composeRule.onNodeWithTag("adjust_qty_input").performScrollTo().performTextInput("3")
        composeRule.onNodeWithTag("reason_chip_damaged_goods").performScrollTo().performClick()
        composeRule.onNodeWithTag("confirm_adjust_stock_button").performClick()
        composeRule.waitForIdle()

        assertEquals(-3.0, capturedDelta, 0.001)
        assertEquals(AdjustmentReason.DAMAGED_GOODS, capturedReason)
    }

    @Test
    fun `reset demo data preserves non-demo user products`() = kotlinx.coroutines.test.runTest {
        val repo = FakeShopRepository()
        val uid = "owner_1"

        repo.saveProduct(
            uid,
            ProductInput(
                itemCode = "REAL-101",
                name = "Real Brass Valve",
                sizeSpec = "1 inch",
                category = HardwareCategory.TAPS_SANITARY,
                unit = ProductUnit.PIECE,
                purchasePrice = 300.0,
                sellingPrice = 420.0,
                stockQty = 12.0,
                lowStockLimit = 4.0,
                description = "",
                imageDataUrl = ""
            ),
            repo.productsFlow.value
        )

        assertTrue(repo.loadSampleShopData(uid).getOrThrow())
        assertFalse(repo.loadSampleShopData(uid).getOrThrow())
        assertEquals(9, repo.productsFlow.value.size)

        val removed = repo.resetDemoData(uid).getOrThrow()
        assertEquals(8, removed)
        assertEquals(1, repo.productsFlow.value.size)
        assertEquals("REAL-101", repo.productsFlow.value.first().itemCode)
    }

    @Test
    fun `alerts screen shows out of stock and low stock with restock button`() {
        val outItem = Product(id = "o1", itemCode = "NUT-M10", name = "Hex Nut M10", stockQty = 0.0, lowStockLimit = 5.0)
        val lowItem = Product(id = "l1", itemCode = "CEM-050", name = "OPC Cement", stockQty = 4.0, lowStockLimit = 10.0)
        var restockedId: String? = null

        composeRule.setContent {
            DukaanStockTheme {
                AlertsScreen(
                    outOfStockProducts = listOf(outItem),
                    lowStockProducts = listOf(lowItem),
                    onRestockClick = { restockedId = it.id }
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("Hex Nut M10").assertIsDisplayed()
        composeRule.onNodeWithText("OPC Cement").assertIsDisplayed()
        composeRule.onNodeWithTag("restock_button_NUT-M10").performClick()
        assertEquals("o1", restockedId)
    }

    @Test
    fun `transaction prevents duplicate submission with same transactionId`() = kotlinx.coroutines.test.runTest {
        val repo = FakeShopRepository()
        val uid = "owner_1"
        val prodId = repo.saveProduct(
            uid,
            ProductInput(
                itemCode = "ROD-808",
                name = "Iron Ring Rod",
                sizeSpec = "8mm",
                category = HardwareCategory.IRON_RODS_STEEL,
                unit = ProductUnit.KG,
                purchasePrice = 60.0,
                sellingPrice = 75.0,
                stockQty = 100.0,
                lowStockLimit = 20.0,
                description = "",
                imageDataUrl = ""
            ),
            emptyList()
        ).getOrThrow()

        val txId = "stable-tx-123"
        assertTrue(repo.recordSale(uid, prodId, 10.0, 75.0, "", PaymentMode.CASH, txId).isSuccess)
        assertTrue(repo.recordSale(uid, prodId, 10.0, 75.0, "", PaymentMode.CASH, txId).isSuccess)
        assertEquals(90.0, repo.productsFlow.value.first().stockQty, 0.001)
        assertEquals(1, repo.salesFlow.value.size)

        assertTrue(repo.recordPurchase(uid, prodId, 30.0, 80.0, "Steel Yard", PaymentMode.UPI, "pur-tx-1").isSuccess)
        val updated = repo.productsFlow.value.first()
        assertEquals(120.0, updated.stockQty, 0.001)
        assertEquals(65.0, updated.averageCost, 0.001)
    }
}
