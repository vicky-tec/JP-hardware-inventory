package com.example.util

import com.example.domain.model.ActivityEntry
import com.example.domain.model.DailySalesBar
import com.example.domain.model.DashboardSummary
import com.example.domain.model.Product
import com.example.domain.model.Purchase
import com.example.domain.model.Sale
import com.example.domain.model.StockAdjustment
import com.example.domain.model.StockStatus
import com.example.domain.model.TopSellingProduct
import com.google.firebase.Timestamp
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.roundToLong

object CurrencyFormatter {
    private val indianLocale = Locale("en", "IN")

    fun formatInr(amount: Double): String {
        val negative = amount < -0.005
        val absVal = abs(amount)
        val rounded = (absVal * 100.0).roundToLong() / 100.0
        val whole = rounded.toLong()
        val paise = ((rounded - whole) * 100.0).roundToLong()

        val wholeFormatted = formatIndianInteger(whole)
        val body = if (paise > 0L) {
            val paiseStr = paise.toString().padStart(2, '0')
            "$wholeFormatted.$paiseStr"
        } else {
            wholeFormatted
        }
        return if (negative) "-₹$body" else "₹$body"
    }

    private fun formatIndianInteger(value: Long): String {
        val digits = value.toString()
        if (digits.length <= 3) return digits
        val lastThree = digits.takeLast(3)
        val remaining = digits.dropLast(3)
        val sb = StringBuilder()
        var idx = remaining.length
        while (idx > 0) {
            val start = (idx - 2).coerceAtLeast(0)
            if (sb.isNotEmpty()) sb.insert(0, ',')
            sb.insert(0, remaining.substring(start, idx))
            idx = start
        }
        sb.append(',').append(lastThree)
        return sb.toString()
    }

    fun formatQty(qty: Double, unit: String): String {
        val formatted = formatNumber(qty)
        return "$formatted $unit"
    }

    fun formatNumber(value: Double): String {
        val rounded = (value * 100.0).roundToLong() / 100.0
        return if (abs(rounded - rounded.toLong().toDouble()) < 0.005) {
            rounded.toLong().toString()
        } else {
            val symbols = DecimalFormatSymbols(indianLocale)
            DecimalFormat("0.##", symbols).format(rounded)
        }
    }
}

object IstDateHelper {
    val istTimeZone: TimeZone = TimeZone.getTimeZone("Asia/Kolkata")
    private val indianLocale = Locale("en", "IN")

    fun dayKey(millis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        sdf.timeZone = istTimeZone
        return sdf.format(Date(millis))
    }

    fun shortDayLabel(millis: Long): String {
        val sdf = SimpleDateFormat("EEE", indianLocale)
        sdf.timeZone = istTimeZone
        return sdf.format(Date(millis))
    }

    fun formatIstDateTime(millis: Long): String {
        val sdf = SimpleDateFormat("dd MMM, hh:mm a", indianLocale)
        sdf.timeZone = istTimeZone
        return sdf.format(Date(millis))
    }

    fun formatIstDateHeader(millis: Long = System.currentTimeMillis()): String {
        val sdf = SimpleDateFormat("EEE, dd MMM yyyy", indianLocale)
        sdf.timeZone = istTimeZone
        return sdf.format(Date(millis))
    }

    fun timestampMillis(primary: Timestamp?, fallback: Timestamp?, nowMillis: Long): Long {
        val ts = primary ?: fallback
        return ts?.toDate()?.time ?: nowMillis
    }

    fun last7DaysIstBounds(nowMillis: Long): List<Pair<String, String>> {
        val cal = Calendar.getInstance(istTimeZone, Locale.US)
        cal.timeInMillis = nowMillis
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.add(Calendar.DAY_OF_YEAR, -6)

        val result = ArrayList<Pair<String, String>>(7)
        for (i in 0 until 7) {
            val m = cal.timeInMillis
            result.add(dayKey(m) to shortDayLabel(m))
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return result
    }
}

object FinancialCalculator {
    fun computeWeightedAverageCost(
        oldStock: Double,
        oldAvgCost: Double,
        purchasedQty: Double,
        newPurchasePrice: Double
    ): Double {
        val safeOldStock = oldStock.coerceAtLeast(0.0)
        val safeOldAvg = oldAvgCost.coerceAtLeast(0.0)
        val safePurchasedQty = purchasedQty.coerceAtLeast(0.0)
        val safeNewPrice = newPurchasePrice.coerceAtLeast(0.0)
        val newTotalStock = safeOldStock + safePurchasedQty
        if (newTotalStock <= 0.0) return safeNewPrice
        return ((safeOldStock * safeOldAvg) + (safePurchasedQty * safeNewPrice)) / newTotalStock
    }

    fun computeSaleProfit(
        unitSellingPrice: Double,
        unitCostAtSale: Double,
        quantity: Double
    ): Double {
        return (unitSellingPrice - unitCostAtSale) * quantity
    }

    fun computeInventoryValueAtCost(
        products: List<Product>,
        includeDemo: Boolean = false
    ): Double {
        return products
            .filter { !it.archived && (includeDemo || !it.isDemo) }
            .sumOf { product ->
                val unitCost = if (product.averageCost > 0.0) product.averageCost else product.purchasePrice
                product.stockQty.coerceAtLeast(0.0) * unitCost.coerceAtLeast(0.0)
            }
    }

    fun computeDashboardSummary(
        products: List<Product>,
        sales: List<Sale>,
        purchases: List<Purchase>,
        nowMillis: Long = System.currentTimeMillis(),
        includeDemo: Boolean = false
    ): DashboardSummary {
        val todayKey = IstDateHelper.dayKey(nowMillis)
        val scopedProducts = products.filter { !it.archived && (includeDemo || !it.isDemo) }
        val scopedSales = sales.filter { includeDemo || !it.isDemo }
        val scopedPurchases = purchases.filter { includeDemo || !it.isDemo }

        val todaysSales = scopedSales.filter { sale ->
            val ms = IstDateHelper.timestampMillis(sale.transactionAt, sale.createdAt, nowMillis)
            IstDateHelper.dayKey(ms) == todayKey
        }
        val todaysPurchases = scopedPurchases.filter { purchase ->
            val ms = IstDateHelper.timestampMillis(purchase.transactionAt, purchase.createdAt, nowMillis)
            IstDateHelper.dayKey(ms) == todayKey
        }

        val todaysSalesTotal = todaysSales.sumOf { it.totalAmount }
        val todaysPurchasesTotal = todaysPurchases.sumOf { it.totalAmount }
        val todaysProfit = todaysSales.sumOf { sale ->
            (sale.unitSellingPrice - sale.unitCostAtSale) * sale.quantity
        }
        val inventoryCostValue = computeInventoryValueAtCost(scopedProducts, includeDemo = true)
        val lowStockCount = scopedProducts.count { it.stockStatus() == StockStatus.LOW_STOCK }
        val outOfStockCount = scopedProducts.count { it.stockStatus() == StockStatus.OUT_OF_STOCK }

        return DashboardSummary(
            todaysSalesInr = todaysSalesTotal,
            todaysPurchasesInr = todaysPurchasesTotal,
            todaysEstimatedProfitInr = todaysProfit,
            totalInventoryValueAtCostInr = inventoryCostValue,
            lowStockCount = lowStockCount,
            outOfStockCount = outOfStockCount
        )
    }

    fun computeLast7DaysSales(
        sales: List<Sale>,
        nowMillis: Long = System.currentTimeMillis(),
        includeDemo: Boolean = false
    ): List<DailySalesBar> {
        val todayKey = IstDateHelper.dayKey(nowMillis)
        val dayBuckets = IstDateHelper.last7DaysIstBounds(nowMillis)
        val totalsByDay = HashMap<String, Double>()
        for (sale in sales) {
            if (!includeDemo && sale.isDemo) continue
            val ms = IstDateHelper.timestampMillis(sale.transactionAt, sale.createdAt, nowMillis)
            val key = IstDateHelper.dayKey(ms)
            totalsByDay[key] = (totalsByDay[key] ?: 0.0) + sale.totalAmount
        }
        return dayBuckets.map { (key, label) ->
            DailySalesBar(
                dayKey = key,
                shortLabel = if (key == todayKey) "Today" else label,
                salesAmountInr = totalsByDay[key] ?: 0.0,
                isToday = key == todayKey
            )
        }
    }

    fun computeTopSellingProducts(
        sales: List<Sale>,
        limit: Int = 5,
        includeDemo: Boolean = false
    ): List<TopSellingProduct> {
        val grouped = LinkedHashMap<String, TopSellingProduct>()
        for (sale in sales) {
            if (!includeDemo && sale.isDemo) continue
            val existing = grouped[sale.productId]
            grouped[sale.productId] = if (existing == null) {
                TopSellingProduct(
                    productId = sale.productId,
                    productName = sale.productName,
                    itemCode = sale.itemCode,
                    unit = sale.unit,
                    totalQtySold = sale.quantity,
                    totalRevenueInr = sale.totalAmount
                )
            } else {
                existing.copy(
                    totalQtySold = existing.totalQtySold + sale.quantity,
                    totalRevenueInr = existing.totalRevenueInr + sale.totalAmount
                )
            }
        }
        return grouped.values
            .sortedByDescending { it.totalRevenueInr }
            .take(limit)
    }

    fun buildRecentActivity(
        sales: List<Sale>,
        purchases: List<Purchase>,
        adjustments: List<StockAdjustment> = emptyList(),
        limit: Int = 5,
        nowMillis: Long = System.currentTimeMillis(),
        includeDemo: Boolean = false
    ): List<ActivityEntry> {
        val entries = ArrayList<ActivityEntry>(sales.size + purchases.size + adjustments.size)
        sales.forEach { sale ->
            if (includeDemo || !sale.isDemo) {
                val ms = IstDateHelper.timestampMillis(sale.transactionAt, sale.createdAt, nowMillis)
                entries.add(ActivityEntry.SaleEntry(sale, ms))
            }
        }
        purchases.forEach { purchase ->
            if (includeDemo || !purchase.isDemo) {
                val ms = IstDateHelper.timestampMillis(purchase.transactionAt, purchase.createdAt, nowMillis)
                entries.add(ActivityEntry.PurchaseEntry(purchase, ms))
            }
        }
        adjustments.forEach { adj ->
            if (includeDemo || !adj.isDemo) {
                val ms = IstDateHelper.timestampMillis(adj.transactionAt, adj.createdAt, nowMillis)
                entries.add(ActivityEntry.AdjustmentEntry(adj, ms))
            }
        }
        return entries.sortedByDescending { it.timestampMillis }.take(limit)
    }

    fun buildProductHistory(
        productId: String,
        sales: List<Sale>,
        purchases: List<Purchase>,
        adjustments: List<StockAdjustment>,
        nowMillis: Long = System.currentTimeMillis()
    ): List<ActivityEntry> {
        val entries = ArrayList<ActivityEntry>()
        sales.filter { it.productId == productId }.forEach { sale ->
            val ms = IstDateHelper.timestampMillis(sale.transactionAt, sale.createdAt, nowMillis)
            entries.add(ActivityEntry.SaleEntry(sale, ms))
        }
        purchases.filter { it.productId == productId }.forEach { purchase ->
            val ms = IstDateHelper.timestampMillis(purchase.transactionAt, purchase.createdAt, nowMillis)
            entries.add(ActivityEntry.PurchaseEntry(purchase, ms))
        }
        adjustments.filter { it.productId == productId }.forEach { adj ->
            val ms = IstDateHelper.timestampMillis(adj.transactionAt, adj.createdAt, nowMillis)
            entries.add(ActivityEntry.AdjustmentEntry(adj, ms))
        }
        return entries.sortedByDescending { it.timestampMillis }
    }
}
