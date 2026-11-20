package com.example.domain.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName

enum class HardwareCategory(val label: String) {
    PIPES_PLUMBING("Pipes & Plumbing"),
    IRON_RODS_STEEL("Iron Rods & Steel"),
    NUTS_BOLTS_SCREWS("Nuts, Bolts & Screws"),
    TAPS_SANITARY("Taps & Sanitary"),
    BUILDING_MATERIALS("Building Materials"),
    TOOLS_ACCESSORIES("Tools & Accessories"),
    OTHER_HARDWARE("Other Hardware");

    companion object {
        fun fromLabel(raw: String?): HardwareCategory = when (raw) {
            "Tools", "Tools & Accessories" -> TOOLS_ACCESSORIES
            else -> entries.firstOrNull { it.label == raw } ?: PIPES_PLUMBING
        }
    }
}

enum class ProductUnit(val label: String, val allowsDecimal: Boolean) {
    PIECE("Piece", false),
    KG("Kg", true),
    FEET("Feet", true),
    METRE("Metre", true),
    BOX("Box", false),
    PACKET("Packet", false),
    BUNDLE("Bundle", false),
    TONNE("Tonne", true);

    companion object {
        fun fromLabel(raw: String?): ProductUnit = when (raw) {
            "Meter", "Metre" -> METRE
            else -> entries.firstOrNull { it.label == raw } ?: PIECE
        }
    }
}

enum class PaymentMode(val label: String) {
    CASH("Cash"),
    UPI("UPI"),
    CREDIT("Credit");

    companion object {
        fun fromLabel(raw: String?): PaymentMode =
            entries.firstOrNull { it.label == raw } ?: CASH
    }
}

enum class AdjustmentReason(val label: String) {
    OPENING_STOCK("Opening Stock"),
    DAMAGED_GOODS("Damaged Goods"),
    PHYSICAL_CORRECTION("Physical Correction"),
    CUSTOMER_RETURN("Customer Return"),
    SUPPLIER_RETURN("Supplier Return");

    companion object {
        fun fromLabel(raw: String?): AdjustmentReason =
            entries.firstOrNull { it.label == raw } ?: PHYSICAL_CORRECTION
    }
}

enum class StockStatus(val label: String) {
    AVAILABLE("Available"),
    LOW_STOCK("Low Stock"),
    OUT_OF_STOCK("Not Available")
}

enum class ProductFilter(val label: String) {
    ALL("All"),
    AVAILABLE("Available"),
    LOW_STOCK("Low Stock"),
    OUT_OF_STOCK("Not Available"),
    ARCHIVED("Archived")
}

@Keep
@IgnoreExtraProperties
data class Product(
    @DocumentId val id: String = "",
    val userId: String = "",
    val itemCode: String = "",
    val name: String = "",
    val brand: String = "",
    val sizeSpec: String = "",
    val category: String = HardwareCategory.PIPES_PLUMBING.label,
    val unit: String = ProductUnit.PIECE.label,
    val purchasePrice: Double = 0.0,
    val averageCost: Double = 0.0,
    val sellingPrice: Double = 0.0,
    val stockQty: Double = 0.0,
    val lowStockLimit: Double = 10.0,
    val description: String = "",
    val imageDataUrl: String = "",
    val archived: Boolean = false,
    @get:PropertyName("isDemo")
    @PropertyName("isDemo")
    val isDemo: Boolean = false,
    val demoBatchId: String = "",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun stockStatus(): StockStatus = when {
        stockQty <= 0.0 -> StockStatus.OUT_OF_STOCK
        stockQty <= lowStockLimit -> StockStatus.LOW_STOCK
        else -> StockStatus.AVAILABLE
    }

    fun unitEnum(): ProductUnit = ProductUnit.fromLabel(unit)
    fun categoryEnum(): HardwareCategory = HardwareCategory.fromLabel(category)
}

@Keep
@IgnoreExtraProperties
data class Purchase(
    @DocumentId val id: String = "",
    val userId: String = "",
    val productId: String = "",
    val productName: String = "",
    val itemCode: String = "",
    val unit: String = ProductUnit.PIECE.label,
    val quantity: Double = 0.0,
    val unitPrice: Double = 0.0,
    val totalAmount: Double = 0.0,
    val partyName: String = "",
    val paymentMode: String = PaymentMode.CASH.label,
    val billGroupId: String = "",
    val transactionAt: Timestamp? = null,
    @get:PropertyName("isDemo")
    @PropertyName("isDemo")
    val isDemo: Boolean = false,
    val demoBatchId: String = "",
    val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Sale(
    @DocumentId val id: String = "",
    val userId: String = "",
    val productId: String = "",
    val productName: String = "",
    val itemCode: String = "",
    val unit: String = ProductUnit.PIECE.label,
    val quantity: Double = 0.0,
    val unitSellingPrice: Double = 0.0,
    val unitCostAtSale: Double = 0.0,
    val totalAmount: Double = 0.0,
    val estimatedProfit: Double = 0.0,
    val partyName: String = "",
    val paymentMode: String = PaymentMode.CASH.label,
    val billGroupId: String = "",
    val transactionAt: Timestamp? = null,
    @get:PropertyName("isDemo")
    @PropertyName("isDemo")
    val isDemo: Boolean = false,
    val demoBatchId: String = "",
    val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class StockAdjustment(
    @DocumentId val id: String = "",
    val userId: String = "",
    val productId: String = "",
    val productName: String = "",
    val itemCode: String = "",
    val unit: String = ProductUnit.PIECE.label,
    val previousQty: Double = 0.0,
    val adjustmentDelta: Double = 0.0,
    val resultingQty: Double = 0.0,
    val reason: String = AdjustmentReason.PHYSICAL_CORRECTION.label,
    val notes: String = "",
    val transactionAt: Timestamp? = null,
    @get:PropertyName("isDemo")
    @PropertyName("isDemo")
    val isDemo: Boolean = false,
    val createdAt: Timestamp? = null
)

data class DashboardSummary(
    val todaysSalesInr: Double = 0.0,
    val todaysPurchasesInr: Double = 0.0,
    val todaysEstimatedProfitInr: Double = 0.0,
    val totalInventoryValueAtCostInr: Double = 0.0,
    val lowStockCount: Int = 0,
    val outOfStockCount: Int = 0
)

data class DailySalesBar(
    val dayKey: String,
    val shortLabel: String,
    val salesAmountInr: Double,
    val isToday: Boolean
)

data class TopSellingProduct(
    val productId: String,
    val productName: String,
    val itemCode: String,
    val unit: String,
    val totalQtySold: Double,
    val totalRevenueInr: Double
)

sealed interface ActivityEntry {
    val id: String
    val productId: String
    val productName: String
    val itemCode: String
    val quantity: Double
    val unit: String
    val totalAmount: Double
    val paymentMode: String
    val partyName: String
    val timestampMillis: Long
    val isDemo: Boolean

    data class SaleEntry(val sale: Sale, override val timestampMillis: Long) : ActivityEntry {
        override val id: String get() = "sale_${sale.id}"
        override val productId: String get() = sale.productId
        override val productName: String get() = sale.productName
        override val itemCode: String get() = sale.itemCode
        override val quantity: Double get() = sale.quantity
        override val unit: String get() = sale.unit
        override val totalAmount: Double get() = sale.totalAmount
        override val paymentMode: String get() = sale.paymentMode
        override val partyName: String get() = sale.partyName
        override val isDemo: Boolean get() = sale.isDemo
    }

    data class PurchaseEntry(val purchase: Purchase, override val timestampMillis: Long) : ActivityEntry {
        override val id: String get() = "purchase_${purchase.id}"
        override val productId: String get() = purchase.productId
        override val productName: String get() = purchase.productName
        override val itemCode: String get() = purchase.itemCode
        override val quantity: Double get() = purchase.quantity
        override val unit: String get() = purchase.unit
        override val totalAmount: Double get() = purchase.totalAmount
        override val paymentMode: String get() = purchase.paymentMode
        override val partyName: String get() = purchase.partyName
        override val isDemo: Boolean get() = purchase.isDemo
    }

    data class AdjustmentEntry(val adjustment: StockAdjustment, override val timestampMillis: Long) : ActivityEntry {
        override val id: String get() = "adj_${adjustment.id}"
        override val productId: String get() = adjustment.productId
        override val productName: String get() = adjustment.productName
        override val itemCode: String get() = adjustment.itemCode
        override val quantity: Double get() = adjustment.adjustmentDelta
        override val unit: String get() = adjustment.unit
        override val totalAmount: Double get() = 0.0
        override val paymentMode: String get() = adjustment.reason
        override val partyName: String get() = adjustment.notes
        override val isDemo: Boolean get() = adjustment.isDemo
    }
}
