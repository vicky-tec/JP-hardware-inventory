package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.RemoveShoppingCart
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.ActivityEntry
import com.example.domain.model.DailySalesBar
import com.example.domain.model.TopSellingProduct
import com.example.ui.DashboardViewState
import com.example.ui.theme.AvailableGreen
import com.example.ui.theme.AvailableGreenBg
import com.example.ui.theme.BorderStone
import com.example.ui.theme.EmeraldAccent
import com.example.ui.theme.ForestGreen
import com.example.ui.theme.InfoBlue
import com.example.ui.theme.InfoBlueBg
import com.example.ui.theme.LowStockAmber
import com.example.ui.theme.LowStockAmberBg
import com.example.ui.theme.OutOfStockRed
import com.example.ui.theme.OutOfStockRedBg
import com.example.ui.theme.WarmStoneSurface
import com.example.util.CurrencyFormatter
import com.example.util.IstDateHelper

@Composable
fun HomeScreen(
    dashboardState: DashboardViewState,
    hasProducts: Boolean,
    hasDemoData: Boolean,
    isDemoBusy: Boolean,
    onToggleDemoPreview: (Boolean) -> Unit,
    onRecordSaleClick: () -> Unit,
    onAddPurchaseClick: () -> Unit,
    onOpenAlertsClick: () -> Unit,
    onLoadSampleDataClick: () -> Unit
) {
    val summary = dashboardState.summary
    val todayDateStr = IstDateHelper.formatIstDateHeader()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // IST Date Header + Quick Actions
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Today in Shop • $todayDateStr (IST)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onRecordSaleClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ForestGreen,
                            contentColor = Color.White
                        ),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .weight(1f)
                            .height(58.dp)
                            .testTag("home_record_sale_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PointOfSale,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "+ Record Sale",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    FilledTonalButton(
                        onClick = onAddPurchaseClick,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = WarmStoneSurface,
                            contentColor = ForestGreen
                        ),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .weight(1f)
                            .height(58.dp)
                            .testTag("home_add_purchase_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AddShoppingCart,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "+ Purchased Stock",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Demo vs Real Data Separation Banner
        if (hasDemoData) {
            item {
                val isPreview = dashboardState.isDemoPreview
                Surface(
                    color = if (isPreview) LowStockAmberBg else WarmStoneSurface,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            if (isPreview) LowStockAmber.copy(alpha = 0.5f) else BorderStone,
                            MaterialTheme.shapes.medium
                        )
                        .testTag("demo_scope_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Science,
                            contentDescription = null,
                            tint = if (isPreview) LowStockAmber else ForestGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isPreview) "Demo Preview Active" else "Real Shop Totals Only",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (isPreview) LowStockAmber else ForestGreen
                            )
                            Text(
                                text = if (isPreview) {
                                    "Showing sample figures together with your items."
                                } else {
                                    "Demo items are excluded from your shop dashboard."
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(
                            onClick = { onToggleDemoPreview(!isPreview) },
                            modifier = Modifier.testTag("toggle_demo_preview_button")
                        ) {
                            Text(
                                text = if (isPreview) "Real Only" else "Preview Demo",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Empty-Shop Welcome Banner when shop has no products yet
        if (!hasProducts) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderStone, MaterialTheme.shapes.large)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Storefront,
                                contentDescription = null,
                                tint = ForestGreen,
                                modifier = Modifier.size(26.dp)
                            )
                            Text(
                                text = "Welcome to JP Hardware",
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                        Text(
                            text = "Start by adding your hardware items in the Products tab, or load 8 sample Indian hardware products to explore right away.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = onLoadSampleDataClick,
                            enabled = !isDemoBusy,
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("empty_load_sample_button")
                        ) {
                            Text(if (isDemoBusy) "Loading..." else "Load Sample Shop Data")
                        }
                    }
                }
            }
        }

        // 6 Large Summary KPI Cards
        item {
            val kpiItems = listOf(
                KpiCardData(
                    title = "Today's Sales",
                    value = CurrencyFormatter.formatInr(summary.todaysSalesInr),
                    subtitle = "Revenue today (IST)",
                    icon = Icons.Filled.PointOfSale,
                    accent = AvailableGreen,
                    bg = AvailableGreenBg,
                    onClick = null
                ),
                KpiCardData(
                    title = "Today's Purchases",
                    value = CurrencyFormatter.formatInr(summary.todaysPurchasesInr),
                    subtitle = "Stock bought today",
                    icon = Icons.Filled.AddShoppingCart,
                    accent = InfoBlue,
                    bg = InfoBlueBg,
                    onClick = null
                ),
                KpiCardData(
                    title = "Est. Today's Profit",
                    value = CurrencyFormatter.formatInr(summary.todaysEstimatedProfitInr),
                    subtitle = "Selling − Cost of Goods Sold",
                    icon = Icons.AutoMirrored.Filled.TrendingUp,
                    accent = if (summary.todaysEstimatedProfitInr >= 0) AvailableGreen else OutOfStockRed,
                    bg = if (summary.todaysEstimatedProfitInr >= 0) AvailableGreenBg else OutOfStockRedBg,
                    onClick = null
                ),
                KpiCardData(
                    title = "Inventory Value (Cost)",
                    value = CurrencyFormatter.formatInr(summary.totalInventoryValueAtCostInr),
                    subtitle = "Active Stock × Avg Cost",
                    icon = Icons.Filled.Inventory2,
                    accent = ForestGreen,
                    bg = WarmStoneSurface,
                    onClick = null
                ),
                KpiCardData(
                    title = "Low Stock Items",
                    value = summary.lowStockCount.toString(),
                    subtitle = "At or below alert limit",
                    icon = Icons.Filled.WarningAmber,
                    accent = LowStockAmber,
                    bg = LowStockAmberBg,
                    onClick = onOpenAlertsClick
                ),
                KpiCardData(
                    title = "Out of Stock",
                    value = summary.outOfStockCount.toString(),
                    subtitle = "Not Available (0 qty)",
                    icon = Icons.Filled.RemoveShoppingCart,
                    accent = OutOfStockRed,
                    bg = OutOfStockRedBg,
                    onClick = onOpenAlertsClick
                )
            )

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val columns = if (maxWidth >= 700.dp) 3 else 2
                val rows = kpiItems.chunked(columns)
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    rows.forEach { rowCards ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            rowCards.forEach { card ->
                                KpiSummaryCard(
                                    data = card,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (rowCards.size < columns) {
                                repeat(columns - rowCards.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }

        // 7-Day Sales Bar Chart
        item {
            SevenDaySalesChartCard(bars = dashboardState.last7DaysSales)
        }

        // Top-Selling Products Card
        if (dashboardState.topSellingProducts.isNotEmpty()) {
            item {
                TopSellingProductsCard(items = dashboardState.topSellingProducts)
            }
        }

        // Recent Activity Header
        item {
            Text(
                text = "Recent Activity (Last 5)",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        if (dashboardState.recentActivity.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderStone, MaterialTheme.shapes.medium)
                ) {
                    Text(
                        text = "No sales, purchases, or stock adjustments recorded yet. Use the buttons above to record your first entry.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(18.dp)
                    )
                }
            }
        } else {
            items(dashboardState.recentActivity, key = { it.id }) { entry ->
                RecentActivityCard(entry = entry)
            }
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }
    }
}

private data class KpiCardData(
    val title: String,
    val value: String,
    val subtitle: String,
    val icon: ImageVector,
    val accent: Color,
    val bg: Color,
    val onClick: (() -> Unit)?
)

@Composable
private fun KpiSummaryCard(
    data: KpiCardData,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large,
        modifier = modifier
            .border(1.dp, BorderStone, MaterialTheme.shapes.large)
            .clip(MaterialTheme.shapes.large)
            .let { mod -> if (data.onClick != null) mod.clickable(onClick = data.onClick) else mod }
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = data.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    color = data.bg,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = data.icon,
                            contentDescription = null,
                            tint = data.accent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Text(
                text = data.value,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = data.subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SevenDaySalesChartCard(bars: List<DailySalesBar>) {
    val maxSale = (bars.maxOfOrNull { it.salesAmountInr } ?: 0.0).coerceAtLeast(1.0)
    val total7Day = bars.sumOf { it.salesAmountInr }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderStone, MaterialTheme.shapes.large)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column {
                Text(
                    text = "Last 7 Days Sales (IST)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "7-day total: ${CurrencyFormatter.formatInr(total7Day)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (bars.isEmpty() || total7Day <= 0.0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .background(WarmStoneSurface.copy(alpha = 0.55f), MaterialTheme.shapes.medium),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Sales chart appears once you record your first sale",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(132.dp)
                ) {
                    val count = bars.size.coerceAtLeast(1)
                    val slotWidth = size.width / count
                    val barWidth = (slotWidth * 0.54f).coerceAtMost(48.dp.toPx())
                    val maxBarHeight = size.height - 16.dp.toPx()

                    drawLine(
                        color = BorderStone,
                        start = Offset(0f, size.height - 2.dp.toPx()),
                        end = Offset(size.width, size.height - 2.dp.toPx()),
                        strokeWidth = 1.5.dp.toPx()
                    )

                    bars.forEachIndexed { idx, bar ->
                        val ratio = (bar.salesAmountInr / maxSale).toFloat().coerceIn(0f, 1f)
                        val barH = if (bar.salesAmountInr > 0.0) {
                            (ratio * maxBarHeight).coerceAtLeast(8.dp.toPx())
                        } else {
                            4.dp.toPx()
                        }
                        val left = idx * slotWidth + (slotWidth - barWidth) / 2f
                        val top = size.height - 2.dp.toPx() - barH

                        drawRoundRect(
                            color = when {
                                bar.salesAmountInr <= 0.0 -> WarmStoneSurface
                                bar.isToday -> ForestGreen
                                else -> EmeraldAccent.copy(alpha = 0.65f)
                            },
                            topLeft = Offset(left, top),
                            size = Size(barWidth, barH),
                            cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    bars.forEach { bar ->
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = bar.shortLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (bar.isToday) ForestGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (bar.isToday) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1
                            )
                            if (bar.salesAmountInr > 0.0) {
                                Text(
                                    text = compactRupees(bar.salesAmountInr),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopSellingProductsCard(items: List<TopSellingProduct>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderStone, MaterialTheme.shapes.large)
            .testTag("top_selling_products_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Top-Selling Hardware Items",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            items.forEachIndexed { index, item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = AvailableGreenBg,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                color = AvailableGreen,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.productName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${item.itemCode} • ${CurrencyFormatter.formatQty(item.totalQtySold, item.unit)} sold",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = CurrencyFormatter.formatInr(item.totalRevenueInr),
                        style = MaterialTheme.typography.titleSmall,
                        color = ForestGreen,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private fun compactRupees(amount: Double): String {
    return when {
        amount >= 100_000 -> String.format("%.1fL", amount / 100_000.0)
        amount >= 1_000 -> String.format("%.1fk", amount / 1_000.0)
        else -> "₹${amount.toInt()}"
    }
}

@Composable
fun RecentActivityCard(entry: ActivityEntry) {
    val (badgeBg, badgeFg, icon, typeLabel) = when (entry) {
        is ActivityEntry.SaleEntry -> ActivityVisual(AvailableGreenBg, AvailableGreen, Icons.Filled.ArrowUpward, "SALE")
        is ActivityEntry.PurchaseEntry -> ActivityVisual(InfoBlueBg, InfoBlue, Icons.Filled.ArrowDownward, "PURCHASE")
        is ActivityEntry.AdjustmentEntry -> ActivityVisual(LowStockAmberBg, LowStockAmber, Icons.Filled.Tune, "ADJUSTMENT")
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderStone, MaterialTheme.shapes.medium)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                color = badgeBg,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = typeLabel,
                        tint = badgeFg,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.productName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val qtyStr = if (entry is ActivityEntry.AdjustmentEntry) {
                    val sign = if (entry.quantity > 0) "+" else ""
                    "$sign${CurrencyFormatter.formatQty(entry.quantity, entry.unit)}"
                } else {
                    CurrencyFormatter.formatQty(entry.quantity, entry.unit)
                }
                val partyPart = if (entry.partyName.isNotBlank()) " • ${entry.partyName}" else ""
                Text(
                    text = "$typeLabel • $qtyStr$partyPart",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${IstDateHelper.formatIstDateTime(entry.timestampMillis)} • ${entry.paymentMode}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (entry is ActivityEntry.AdjustmentEntry) {
                val sign = if (entry.quantity > 0) "+" else ""
                Text(
                    text = "$sign${CurrencyFormatter.formatNumber(entry.quantity)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = badgeFg,
                    fontWeight = FontWeight.ExtraBold
                )
            } else {
                Text(
                    text = CurrencyFormatter.formatInr(entry.totalAmount),
                    style = MaterialTheme.typography.titleMedium,
                    color = badgeFg,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

private data class ActivityVisual(
    val bg: Color,
    val fg: Color,
    val icon: ImageVector,
    val label: String
)
