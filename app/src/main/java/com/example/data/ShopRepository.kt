package com.example.data

import android.content.Context
import android.graphics.Color
import com.example.R
import com.example.domain.model.AdjustmentReason
import com.example.domain.model.HardwareCategory
import com.example.domain.model.PaymentMode
import com.example.domain.model.Product
import com.example.domain.model.ProductUnit
import com.example.domain.model.Purchase
import com.example.domain.model.Sale
import com.example.domain.model.StockAdjustment
import com.example.util.CurrencyFormatter
import com.example.util.FinancialCalculator
import com.example.util.ImageCompressor
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.UUID

class InsufficientStockException(
    val availableQty: Double,
    val unit: String,
    val productName: String = ""
) : IllegalStateException(
    if (productName.isNotBlank()) {
        "Only ${CurrencyFormatter.formatNumber(availableQty)} $unit available for $productName"
    } else {
        "Only ${CurrencyFormatter.formatNumber(availableQty)} $unit available in stock"
    }
)

data class ProductInput(
    val id: String? = null,
    val itemCode: String,
    val name: String,
    val brand: String = "",
    val sizeSpec: String,
    val category: HardwareCategory,
    val unit: ProductUnit,
    val purchasePrice: Double,
    val sellingPrice: Double,
    val stockQty: Double,
    val lowStockLimit: Double,
    val description: String,
    val imageDataUrl: String
)

data class PurchaseLineInput(
    val productId: String,
    val quantity: Double,
    val unitPrice: Double
)

data class SaleLineInput(
    val productId: String,
    val quantity: Double,
    val unitSellingPrice: Double
)

interface ShopRepository {
    fun observeProducts(userId: String): Flow<List<Product>>
    fun observePurchases(userId: String): Flow<List<Purchase>>
    fun observeSales(userId: String): Flow<List<Sale>>
    fun observeStockAdjustments(userId: String): Flow<List<StockAdjustment>>

    suspend fun saveProduct(userId: String, input: ProductInput, existingProducts: List<Product>): Result<String>
    suspend fun setProductArchived(userId: String, productId: String, archived: Boolean): Result<Unit>
    suspend fun deleteProduct(userId: String, productId: String): Result<Unit>

    suspend fun adjustStock(
        userId: String,
        productId: String,
        adjustmentDelta: Double,
        reason: AdjustmentReason,
        notes: String,
        transactionId: String
    ): Result<Unit>

    suspend fun recordPurchase(
        userId: String,
        productId: String,
        quantity: Double,
        unitPrice: Double,
        partyName: String,
        paymentMode: PaymentMode,
        transactionId: String
    ): Result<Unit>

    suspend fun recordPurchaseBill(
        userId: String,
        items: List<PurchaseLineInput>,
        partyName: String,
        paymentMode: PaymentMode,
        billGroupId: String,
        transactionIds: List<String>
    ): Result<Unit>

    suspend fun recordSale(
        userId: String,
        productId: String,
        quantity: Double,
        unitSellingPrice: Double,
        partyName: String,
        paymentMode: PaymentMode,
        transactionId: String
    ): Result<Unit>

    suspend fun recordSaleBill(
        userId: String,
        items: List<SaleLineInput>,
        partyName: String,
        paymentMode: PaymentMode,
        billGroupId: String,
        transactionIds: List<String>
    ): Result<Unit>

    suspend fun loadSampleShopData(userId: String): Result<Boolean>
    suspend fun resetDemoData(userId: String): Result<Int>

    companion object {
        const val MAX_ITEMS_PER_BILL = 8
    }
}

class FirestoreShopRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) : ShopRepository {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(expectedUserId: String): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in before accessing Firestore.")
        check(uid == expectedUserId) { "Signed-in user changed; refusing cross-user operation." }
        return uid
    }

    private fun scopedTransactionId(uid: String, rawId: String): String {
        val cleanRaw = rawId.trim().replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
        require(cleanRaw.isNotBlank()) { "Transaction ID cannot be blank." }
        val prefixed = if (cleanRaw.startsWith("tx_${uid}_")) cleanRaw else "tx_${uid}_$cleanRaw"
        return prefixed.take(128)
    }

    override fun observeProducts(userId: String): Flow<List<Product>> = flow {
        val uid = requireUserId(userId)
        emitAll(
            db.collection("products")
                .whereEqualTo("userId", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Product::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.name.lowercase() }
                }
                .catch { error ->
                    handleFlowError(error, OperationType.LIST, "products", uid)
                }
        )
    }

    override fun observePurchases(userId: String): Flow<List<Purchase>> = flow {
        val uid = requireUserId(userId)
        emitAll(
            db.collection("purchases")
                .whereEqualTo("userId", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Purchase::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending {
                        (it.transactionAt ?: it.createdAt ?: Timestamp(0, 0)).toDate().time
                    }
                }
                .catch { error ->
                    handleFlowError(error, OperationType.LIST, "purchases", uid)
                }
        )
    }

    override fun observeSales(userId: String): Flow<List<Sale>> = flow {
        val uid = requireUserId(userId)
        emitAll(
            db.collection("sales")
                .whereEqualTo("userId", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Sale::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending {
                        (it.transactionAt ?: it.createdAt ?: Timestamp(0, 0)).toDate().time
                    }
                }
                .catch { error ->
                    handleFlowError(error, OperationType.LIST, "sales", uid)
                }
        )
    }

    override fun observeStockAdjustments(userId: String): Flow<List<StockAdjustment>> = flow {
        val uid = requireUserId(userId)
        emitAll(
            db.collection("stock_adjustments")
                .whereEqualTo("userId", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        StockAdjustment::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending {
                        (it.transactionAt ?: it.createdAt ?: Timestamp(0, 0)).toDate().time
                    }
                }
                .catch { error ->
                    handleFlowError(error, OperationType.LIST, "stock_adjustments", uid)
                }
        )
    }

    private fun handleFlowError(
        error: Throwable,
        operationType: OperationType,
        path: String,
        uid: String
    ): Nothing {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, operationType, path)
        }
        throw error
    }

    override suspend fun saveProduct(
        userId: String,
        input: ProductInput,
        existingProducts: List<Product>
    ): Result<String> {
        var docPath = "products"
        return try {
            val uid = requireUserId(userId)
            val cleanCode = input.itemCode.trim().uppercase()
            val cleanName = input.name.trim()
            require(cleanCode.length in 4..9) { "Item code must be 4 to 9 characters." }
            require(cleanName.isNotBlank() && cleanName.length <= 120) { "Product name is required (max 120 chars)." }
            require(input.purchasePrice >= 0.0) { "Purchase price cannot be negative." }
            require(input.sellingPrice >= 0.0) { "Selling price cannot be negative." }
            require(input.stockQty >= 0.0) { "Stock quantity cannot be negative." }
            require(input.lowStockLimit >= 0.0) { "Low stock alert limit cannot be negative." }
            if (input.imageDataUrl.isNotBlank()) {
                require(input.imageDataUrl.startsWith("data:image/jpeg;base64,")) {
                    "Only compressed JPEG product images are supported."
                }
                require(input.imageDataUrl.length <= ImageCompressor.MAX_DATA_URL_CHARS) {
                    "Image exceeds maximum compressed size limit (120 KB)."
                }
            }

            val duplicate = existingProducts.firstOrNull {
                it.itemCode.equals(cleanCode, ignoreCase = true) && it.id != input.id
            }
            require(duplicate == null) {
                "Item code '$cleanCode' is already used by ${duplicate?.name}."
            }

            val isEdit = !input.id.isNullOrBlank()
            if (isEdit) {
                val productId = input.id!!
                val docRef = db.collection("products").document(productId)
                docPath = docRef.path
                val existing = existingProducts.firstOrNull { it.id == productId }
                val avgCost = existing?.averageCost?.takeIf { it > 0.0 } ?: input.purchasePrice

                // IMPORTANT: Stock quantity is never overwritten directly during metadata edits.
                // Use Purchase, Sale, or Adjust Stock so every stock change is audited.
                val updates = mutableMapOf<String, Any>(
                    "itemCode" to cleanCode,
                    "name" to cleanName,
                    "brand" to if (input.brand.isNotBlank()) input.brand.trim().take(80) else FieldValue.delete(),
                    "sizeSpec" to if (input.sizeSpec.isNotBlank()) input.sizeSpec.trim().take(80) else FieldValue.delete(),
                    "category" to input.category.label,
                    "unit" to input.unit.label,
                    "purchasePrice" to input.purchasePrice,
                    "averageCost" to avgCost,
                    "sellingPrice" to input.sellingPrice,
                    "lowStockLimit" to input.lowStockLimit,
                    "description" to if (input.description.isNotEmpty()) input.description.take(1000) else FieldValue.delete(),
                    "imageDataUrl" to if (input.imageDataUrl.isNotBlank()) input.imageDataUrl else FieldValue.delete(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updates).await()
                Result.success(productId)
            } else {
                val docRef = db.collection("products").document()
                docPath = docRef.path
                val payload = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "itemCode" to cleanCode,
                    "name" to cleanName,
                    "category" to input.category.label,
                    "unit" to input.unit.label,
                    "purchasePrice" to input.purchasePrice,
                    "averageCost" to input.purchasePrice,
                    "sellingPrice" to input.sellingPrice,
                    "stockQty" to input.stockQty,
                    "lowStockLimit" to input.lowStockLimit,
                    "archived" to false,
                    "isDemo" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (input.brand.isNotBlank()) payload["brand"] = input.brand.trim().take(80)
                if (input.sizeSpec.isNotBlank()) payload["sizeSpec"] = input.sizeSpec.trim().take(80)
                if (input.description.isNotEmpty()) payload["description"] = input.description.take(1000)
                if (input.imageDataUrl.isNotBlank()) payload["imageDataUrl"] = input.imageDataUrl

                docRef.set(payload).await()
                Result.success(docRef.id)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    override suspend fun setProductArchived(
        userId: String,
        productId: String,
        archived: Boolean
    ): Result<Unit> {
        var docPath = "products/$productId"
        return try {
            requireUserId(userId)
            require(productId.isNotBlank()) { "Product ID cannot be blank." }
            val docRef = db.collection("products").document(productId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "archived" to archived,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    override suspend fun deleteProduct(userId: String, productId: String): Result<Unit> {
        var docPath = "products/$productId"
        return try {
            requireUserId(userId)
            require(productId.isNotBlank()) { "Product ID cannot be blank." }
            val docRef = db.collection("products").document(productId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    override suspend fun adjustStock(
        userId: String,
        productId: String,
        adjustmentDelta: Double,
        reason: AdjustmentReason,
        notes: String,
        transactionId: String
    ): Result<Unit> {
        var docPath = "stock_adjustments/$transactionId"
        return try {
            val uid = requireUserId(userId)
            require(productId.isNotBlank()) { "Product ID cannot be blank." }
            require(adjustmentDelta != 0.0) { "Adjustment quantity must be greater than zero." }
            val txId = scopedTransactionId(uid, transactionId)
            val adjRef = db.collection("stock_adjustments").document(txId)
            val productRef = db.collection("products").document(productId)
            docPath = adjRef.path

            db.runTransaction { transaction ->
                val existingAdj = transaction.get(adjRef)
                if (existingAdj.exists()) {
                    return@runTransaction
                }

                val productSnap = transaction.get(productRef)
                if (!productSnap.exists()) {
                    throw IllegalStateException("Selected product no longer exists.")
                }
                if (productSnap.getString("userId") != uid) {
                    throw SecurityException("Product does not belong to current shop owner.")
                }

                val previousQty = (productSnap.get("stockQty") as? Number)?.toDouble() ?: 0.0
                val unit = productSnap.getString("unit") ?: ProductUnit.PIECE.label
                val productName = productSnap.getString("name").orEmpty()
                val itemCode = productSnap.getString("itemCode").orEmpty()
                val resultingQty = previousQty + adjustmentDelta

                if (resultingQty < 0.0) {
                    throw InsufficientStockException(previousQty, unit, productName)
                }

                transaction.update(
                    productRef,
                    mapOf(
                        "stockQty" to resultingQty,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )

                val adjMap = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "productId" to productId,
                    "productName" to productName,
                    "itemCode" to itemCode,
                    "unit" to unit,
                    "previousQty" to previousQty,
                    "adjustmentDelta" to adjustmentDelta,
                    "resultingQty" to resultingQty,
                    "reason" to reason.label,
                    "transactionAt" to FieldValue.serverTimestamp(),
                    "isDemo" to false,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                if (notes.isNotBlank()) {
                    adjMap["notes"] = notes.trim().take(300)
                }
                transaction.set(adjRef, adjMap)
            }.await()

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
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
        userId = userId,
        items = listOf(PurchaseLineInput(productId, quantity, unitPrice)),
        partyName = partyName,
        paymentMode = paymentMode,
        billGroupId = "",
        transactionIds = listOf(transactionId)
    )

    override suspend fun recordPurchaseBill(
        userId: String,
        items: List<PurchaseLineInput>,
        partyName: String,
        paymentMode: PaymentMode,
        billGroupId: String,
        transactionIds: List<String>
    ): Result<Unit> {
        var docPath = "purchases"
        return try {
            val uid = requireUserId(userId)
            require(items.isNotEmpty()) { "Add at least one item to confirm purchase." }
            require(items.size <= ShopRepository.MAX_ITEMS_PER_BILL) {
                "Maximum ${ShopRepository.MAX_ITEMS_PER_BILL} distinct items allowed per purchase bill."
            }
            require(items.size == transactionIds.size) { "Transaction ID mismatch for purchase items." }
            require(items.map { it.productId }.distinct().size == items.size) {
                "Duplicate product in bill. Combine quantities into one line."
            }
            items.forEach { item ->
                require(item.productId.isNotBlank()) { "Please select a product." }
                require(item.quantity > 0.0) { "Purchase quantity must be greater than zero." }
                require(item.unitPrice >= 0.0) { "Purchase price cannot be negative." }
            }

            val scopedTxIds = transactionIds.map { scopedTransactionId(uid, it) }
            val cleanBillGroupId = billGroupId.trim().replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(128)
            docPath = "purchases/${scopedTxIds.first()}"

            db.runTransaction { transaction ->
                val purchaseRefs = scopedTxIds.map { db.collection("purchases").document(it) }
                val existingFlags = purchaseRefs.map { transaction.get(it).exists() }
                if (existingFlags.all { it }) {
                    return@runTransaction
                }

                // Read ALL product documents BEFORE performing any writes
                val productRefs = items.map { db.collection("products").document(it.productId) }
                val productSnaps = productRefs.map { transaction.get(it) }

                val pendingWrites = items.indices.mapNotNull { idx ->
                    if (existingFlags[idx]) return@mapNotNull null
                    val item = items[idx]
                    val productSnap = productSnaps[idx]
                    if (!productSnap.exists()) {
                        throw IllegalStateException("One of the selected products no longer exists.")
                    }
                    if (productSnap.getString("userId") != uid) {
                        throw SecurityException("Product does not belong to current shop owner.")
                    }

                    val oldStock = (productSnap.get("stockQty") as? Number)?.toDouble() ?: 0.0
                    val oldAvgCost = (productSnap.get("averageCost") as? Number)?.toDouble()
                        ?: ((productSnap.get("purchasePrice") as? Number)?.toDouble() ?: 0.0)
                    val productName = productSnap.getString("name").orEmpty()
                    val itemCode = productSnap.getString("itemCode").orEmpty()
                    val unit = productSnap.getString("unit") ?: ProductUnit.PIECE.label

                    val newTotalStock = oldStock + item.quantity
                    val newAverageCost = FinancialCalculator.computeWeightedAverageCost(
                        oldStock = oldStock,
                        oldAvgCost = oldAvgCost,
                        purchasedQty = item.quantity,
                        newPurchasePrice = item.unitPrice
                    )

                    val purchaseMap = mutableMapOf<String, Any>(
                        "userId" to uid,
                        "productId" to item.productId,
                        "productName" to productName,
                        "itemCode" to itemCode,
                        "unit" to unit,
                        "quantity" to item.quantity,
                        "unitPrice" to item.unitPrice,
                        "totalAmount" to (item.quantity * item.unitPrice),
                        "paymentMode" to paymentMode.label,
                        "transactionAt" to FieldValue.serverTimestamp(),
                        "isDemo" to false,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                    if (partyName.isNotBlank()) {
                        purchaseMap["partyName"] = partyName.trim().take(100)
                    }
                    if (cleanBillGroupId.isNotBlank()) {
                        purchaseMap["billGroupId"] = cleanBillGroupId
                    }

                    Triple(
                        productRefs[idx],
                        mapOf(
                            "stockQty" to newTotalStock,
                            "purchasePrice" to item.unitPrice,
                            "averageCost" to newAverageCost,
                            "updatedAt" to FieldValue.serverTimestamp()
                        ),
                        purchaseRefs[idx] to purchaseMap
                    )
                }

                pendingWrites.forEach { (prodRef, prodUpdate, purchaseWrite) ->
                    transaction.update(prodRef, prodUpdate)
                    transaction.set(purchaseWrite.first, purchaseWrite.second)
                }
            }.await()

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
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
        userId = userId,
        items = listOf(SaleLineInput(productId, quantity, unitSellingPrice)),
        partyName = partyName,
        paymentMode = paymentMode,
        billGroupId = "",
        transactionIds = listOf(transactionId)
    )

    override suspend fun recordSaleBill(
        userId: String,
        items: List<SaleLineInput>,
        partyName: String,
        paymentMode: PaymentMode,
        billGroupId: String,
        transactionIds: List<String>
    ): Result<Unit> {
        var docPath = "sales"
        return try {
            val uid = requireUserId(userId)
            require(items.isNotEmpty()) { "Add at least one item to confirm sale." }
            require(items.size <= ShopRepository.MAX_ITEMS_PER_BILL) {
                "Maximum ${ShopRepository.MAX_ITEMS_PER_BILL} distinct items allowed per sale bill."
            }
            require(items.size == transactionIds.size) { "Transaction ID mismatch for sale items." }
            require(items.map { it.productId }.distinct().size == items.size) {
                "Duplicate product in bill. Combine quantities into one line."
            }
            items.forEach { item ->
                require(item.productId.isNotBlank()) { "Please select a product." }
                require(item.quantity > 0.0) { "Sale quantity must be greater than zero." }
                require(item.unitSellingPrice >= 0.0) { "Selling price cannot be negative." }
            }

            val scopedTxIds = transactionIds.map { scopedTransactionId(uid, it) }
            val cleanBillGroupId = billGroupId.trim().replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(128)
            docPath = "sales/${scopedTxIds.first()}"

            db.runTransaction { transaction ->
                val saleRefs = scopedTxIds.map { db.collection("sales").document(it) }
                val existingFlags = saleRefs.map { transaction.get(it).exists() }
                if (existingFlags.all { it }) {
                    return@runTransaction
                }

                // Read ALL product documents BEFORE validating or writing
                val productRefs = items.map { db.collection("products").document(it.productId) }
                val productSnaps = productRefs.map { transaction.get(it) }

                val pendingWrites = items.indices.mapNotNull { idx ->
                    if (existingFlags[idx]) return@mapNotNull null
                    val item = items[idx]
                    val productSnap = productSnaps[idx]
                    if (!productSnap.exists()) {
                        throw IllegalStateException("One of the selected products no longer exists.")
                    }
                    if (productSnap.getString("userId") != uid) {
                        throw SecurityException("Product does not belong to current shop owner.")
                    }

                    val availableStock = (productSnap.get("stockQty") as? Number)?.toDouble() ?: 0.0
                    val unit = productSnap.getString("unit") ?: ProductUnit.PIECE.label
                    val productName = productSnap.getString("name").orEmpty()
                    if (item.quantity > availableStock) {
                        throw InsufficientStockException(availableStock, unit, productName)
                    }

                    val unitCostAtSale = (productSnap.get("averageCost") as? Number)?.toDouble()
                        ?: ((productSnap.get("purchasePrice") as? Number)?.toDouble() ?: 0.0)
                    val itemCode = productSnap.getString("itemCode").orEmpty()
                    val remainingStock = (availableStock - item.quantity).coerceAtLeast(0.0)
                    val totalAmount = item.quantity * item.unitSellingPrice
                    val estimatedProfit = FinancialCalculator.computeSaleProfit(
                        unitSellingPrice = item.unitSellingPrice,
                        unitCostAtSale = unitCostAtSale,
                        quantity = item.quantity
                    )

                    val saleMap = mutableMapOf<String, Any>(
                        "userId" to uid,
                        "productId" to item.productId,
                        "productName" to productName,
                        "itemCode" to itemCode,
                        "unit" to unit,
                        "quantity" to item.quantity,
                        "unitSellingPrice" to item.unitSellingPrice,
                        "unitCostAtSale" to unitCostAtSale,
                        "totalAmount" to totalAmount,
                        "estimatedProfit" to estimatedProfit,
                        "paymentMode" to paymentMode.label,
                        "transactionAt" to FieldValue.serverTimestamp(),
                        "isDemo" to false,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                    if (partyName.isNotBlank()) {
                        saleMap["partyName"] = partyName.trim().take(100)
                    }
                    if (cleanBillGroupId.isNotBlank()) {
                        saleMap["billGroupId"] = cleanBillGroupId
                    }

                    Triple(
                        productRefs[idx],
                        mapOf(
                            "stockQty" to remainingStock,
                            "updatedAt" to FieldValue.serverTimestamp()
                        ),
                        saleRefs[idx] to saleMap
                    )
                }

                pendingWrites.forEach { (prodRef, prodUpdate, saleWrite) ->
                    transaction.update(prodRef, prodUpdate)
                    transaction.set(saleWrite.first, saleWrite.second)
                }
            }.await()

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    override suspend fun loadSampleShopData(userId: String): Result<Boolean> {
        return try {
            val uid = requireUserId(userId)

            val existingDemoProducts = db.collection("products")
                .whereEqualTo("userId", uid)
                .whereEqualTo("isDemo", true)
                .get()
                .await()

            if (!existingDemoProducts.isEmpty) {
                return Result.success(false)
            }

            val prefix = "demo_${uid.take(12).replace(Regex("[^a-zA-Z0-9_-]"), "")}"
            val demoBatchId = "demo_batch_${UUID.randomUUID().toString().take(8)}"
            val nowMs = System.currentTimeMillis()
            val batch = db.batch()

            data class DemoProductSpec(
                val docId: String,
                val code: String,
                val name: String,
                val brand: String,
                val sizeSpec: String,
                val category: HardwareCategory,
                val unit: ProductUnit,
                val purchasePrice: Double,
                val sellingPrice: Double,
                val stockQty: Double,
                val lowStockLimit: Double,
                val desc: String,
                val bgHex: Int,
                val accentHex: Int,
                val shapeKind: String
            )

            val demoProducts = listOf(
                DemoProductSpec(
                    "${prefix}_p1", "PVC-101", "Astral PVC Pipe", "Astral", "1 inch (25mm) • 10 ft",
                    HardwareCategory.PIPES_PLUMBING, ProductUnit.PIECE, 190.0, 245.0, 45.0, 10.0,
                    "Rack A-1 • Schedule 40 UPVC", Color.rgb(20, 83, 45), Color.rgb(241, 245, 249), "pipe"
                ),
                DemoProductSpec(
                    "${prefix}_p2", "CPVC-204", "Ashirvad CPVC Elbow 90°", "Ashirvad", "3/4 inch (20mm)",
                    HardwareCategory.PIPES_PLUMBING, ProductUnit.PIECE, 28.0, 42.0, 8.0, 15.0,
                    "Bin B-4 • Hot & Cold Water", Color.rgb(180, 83, 9), Color.rgb(254, 243, 199), "elbow"
                ),
                DemoProductSpec(
                    "${prefix}_p3", "ROD-808", "TMT Iron Ring Rod (Sariya)", "Kamdhenu", "8mm • Fe-500D • 12m",
                    HardwareCategory.IRON_RODS_STEEL, ProductUnit.KG, 62.0, 74.0, 320.5, 50.0,
                    "Yard Bay 2 • Stirrup Ring Steel", Color.rgb(31, 41, 55), Color.rgb(203, 213, 225), "rod"
                ),
                DemoProductSpec(
                    "${prefix}_p4", "NUT-M10", "GI Hex Nut & Bolt M10", "Unbrako", "10mm x 40mm • Galvanized",
                    HardwareCategory.NUTS_BOLTS_SCREWS, ProductUnit.PACKET, 140.0, 195.0, 0.0, 5.0,
                    "Drawer C-2 • 50 pcs per packet", Color.rgb(153, 27, 27), Color.rgb(254, 226, 226), "nut"
                ),
                DemoProductSpec(
                    "${prefix}_p5", "TAP-302", "Brass Bib Cock Tap", "Jaquar", "1/2 inch Heavy Chrome",
                    HardwareCategory.TAPS_SANITARY, ProductUnit.PIECE, 260.0, 360.0, 14.0, 5.0,
                    "Showcase Shelf 1 • ISI Marked", Color.rgb(146, 64, 14), Color.rgb(253, 224, 71), "tap"
                ),
                DemoProductSpec(
                    "${prefix}_p6", "TNK-500", "Sintex Overhead Water Tank", "Sintex", "500 Litre • 3-Layer UV",
                    HardwareCategory.PIPES_PLUMBING, ProductUnit.PIECE, 3400.0, 4150.0, 3.0, 2.0,
                    "Godown Roof Lot • Black/White", Color.rgb(15, 23, 42), Color.rgb(56, 189, 248), "tank"
                ),
                DemoProductSpec(
                    "${prefix}_p7", "CEM-050", "UltraTech OPC 53 Cement", "UltraTech", "50 Kg Bag • Fresh Batch",
                    HardwareCategory.BUILDING_MATERIALS, ProductUnit.PIECE, 365.0, 410.0, 6.0, 12.0,
                    "Dry Platform 1 • Keep Moisture Free", Color.rgb(68, 64, 60), Color.rgb(231, 229, 228), "cement"
                ),
                DemoProductSpec(
                    "${prefix}_p8", "TOL-410", "Taparia Claw Hammer 500g", "Taparia", "Forged Steel • Fibreglass Grip",
                    HardwareCategory.TOOLS_ACCESSORIES, ProductUnit.PIECE, 290.0, 390.0, 0.0, 4.0,
                    "Tool Pegboard • Heavy Duty", Color.rgb(30, 58, 138), Color.rgb(251, 191, 36), "hammer"
                )
            )

            demoProducts.forEach { spec ->
                val thumbUrl = ImageCompressor.generateHardwareThumbnailDataUrl(
                    bgColorHex = spec.bgHex,
                    accentColorHex = spec.accentHex,
                    codeBadge = spec.code,
                    shapeKind = spec.shapeKind
                )
                val productMap = mapOf(
                    "userId" to uid,
                    "itemCode" to spec.code,
                    "name" to spec.name,
                    "brand" to spec.brand,
                    "sizeSpec" to spec.sizeSpec,
                    "category" to spec.category.label,
                    "unit" to spec.unit.label,
                    "purchasePrice" to spec.purchasePrice,
                    "averageCost" to spec.purchasePrice,
                    "sellingPrice" to spec.sellingPrice,
                    "stockQty" to spec.stockQty,
                    "lowStockLimit" to spec.lowStockLimit,
                    "description" to spec.desc,
                    "imageDataUrl" to thumbUrl,
                    "archived" to false,
                    "isDemo" to true,
                    "demoBatchId" to demoBatchId,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                batch.set(db.collection("products").document(spec.docId), productMap)
            }
            batch.commit().await()

            val ledgerBatch = db.batch()
            val dayMs = 86_400_000L
            data class DemoSaleSpec(
                val docId: String,
                val product: DemoProductSpec,
                val qty: Double,
                val offsetMs: Long,
                val mode: PaymentMode,
                val party: String
            )

            val demoSales = listOf(
                DemoSaleSpec("${prefix}_s1", demoProducts[0], 10.0, 20_000L, PaymentMode.UPI, "Ramesh Contractor"),
                DemoSaleSpec("${prefix}_s2", demoProducts[2], 45.0, 90_000L, PaymentMode.CASH, "Shiva Builders"),
                DemoSaleSpec("${prefix}_s3", demoProducts[4], 3.0, 240_000L, PaymentMode.CASH, "Walk-in Customer"),
                DemoSaleSpec("${prefix}_s4", demoProducts[5], 1.0, 1 * dayMs + 3_600_000L, PaymentMode.UPI, "Patel Residence"),
                DemoSaleSpec("${prefix}_s5", demoProducts[6], 8.0, 2 * dayMs + 7_200_000L, PaymentMode.CREDIT, "Mahesh Mason"),
                DemoSaleSpec("${prefix}_s6", demoProducts[0], 12.0, 3 * dayMs + 5_400_000L, PaymentMode.CASH, "Anil Plumber"),
                DemoSaleSpec("${prefix}_s7", demoProducts[2], 60.0, 4 * dayMs + 10_800_000L, PaymentMode.UPI, "Kiran Construction"),
                DemoSaleSpec("${prefix}_s8", demoProducts[4], 4.0, 5 * dayMs + 4_200_000L, PaymentMode.CASH, "Walk-in Customer")
            )

            demoSales.forEach { s ->
                val ts = Timestamp(Date(nowMs - s.offsetMs))
                val total = s.qty * s.product.sellingPrice
                val profit = (s.product.sellingPrice - s.product.purchasePrice) * s.qty
                val saleMap = mapOf(
                    "userId" to uid,
                    "productId" to s.product.docId,
                    "productName" to s.product.name,
                    "itemCode" to s.product.code,
                    "unit" to s.product.unit.label,
                    "quantity" to s.qty,
                    "unitSellingPrice" to s.product.sellingPrice,
                    "unitCostAtSale" to s.product.purchasePrice,
                    "totalAmount" to total,
                    "estimatedProfit" to profit,
                    "partyName" to s.party,
                    "paymentMode" to s.mode.label,
                    "transactionAt" to ts,
                    "isDemo" to true,
                    "demoBatchId" to demoBatchId,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                ledgerBatch.set(db.collection("sales").document(s.docId), saleMap)
            }

            val demoPurchases = listOf(
                Triple("${prefix}_pu1", demoProducts[0], 20.0) to (45_000L to "Astral Pipes Depot"),
                Triple("${prefix}_pu2", demoProducts[6], 15.0) to (180_000L to "Shree Cement Agency")
            )
            demoPurchases.forEach { (item, meta) ->
                val (docId, product, qty) = item
                val (offsetMs, supplier) = meta
                val purchaseMap = mapOf(
                    "userId" to uid,
                    "productId" to product.docId,
                    "productName" to product.name,
                    "itemCode" to product.code,
                    "unit" to product.unit.label,
                    "quantity" to qty,
                    "unitPrice" to product.purchasePrice,
                    "totalAmount" to (qty * product.purchasePrice),
                    "partyName" to supplier,
                    "paymentMode" to PaymentMode.UPI.label,
                    "transactionAt" to Timestamp(Date(nowMs - offsetMs)),
                    "isDemo" to true,
                    "demoBatchId" to demoBatchId,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                ledgerBatch.set(db.collection("purchases").document(docId), purchaseMap)
            }

            ledgerBatch.commit().await()
            Result.success(true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "demo_seed")
            Result.failure(e)
        }
    }

    override suspend fun resetDemoData(userId: String): Result<Int> {
        return try {
            val uid = requireUserId(userId)
            val collections = listOf("sales", "purchases", "stock_adjustments", "products")
            val refsToDelete = ArrayList<DocumentReference>()

            for (col in collections) {
                val snap = db.collection(col)
                    .whereEqualTo("userId", uid)
                    .whereEqualTo("isDemo", true)
                    .get()
                    .await()
                snap.documents.forEach { doc ->
                    if (doc.getBoolean("isDemo") == true) {
                        refsToDelete.add(doc.reference)
                    }
                }
            }

            refsToDelete.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { batch.delete(it) }
                batch.commit().await()
            }

            Result.success(refsToDelete.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "demo_reset")
            Result.failure(e)
        }
    }
}
