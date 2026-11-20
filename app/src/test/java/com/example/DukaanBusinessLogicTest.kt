package com.example

import android.graphics.Bitmap
import com.example.domain.model.HardwareCategory
import com.example.domain.model.PaymentMode
import com.example.domain.model.Product
import com.example.domain.model.ProductUnit
import com.example.domain.model.Purchase
import com.example.domain.model.Sale
import com.example.domain.model.StockStatus
import com.example.ui.PhoneAuthHelper
import com.example.util.CurrencyFormatter
import com.example.util.FinancialCalculator
import com.example.util.ImageCompressor
import com.example.util.IstDateHelper
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DukaanBusinessLogicTest {

    @Test
    fun `indian rupee formatting uses Indian comma grouping`() {
        assertEquals("₹0", CurrencyFormatter.formatInr(0.0))
        assertEquals("₹950", CurrencyFormatter.formatInr(950.0))
        assertEquals("₹12,500", CurrencyFormatter.formatInr(12500.0))
        assertEquals("₹1,25,000", CurrencyFormatter.formatInr(125000.0))
        assertEquals("₹12,34,567.50", CurrencyFormatter.formatInr(1234567.5))
        assertEquals("-₹500", CurrencyFormatter.formatInr(-500.0))
    }

    @Test
    fun `weighted average cost calculates accurately on new stock arrival`() {
        val avg = FinancialCalculator.computeWeightedAverageCost(
            oldStock = 10.0,
            oldAvgCost = 100.0,
            purchasedQty = 10.0,
            newPurchasePrice = 120.0
        )
        assertEquals(110.0, avg, 0.001)
    }

    @Test
    fun `dashboard excludes demo and archived data by default and uses unitCostAtSale`() {
        val nowMs = System.currentTimeMillis()
        val products = listOf(
            Product(
                id = "p1",
                itemCode = "PVC-101",
                name = "PVC Pipe",
                category = HardwareCategory.PIPES_PLUMBING.label,
                unit = ProductUnit.PIECE.label,
                purchasePrice = 220.0,
                averageCost = 200.0,
                sellingPrice = 250.0,
                stockQty = 10.0,
                lowStockLimit = 5.0,
                isDemo = false
            ),
            Product(
                id = "p2",
                itemCode = "CPVC-204",
                name = "CPVC Elbow",
                stockQty = 4.0,
                lowStockLimit = 10.0,
                averageCost = 30.0,
                isDemo = false
            ),
            Product(
                id = "p3",
                itemCode = "NUT-M10",
                name = "Archived Hex Nut",
                stockQty = 0.0,
                lowStockLimit = 5.0,
                averageCost = 140.0,
                archived = true, // Archived must NOT count in outOfStockCount or inventory value
                isDemo = false
            ),
            Product(
                id = "d1",
                itemCode = "DEMO-1",
                name = "Demo Cement",
                stockQty = 50.0,
                lowStockLimit = 5.0,
                averageCost = 400.0,
                isDemo = true // Demo must NOT count when includeDemo = false
            )
        )

        val sales = listOf(
            Sale(
                id = "s1",
                productId = "p1",
                productName = "PVC Pipe",
                itemCode = "PVC-101",
                unit = "Piece",
                quantity = 2.0,
                unitSellingPrice = 250.0,
                unitCostAtSale = 190.0,
                totalAmount = 500.0,
                estimatedProfit = 120.0,
                paymentMode = PaymentMode.CASH.label,
                isDemo = false,
                transactionAt = Timestamp(Date(nowMs - 10_000L))
            ),
            Sale(
                id = "s_demo",
                productId = "d1",
                productName = "Demo Cement",
                itemCode = "DEMO-1",
                unit = "Piece",
                quantity = 5.0,
                unitSellingPrice = 500.0,
                unitCostAtSale = 400.0,
                totalAmount = 2500.0,
                estimatedProfit = 500.0,
                isDemo = true,
                transactionAt = Timestamp(Date(nowMs - 8_000L))
            ),
            Sale(
                id = "s2",
                productId = "p1",
                quantity = 4.0,
                unitSellingPrice = 250.0,
                unitCostAtSale = 190.0,
                totalAmount = 1000.0,
                estimatedProfit = 240.0,
                isDemo = false,
                transactionAt = Timestamp(Date(nowMs - 86_400_000L))
            )
        )

        val purchases = listOf(
            Purchase(
                id = "pu1",
                productId = "p1",
                quantity = 5.0,
                unitPrice = 220.0,
                totalAmount = 1100.0,
                isDemo = false,
                transactionAt = Timestamp(Date(nowMs - 5_000L))
            ),
            Purchase(
                id = "pu_demo",
                productId = "d1",
                quantity = 10.0,
                unitPrice = 400.0,
                totalAmount = 4000.0,
                isDemo = true,
                transactionAt = Timestamp(Date(nowMs - 4_000L))
            )
        )

        val realSummary = FinancialCalculator.computeDashboardSummary(
            products, sales, purchases, nowMs, includeDemo = false
        )
        assertEquals(500.0, realSummary.todaysSalesInr, 0.01)
        assertEquals(1100.0, realSummary.todaysPurchasesInr, 0.01)
        assertEquals(120.0, realSummary.todaysEstimatedProfitInr, 0.01)
        assertEquals(2120.0, realSummary.totalInventoryValueAtCostInr, 0.01)
        assertEquals(1, realSummary.lowStockCount)
        assertEquals(0, realSummary.outOfStockCount)

        val previewSummary = FinancialCalculator.computeDashboardSummary(
            products, sales, purchases, nowMs, includeDemo = true
        )
        assertEquals(3000.0, previewSummary.todaysSalesInr, 0.01)
        assertEquals(5100.0, previewSummary.todaysPurchasesInr, 0.01)
        assertEquals(22120.0, previewSummary.totalInventoryValueAtCostInr, 0.01)

        val real7Days = FinancialCalculator.computeLast7DaysSales(sales, nowMs, includeDemo = false)
        assertEquals(7, real7Days.size)
        assertEquals(500.0, real7Days.last().salesAmountInr, 0.01)

        val topSelling = FinancialCalculator.computeTopSellingProducts(sales, includeDemo = false)
        assertEquals(1, topSelling.size)
        assertEquals("p1", topSelling.first().productId)
        assertEquals(6.0, topSelling.first().totalQtySold, 0.01)
        assertEquals("Asia/Kolkata", IstDateHelper.istTimeZone.id)
    }

    @Test
    fun `image validation rejects videos and compresses large bitmaps under 600px and 120KB`() {
        assertFalse(ImageCompressor.isSupportedImageMimeType("video/mp4"))
        assertFalse(ImageCompressor.isSupportedImageMimeType("video/quicktime"))
        assertFalse(ImageCompressor.isSupportedImageMimeType("application/pdf"))
        assertTrue(ImageCompressor.isSupportedImageMimeType("image/jpeg"))
        assertTrue(ImageCompressor.isSupportedImageMimeType("image/png"))
        assertTrue(ImageCompressor.isSupportedImageMimeType("image/webp"))

        val largeBmp = Bitmap.createBitmap(1600, 1200, Bitmap.Config.ARGB_8888)
        val result = ImageCompressor.compressBitmapToJpegDataUrl(largeBmp)
        largeBmp.recycle()

        assertTrue(result.isSuccess)
        val compressed = result.getOrThrow()
        assertTrue(compressed.dataUrl.startsWith("data:image/jpeg;base64,"))
        assertTrue(compressed.width <= 600)
        assertTrue(compressed.height <= 600)
        assertTrue(compressed.sizeBytes <= 120 * 1024)
        assertTrue(compressed.dataUrl.length <= ImageCompressor.MAX_DATA_URL_CHARS)
    }

    @Test
    fun `phone normalization and legacy category unit mapping work accurately`() {
        assertEquals("+919876543210", PhoneAuthHelper.normalizeIndianPhone("9876543210"))
        assertEquals("+919876543210", PhoneAuthHelper.normalizeIndianPhone("09876543210"))
        assertEquals("+919876543210", PhoneAuthHelper.normalizeIndianPhone("+91 98765-43210"))
        assertNull(PhoneAuthHelper.normalizeIndianPhone("12345"))
        assertEquals("DS-ABC12345", PhoneAuthHelper.shopReferenceId("abc12345xyz"))

        assertEquals(HardwareCategory.TOOLS_ACCESSORIES, HardwareCategory.fromLabel("Tools"))
        assertEquals(HardwareCategory.OTHER_HARDWARE, HardwareCategory.fromLabel("Other Hardware"))
        assertEquals(ProductUnit.METRE, ProductUnit.fromLabel("Meter"))
        assertEquals(ProductUnit.TONNE, ProductUnit.fromLabel("Tonne"))
    }

    @Test
    fun `stock status classifies available low stock and out of stock`() {
        assertEquals(StockStatus.OUT_OF_STOCK, Product(stockQty = 0.0, lowStockLimit = 5.0).stockStatus())
        assertEquals(StockStatus.LOW_STOCK, Product(stockQty = 5.0, lowStockLimit = 5.0).stockStatus())
        assertEquals(StockStatus.AVAILABLE, Product(stockQty = 6.0, lowStockLimit = 5.0).stockStatus())
    }
}
