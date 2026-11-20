package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.domain.model.AdjustmentReason
import com.example.domain.model.Product
import com.example.domain.model.Purchase
import com.example.domain.model.Sale
import com.example.domain.model.StockAdjustment
import com.example.ui.components.StockStatusBadge
import com.example.ui.components.categoryIcon
import com.example.ui.theme.AvailableGreen
import com.example.ui.theme.AvailableGreenBg
import com.example.ui.theme.BorderStone
import com.example.ui.theme.ForestGreen
import com.example.ui.theme.ForestGreenContainer
import com.example.ui.theme.InfoBlue
import com.example.ui.theme.LowStockAmber
import com.example.ui.theme.LowStockAmberBg
import com.example.ui.theme.OnForestGreenContainer
import com.example.ui.theme.OutOfStockRed
import com.example.ui.theme.OutOfStockRedBg
import com.example.ui.theme.WarmStoneSurface
import com.example.util.CurrencyFormatter
import com.example.util.FinancialCalculator
import com.example.util.ImageCompressor

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProductDetailSheet(
    product: Product,
    sales: List<Sale>,
    purchases: List<Purchase>,
    adjustments: List<StockAdjustment>,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onEditClick: () -> Unit,
    onRecordSaleClick: () -> Unit,
    onBuyStockClick: () -> Unit,
    onArchiveToggle: (Boolean) -> Unit,
    onDeleteDemoProduct: (() -> Unit)?,
    onAdjustStock: (
        adjustmentDelta: Double,
        reason: AdjustmentReason,
        notes: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) -> Unit
) {
    BackHandler(onBack = onDismiss)

    var showAdjustDialog by remember { mutableStateOf(false) }
    var showArchiveConfirm by remember { mutableStateOf(false) }
    var showDeleteDemoConfirm by remember { mutableStateOf(false) }

    val history = remember(product.id, sales, purchases, adjustments) {
        FinancialCalculator.buildProductHistory(
            productId = product.id,
            sales = sales,
            purchases = purchases,
            adjustments = adjustments
        )
    }

    val photoBitmap = remember(product.imageDataUrl) {
        ImageCompressor.decodeDataUrlToImageBitmap(product.imageDataUrl)
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = product.name,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1
                        )
                        Text(
                            text = product.itemCode,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onEditClick,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("detail_edit_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Edit product details",
                            tint = ForestGreen
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("detail_lazy_column"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Photo + Key Badges Card
            item {
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
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(WarmStoneSurface)
                                .border(1.dp, BorderStone, RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (photoBitmap != null) {
                                Image(
                                    bitmap = photoBitmap,
                                    contentDescription = "${product.name} photo",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    imageVector = categoryIcon(product.categoryEnum()),
                                    contentDescription = product.category,
                                    tint = ForestGreen,
                                    modifier = Modifier.size(64.dp)
                                )
                            }
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            StockStatusBadge(status = product.stockStatus())
                            Surface(color = WarmStoneSurface, shape = RoundedCornerShape(50)) {
                                Text(
                                    text = product.categoryEnum().label,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                            if (product.brand.isNotBlank()) {
                                Surface(color = AvailableGreenBg, shape = RoundedCornerShape(50)) {
                                    Text(
                                        text = "Brand: ${product.brand}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = AvailableGreen,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            if (product.archived) {
                                Surface(color = OutOfStockRedBg, shape = RoundedCornerShape(50)) {
                                    Text(
                                        text = "ARCHIVED",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = OutOfStockRed,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        if (product.sizeSpec.isNotBlank()) {
                            Text(
                                text = "Size / Spec: ${product.sizeSpec}",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Stock & Pricing Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderStone, MaterialTheme.shapes.large)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Current Available Stock",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = CurrencyFormatter.formatQty(product.stockQty, product.unit),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = ForestGreen,
                                    modifier = Modifier.testTag("detail_stock_qty")
                                )
                            }
                            OutlinedButton(
                                onClick = { showAdjustDialog = true },
                                enabled = !isSubmitting,
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("adjust_stock_button")
                            ) {
                                Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Adjust Stock")
                            }
                        }

                        HorizontalDivider(color = BorderStone.copy(alpha = 0.6f))

                        DetailStatRow("Selling Price", CurrencyFormatter.formatInr(product.sellingPrice))
                        DetailStatRow("Latest Purchase Price", CurrencyFormatter.formatInr(product.purchasePrice))
                        val avgCost = if (product.averageCost > 0.0) product.averageCost else product.purchasePrice
                        DetailStatRow("Weighted Avg Cost", CurrencyFormatter.formatInr(avgCost))
                        DetailStatRow(
                            "Low-Stock Alert Limit",
                            "≤ ${CurrencyFormatter.formatQty(product.lowStockLimit, product.unit)}"
                        )
                    }
                }
            }

            // Verbatim Description Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderStone, MaterialTheme.shapes.large)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Shopkeeper Notes / Description",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (product.description.isNotEmpty()) {
                                product.description
                            } else {
                                "No description or rack location saved yet."
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (product.description.isNotEmpty()) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.testTag("detail_description_text")
                        )
                    }
                }
            }

            // Quick Action Row
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!product.archived) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onRecordSaleClick,
                                colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                            ) {
                                Icon(Icons.Filled.PointOfSale, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Sell")
                            }
                            Button(
                                onClick = onBuyStockClick,
                                colors = ButtonDefaults.buttonColors(containerColor = InfoBlue),
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                            ) {
                                Icon(Icons.Filled.AddShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Purchase In")
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (product.archived) onArchiveToggle(false) else showArchiveConfirm = true
                            },
                            enabled = !isSubmitting,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("archive_product_button")
                        ) {
                            Icon(
                                imageVector = if (product.archived) Icons.Filled.Unarchive else Icons.Filled.Archive,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (product.archived) "Restore Product" else "Archive Product")
                        }

                        if (product.isDemo && onDeleteDemoProduct != null) {
                            OutlinedButton(
                                onClick = { showDeleteDemoConfirm = true },
                                enabled = !isSubmitting,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = null,
                                    tint = OutOfStockRed,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Delete Demo", color = OutOfStockRed)
                            }
                        }
                    }
                }
            }

            // Transaction History
            item {
                Text(
                    text = "Product Ledger History (${history.size})",
                    style = MaterialTheme.typography.titleLarge
                )
            }

            if (history.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, BorderStone, MaterialTheme.shapes.medium)
                    ) {
                        Text(
                            text = "No sales, purchases, or stock adjustments recorded for this item yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(history, key = { it.id }) { entry ->
                    RecentActivityCard(entry = entry)
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    if (showAdjustDialog) {
        AdjustStockDialog(
            product = product,
            isSubmitting = isSubmitting,
            onDismiss = { showAdjustDialog = false },
            onConfirm = { delta, reason, notes, onError ->
                onAdjustStock(
                    delta,
                    reason,
                    notes,
                    { showAdjustDialog = false },
                    onError
                )
            }
        )
    }

    if (showArchiveConfirm) {
        AlertDialog(
            onDismissRequest = { showArchiveConfirm = false },
            title = { Text("Archive ${product.name}?") },
            text = {
                Text("Archived products are hidden from active billing and low-stock alerts while keeping full sales and purchase history intact. You can restore it anytime under the Archived filter.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showArchiveConfirm = false
                    onArchiveToggle(true)
                }) {
                    Text("Archive", color = LowStockAmber, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showArchiveConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDeleteDemoConfirm && onDeleteDemoProduct != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDemoConfirm = false },
            title = { Text("Delete Demo Product?") },
            text = { Text("Remove demo item ${product.name}?") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDemoConfirm = false
                    onDeleteDemoProduct()
                    onDismiss()
                }) {
                    Text("Delete", color = OutOfStockRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDemoConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun DetailStatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdjustStockDialog(
    product: Product,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (
        delta: Double,
        reason: AdjustmentReason,
        notes: String,
        onError: (String) -> Unit
    ) -> Unit
) {
    val allowsDecimal = product.unitEnum().allowsDecimal
    var isIncrease by remember { mutableStateOf(true) }
    var qtyText by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf(AdjustmentReason.PHYSICAL_CORRECTION) }
    var notes by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val qtyValue = qtyText.toDoubleOrNull() ?: 0.0
    val delta = if (isIncrease) qtyValue else -qtyValue
    val resultingQty = product.stockQty + delta
    val invalidNegative = qtyValue > 0.0 && resultingQty < 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Adjust Stock • ${product.itemCode}",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = isIncrease,
                        onClick = { isIncrease = true; errorMsg = null },
                        label = { Text("+ Increase") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ForestGreenContainer,
                            selectedLabelColor = OnForestGreenContainer
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("adjust_increase_chip")
                    )
                    FilterChip(
                        selected = !isIncrease,
                        onClick = { isIncrease = false; errorMsg = null },
                        label = { Text("- Decrease") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = OutOfStockRedBg,
                            selectedLabelColor = OutOfStockRed
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("adjust_decrease_chip")
                    )
                }

                OutlinedTextField(
                    value = qtyText,
                    onValueChange = {
                        errorMsg = null
                        qtyText = sanitizeDecimalInput(it, allowsDecimal)
                    },
                    label = { Text("Adjustment Qty (${product.unit})") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (allowsDecimal) KeyboardType.Decimal else KeyboardType.Number
                    ),
                    singleLine = true,
                    isError = invalidNegative,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("adjust_qty_input")
                )

                // Live Current -> Delta -> Resulting Summary
                Surface(
                    color = if (invalidNegative) OutOfStockRedBg else WarmStoneSurface,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            if (invalidNegative) OutOfStockRed.copy(alpha = 0.5f) else BorderStone,
                            MaterialTheme.shapes.medium
                        )
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        DetailStatRow(
                            "Current Stock",
                            CurrencyFormatter.formatQty(product.stockQty, product.unit)
                        )
                        DetailStatRow(
                            "Adjustment",
                            "${if (isIncrease) "+" else "-"}${CurrencyFormatter.formatQty(qtyValue, product.unit)}"
                        )
                        DetailStatRow(
                            "Resulting Stock",
                            CurrencyFormatter.formatQty(resultingQty, product.unit)
                        )
                    }
                }

                Text(
                    text = "Reason *",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AdjustmentReason.entries.forEach { item ->
                        FilterChip(
                            selected = reason == item,
                            onClick = { reason = item },
                            label = { Text(item.label, style = MaterialTheme.typography.labelMedium) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ForestGreenContainer,
                                selectedLabelColor = OnForestGreenContainer
                            ),
                            modifier = Modifier.testTag("reason_chip_${item.name.lowercase()}")
                        )
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { if (it.length <= 300) notes = it },
                    label = { Text("Optional Notes (e.g. Damaged in rain)") },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMsg != null || invalidNegative) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Filled.ErrorOutline,
                            contentDescription = null,
                            tint = OutOfStockRed,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = errorMsg ?: "Resulting stock cannot go below 0.",
                            style = MaterialTheme.typography.bodySmall,
                            color = OutOfStockRed,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        qtyValue <= 0.0 -> errorMsg = "Enter an adjustment quantity greater than zero."
                        resultingQty < 0.0 -> errorMsg = "Resulting stock cannot be negative."
                        else -> onConfirm(delta, reason, notes) { err -> errorMsg = err }
                    }
                },
                enabled = !isSubmitting && qtyValue > 0.0 && !invalidNegative,
                colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                modifier = Modifier.testTag("confirm_adjust_stock_button")
            ) {
                Text(if (isSubmitting) "Saving..." else "Save Adjustment", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                Text("Cancel")
            }
        }
    )
}
