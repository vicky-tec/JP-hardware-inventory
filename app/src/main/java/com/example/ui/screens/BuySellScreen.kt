package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.ShopRepository
import com.example.domain.model.PaymentMode
import com.example.domain.model.Product
import com.example.ui.TradeCartLine
import com.example.ui.TradeMode
import com.example.ui.components.ProductThumbnail
import com.example.ui.components.StockStatusBadge
import com.example.ui.theme.BorderStone
import com.example.ui.theme.ForestGreen
import com.example.ui.theme.ForestGreenContainer
import com.example.ui.theme.InfoBlue
import com.example.ui.theme.OnForestGreenContainer
import com.example.ui.theme.OutOfStockRed
import com.example.ui.theme.OutOfStockRedBg
import com.example.ui.theme.WarmStoneSurface
import com.example.util.CurrencyFormatter
import com.example.util.FinancialCalculator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuySellScreen(
    products: List<Product>,
    tradeMode: TradeMode,
    selectedProductId: String?,
    isSubmitting: Boolean,
    onTradeModeChange: (TradeMode) -> Unit,
    onSelectProduct: (String?) -> Unit,
    onConfirmMultiTrade: (
        mode: TradeMode,
        lines: List<TradeCartLine>,
        partyName: String,
        paymentMode: PaymentMode,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) -> Unit
) {
    val activeProducts = remember(products) { products.filter { !it.archived } }
    val selectedProduct = remember(activeProducts, selectedProductId) {
        activeProducts.firstOrNull { it.id == selectedProductId } ?: activeProducts.firstOrNull()
    }

    var quantityText by remember { mutableStateOf("1") }
    var unitPriceText by remember { mutableStateOf("") }
    var partyName by remember { mutableStateOf("") }
    var paymentMode by remember { mutableStateOf(PaymentMode.CASH) }
    var submitError by remember { mutableStateOf<String?>(null) }
    var showProductPicker by remember { mutableStateOf(false) }

    val billLines = remember { mutableStateListOf<TradeCartLine>() }

    // Clear staged cart if switching between Sale and Purchase
    LaunchedEffect(tradeMode) {
        billLines.clear()
        submitError = null
    }

    // Auto-fill unit price whenever selected product or trade mode changes
    LaunchedEffect(selectedProduct?.id, tradeMode) {
        submitError = null
        if (selectedProduct != null) {
            val defaultPrice = if (tradeMode == TradeMode.SALE) {
                selectedProduct.sellingPrice
            } else {
                selectedProduct.purchasePrice
            }
            unitPriceText = CurrencyFormatter.formatNumber(defaultPrice)
        } else {
            unitPriceText = ""
        }
    }

    val quantity = quantityText.toDoubleOrNull() ?: 0.0
    val unitPrice = unitPriceText.toDoubleOrNull() ?: 0.0
    val currentLineTotal = (quantity * unitPrice).coerceAtLeast(0.0)

    val stagedAlreadyQty = selectedProduct?.let { prod ->
        billLines.firstOrNull { it.product.id == prod.id }?.quantity ?: 0.0
    } ?: 0.0

    val insufficientCurrentLineStock = selectedProduct != null &&
        tradeMode == TradeMode.SALE &&
        (quantity + stagedAlreadyQty) > selectedProduct.stockQty

    val allowsDecimal = selectedProduct?.unitEnum()?.allowsDecimal ?: true

    val cartTotalAmount = billLines.sumOf { it.quantity * it.unitPrice }
    val displayGrandTotal = if (billLines.isNotEmpty()) cartTotalAmount else currentLineTotal

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Big Mode Toggle: Record Sale vs Purchase In
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(WarmStoneSurface)
                .border(1.dp, BorderStone, MaterialTheme.shapes.large)
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TradeMode.entries.forEach { mode ->
                val selected = mode == tradeMode
                val activeColor = if (mode == TradeMode.SALE) ForestGreen else InfoBlue
                Surface(
                    color = if (selected) activeColor else Color.Transparent,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .clickable { onTradeModeChange(mode) }
                        .testTag("trade_mode_${mode.name.lowercase()}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (mode == TradeMode.SALE) {
                                Icons.Filled.PointOfSale
                            } else {
                                Icons.Filled.AddShoppingCart
                            },
                            contentDescription = null,
                            tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (mode == TradeMode.SALE) "Record Sale" else "Purchase In",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        if (activeProducts.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderStone, MaterialTheme.shapes.large)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "No active products available",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Add a hardware product (or unarchive an existing item) in the Products tab before recording sales or purchases.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            // Searchable Product Selector Card
            Text(
                text = "Select Hardware Product",
                style = MaterialTheme.typography.titleMedium
            )
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, ForestGreen.copy(alpha = 0.45f), MaterialTheme.shapes.large)
                    .clip(MaterialTheme.shapes.large)
                    .clickable { showProductPicker = true }
                    .testTag("trade_product_selector")
            ) {
                if (selectedProduct != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ProductThumbnail(
                            imageDataUrl = selectedProduct.imageDataUrl,
                            category = selectedProduct.categoryEnum(),
                            productName = selectedProduct.name,
                            size = 56.dp
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${selectedProduct.itemCode} • ${selectedProduct.name}",
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Available: ${CurrencyFormatter.formatQty(selectedProduct.stockQty, selectedProduct.unit)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (selectedProduct.stockQty <= 0.0) OutOfStockRed else ForestGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Icon(
                            imageVector = Icons.Filled.ArrowDropDown,
                            contentDescription = "Choose product",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            // Quantity & Unit Price
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = quantityText,
                    onValueChange = {
                        submitError = null
                        quantityText = sanitizeDecimalInput(it, allowsDecimal)
                    },
                    label = {
                        Text("Quantity (${selectedProduct?.unit ?: "Qty"})")
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (allowsDecimal) KeyboardType.Decimal else KeyboardType.Number
                    ),
                    isError = insufficientCurrentLineStock,
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("trade_quantity_input")
                )

                OutlinedTextField(
                    value = unitPriceText,
                    onValueChange = {
                        submitError = null
                        unitPriceText = sanitizeDecimalInput(it, true)
                    },
                    label = {
                        Text(if (tradeMode == TradeMode.SALE) "Sell Price ₹" else "Buy Price ₹")
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("trade_unit_price_input")
                )
            }

            // Insufficient stock inline blocker warning
            if (insufficientCurrentLineStock && selectedProduct != null) {
                Surface(
                    color = OutOfStockRedBg,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, OutOfStockRed.copy(alpha = 0.4f), MaterialTheme.shapes.medium)
                        .testTag("insufficient_stock_warning")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ErrorOutline,
                            contentDescription = "Insufficient stock",
                            tint = OutOfStockRed
                        )
                        Text(
                            text = "Only ${CurrencyFormatter.formatQty(selectedProduct.stockQty, selectedProduct.unit)} available in stock. Cannot sell more than available stock.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = OutOfStockRed,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Add Item to Multi-Item Bill Button
            val canAddLine = selectedProduct != null &&
                quantity > 0.0 &&
                unitPrice >= 0.0 &&
                !insufficientCurrentLineStock &&
                (billLines.any { it.product.id == selectedProduct.id } || billLines.size < ShopRepository.MAX_ITEMS_PER_BILL)

            OutlinedButton(
                onClick = {
                    val prod = selectedProduct ?: return@OutlinedButton
                    submitError = null
                    val existingIdx = billLines.indexOfFirst { it.product.id == prod.id }
                    if (existingIdx >= 0) {
                        val combinedQty = billLines[existingIdx].quantity + quantity
                        if (tradeMode == TradeMode.SALE && combinedQty > prod.stockQty) {
                            submitError = "Combined bill quantity exceeds available stock for ${prod.name}."
                            return@OutlinedButton
                        }
                        billLines[existingIdx] = TradeCartLine(prod, combinedQty, unitPrice)
                    } else {
                        if (billLines.size >= ShopRepository.MAX_ITEMS_PER_BILL) {
                            submitError = "Maximum ${ShopRepository.MAX_ITEMS_PER_BILL} distinct items per bill."
                            return@OutlinedButton
                        }
                        billLines.add(TradeCartLine(prod, quantity, unitPrice))
                    }
                    quantityText = "1"
                },
                enabled = canAddLine && !isSubmitting,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("add_to_bill_button")
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (billLines.isEmpty()) {
                        "Add to Multi-Item Bill (up to ${ShopRepository.MAX_ITEMS_PER_BILL} items)"
                    } else {
                        "+ Add Item to Bill (${billLines.size}/${ShopRepository.MAX_ITEMS_PER_BILL})"
                    }
                )
            }

            // Staged Multi-Item Bill List
            if (billLines.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.5.dp, ForestGreen.copy(alpha = 0.45f), MaterialTheme.shapes.large)
                        .testTag("bill_items_card")
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.ReceiptLong, contentDescription = null, tint = ForestGreen)
                            Text(
                                text = "Bill Items (${billLines.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        HorizontalDivider(color = BorderStone.copy(alpha = 0.6f))
                        billLines.forEachIndexed { idx, line ->
                            val lineTotal = line.quantity * line.unitPrice
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${line.product.itemCode} • ${line.product.name}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${CurrencyFormatter.formatQty(line.quantity, line.product.unit)} × ${CurrencyFormatter.formatInr(line.unitPrice)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = CurrencyFormatter.formatInr(lineTotal),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(
                                    onClick = { billLines.removeAt(idx) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = "Remove ${line.product.name}",
                                        tint = OutOfStockRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Live Total & Cost/Profit Preview Card
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderStone, MaterialTheme.shapes.large)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (tradeMode == TradeMode.SALE) "Total Sale Amount" else "Total Purchase Cost",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = CurrencyFormatter.formatInr(displayGrandTotal),
                            style = MaterialTheme.typography.headlineMedium,
                            color = if (tradeMode == TradeMode.SALE) ForestGreen else InfoBlue,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    if (billLines.isNotEmpty()) {
                        if (tradeMode == TradeMode.SALE) {
                            val totalEstProfit = billLines.sumOf { line ->
                                val unitCost = if (line.product.averageCost > 0.0) line.product.averageCost else line.product.purchasePrice
                                FinancialCalculator.computeSaleProfit(line.unitPrice, unitCost, line.quantity)
                            }
                            Text(
                                text = "Total Est. Gross Profit (${billLines.size} items): ${CurrencyFormatter.formatInr(totalEstProfit)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = "Stock and weighted average cost will update atomically for all ${billLines.size} items.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else if (selectedProduct != null && tradeMode == TradeMode.SALE && quantity > 0.0) {
                        val unitCost = if (selectedProduct.averageCost > 0.0) {
                            selectedProduct.averageCost
                        } else {
                            selectedProduct.purchasePrice
                        }
                        val estProfit = FinancialCalculator.computeSaleProfit(unitPrice, unitCost, quantity)
                        Text(
                            text = "Est. Gross Profit (vs avg cost ${CurrencyFormatter.formatInr(unitCost)}): ${CurrencyFormatter.formatInr(estProfit)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (selectedProduct != null && tradeMode == TradeMode.PURCHASE && quantity > 0.0) {
                        val newAvg = FinancialCalculator.computeWeightedAverageCost(
                            oldStock = selectedProduct.stockQty,
                            oldAvgCost = selectedProduct.averageCost,
                            purchasedQty = quantity,
                            newPurchasePrice = unitPrice
                        )
                        Text(
                            text = "New Weighted Avg Cost: ${CurrencyFormatter.formatInr(newAvg)} • New Stock: ${CurrencyFormatter.formatQty(selectedProduct.stockQty + quantity, selectedProduct.unit)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Optional Party Name
            OutlinedTextField(
                value = partyName,
                onValueChange = { if (it.length <= 100) partyName = it },
                label = {
                    Text(
                        if (tradeMode == TradeMode.SALE) {
                            "Customer / Mistri Name (Optional)"
                        } else {
                            "Supplier / Wholesaler Name (Optional)"
                        }
                    )
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // Payment Mode
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Payment Classification",
                    style = MaterialTheme.typography.titleMedium
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PaymentMode.entries.forEach { mode ->
                        FilterChip(
                            selected = paymentMode == mode,
                            onClick = { paymentMode = mode },
                            label = {
                                Text(
                                    text = mode.label,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ForestGreenContainer,
                                selectedLabelColor = OnForestGreenContainer
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        )
                    }
                }
                if (paymentMode == PaymentMode.CREDIT) {
                    Text(
                        text = "Note: Credit marks this entry for reference only (no customer balance ledger).",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (submitError != null) {
                Surface(
                    color = OutOfStockRedBg,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ErrorOutline,
                            contentDescription = null,
                            tint = OutOfStockRed
                        )
                        Text(
                            text = submitError.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = OutOfStockRed,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            val linesToSubmit: List<TradeCartLine> = if (billLines.isNotEmpty()) {
                billLines.toList()
            } else if (selectedProduct != null && quantity > 0.0 && unitPrice >= 0.0) {
                listOf(TradeCartLine(selectedProduct, quantity, unitPrice))
            } else {
                emptyList()
            }

            val canSubmit = linesToSubmit.isNotEmpty() &&
                !(billLines.isEmpty() && insufficientCurrentLineStock) &&
                !isSubmitting

            Button(
                onClick = {
                    if (linesToSubmit.isEmpty()) return@Button
                    submitError = null
                    onConfirmMultiTrade(
                        tradeMode,
                        linesToSubmit,
                        partyName,
                        paymentMode,
                        {
                            billLines.clear()
                            quantityText = "1"
                            partyName = ""
                        },
                        { err ->
                            submitError = err
                        }
                    )
                },
                enabled = canSubmit,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (tradeMode == TradeMode.SALE) ForestGreen else InfoBlue,
                    contentColor = Color.White
                ),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .testTag("confirm_trade_button")
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.White,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Saving Transaction...", style = MaterialTheme.typography.titleMedium)
                } else {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    val countSuffix = if (billLines.size > 1) " (${billLines.size} items)" else ""
                    Text(
                        text = if (tradeMode == TradeMode.SALE) {
                            "Confirm Sale$countSuffix • ${CurrencyFormatter.formatInr(displayGrandTotal)}"
                        } else {
                            "Confirm Purchase$countSuffix • ${CurrencyFormatter.formatInr(displayGrandTotal)}"
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    if (showProductPicker) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var pickerQuery by remember { mutableStateOf("") }
        val matching = remember(activeProducts, pickerQuery) {
            val q = pickerQuery.trim().lowercase()
            if (q.isEmpty()) activeProducts else activeProducts.filter {
                it.name.lowercase().contains(q) ||
                    it.itemCode.lowercase().contains(q) ||
                    it.brand.lowercase().contains(q)
            }
        }

        ModalBottomSheet(
            onDismissRequest = { showProductPicker = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 540.dp)
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = "Choose Hardware Product",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = pickerQuery,
                    onValueChange = { pickerQuery = it },
                    placeholder = { Text("Search name, brand, or code...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(matching, key = { it.id }) { prod ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, BorderStone, MaterialTheme.shapes.medium)
                                .clip(MaterialTheme.shapes.medium)
                                .clickable {
                                    onSelectProduct(prod.id)
                                    showProductPicker = false
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                ProductThumbnail(
                                    imageDataUrl = prod.imageDataUrl,
                                    category = prod.categoryEnum(),
                                    productName = prod.name,
                                    size = 48.dp
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = prod.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${prod.itemCode} • Stock: ${CurrencyFormatter.formatQty(prod.stockQty, prod.unit)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                StockStatusBadge(status = prod.stockStatus())
                            }
                        }
                    }
                }
            }
        }
    }
}
