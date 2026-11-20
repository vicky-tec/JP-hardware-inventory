package com.example.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Dataset
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.FirestoreShopRepository
import com.example.domain.model.Product
import com.example.ui.components.DatabaseStatusPill
import com.example.ui.components.FeedbackBanner
import com.example.ui.screens.AlertsScreen
import com.example.ui.screens.BuySellScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ProductDetailSheet
import com.example.ui.screens.ProductFormSheet
import com.example.ui.screens.ProductsScreen
import com.example.ui.theme.BorderStone
import com.example.ui.theme.ForestGreen
import com.example.ui.theme.ForestGreenContainer
import com.example.ui.theme.OnForestGreenContainer
import com.example.ui.theme.OutOfStockRed
import com.example.ui.theme.OutOfStockRedBg
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay

@Composable
fun DukaanAppShell(
    currentUserId: String,
    userEmail: String?,
    userPhone: String? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: DukaanViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                DukaanViewModel(FirestoreShopRepository(db), currentUserId)
            }
        }
    )

    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val productsState by viewModel.productsState.collectAsStateWithLifecycle()
    val purchasesState by viewModel.purchasesState.collectAsStateWithLifecycle()
    val salesState by viewModel.salesState.collectAsStateWithLifecycle()
    val adjustmentsState by viewModel.stockAdjustmentsState.collectAsStateWithLifecycle()
    val filteredProducts by viewModel.filteredProducts.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val productFilter by viewModel.productFilter.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val dashboardState by viewModel.dashboardState.collectAsStateWithLifecycle()
    val alertProducts by viewModel.alertProducts.collectAsStateWithLifecycle()
    val tradeMode by viewModel.tradeMode.collectAsStateWithLifecycle()
    val selectedTradeProductId by viewModel.selectedTradeProductId.collectAsStateWithLifecycle()
    val isSubmitting by viewModel.isSubmitting.collectAsStateWithLifecycle()
    val isDemoBusy by viewModel.isDemoBusy.collectAsStateWithLifecycle()
    val hasDemoData by viewModel.hasDemoData.collectAsStateWithLifecycle()
    val databaseStatus by viewModel.databaseStatus.collectAsStateWithLifecycle()
    val feedback by viewModel.feedback.collectAsStateWithLifecycle()

    var showMenu by remember { mutableStateOf(false) }
    var showResetDemoConfirm by remember { mutableStateOf(false) }
    var showAccountSheet by remember { mutableStateOf(false) }
    var detailProductId by remember { mutableStateOf<String?>(null) }
    var editingProduct by remember { mutableStateOf<Product?>(null) }
    var showProductSheet by remember { mutableStateOf(false) }

    val totalAlerts = alertProducts.first.size + alertProducts.second.size
    val allProducts = (productsState as? UiState.Success)?.data.orEmpty()
    val allPurchases = (purchasesState as? UiState.Success)?.data.orEmpty()
    val allSales = (salesState as? UiState.Success)?.data.orEmpty()
    val allAdjustments = (adjustmentsState as? UiState.Success)?.data.orEmpty()
    val activeDetailProduct = remember(allProducts, detailProductId) {
        detailProductId?.let { id -> allProducts.firstOrNull { it.id == id } }
    }

    LaunchedEffect(feedback?.id) {
        if (feedback != null) {
            delay(4000)
            viewModel.clearFeedback()
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 720.dp

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.navigationBars,
            topBar = {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.statusBarsPadding()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                color = Color.Transparent,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier
                                    .size(38.dp)
                                    .border(1.dp, BorderStone, MaterialTheme.shapes.small)
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.jp_hardware_logo),
                                    contentDescription = "JP Hardware Logo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(MaterialTheme.shapes.small)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(id = R.string.app_name),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val identityLine = listOfNotNull(
                                    userEmail?.takeIf { it.isNotBlank() },
                                    userPhone?.takeIf { it.isNotBlank() }
                                ).joinToString(" • ")
                                    .ifBlank { PhoneAuthHelper.shopReferenceId(currentUserId) }
                                Text(
                                    text = identityLine,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            DatabaseStatusPill(status = databaseStatus)

                            Box {
                                IconButton(
                                    onClick = { showMenu = true },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("shop_menu_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.MoreVert,
                                        contentDescription = "Shop options & account"
                                    )
                                }

                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Text("Account & Mobile OTP Link", fontWeight = FontWeight.SemiBold)
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Filled.Badge, contentDescription = null, tint = ForestGreen)
                                        },
                                        onClick = {
                                            showMenu = false
                                            showAccountSheet = true
                                        },
                                        modifier = Modifier.testTag("menu_account_sheet")
                                    )
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                if (isDemoBusy) "Working..." else "Load Sample Shop Data",
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Filled.Dataset, contentDescription = null, tint = ForestGreen)
                                        },
                                        enabled = !isDemoBusy,
                                        onClick = {
                                            showMenu = false
                                            viewModel.loadSampleShopData()
                                        },
                                        modifier = Modifier.testTag("menu_load_sample_data")
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                "Reset Demo Data",
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Filled.DeleteSweep, contentDescription = null)
                                        },
                                        enabled = !isDemoBusy && hasDemoData,
                                        onClick = {
                                            showMenu = false
                                            showResetDemoConfirm = true
                                        },
                                        modifier = Modifier.testTag("menu_reset_demo_data")
                                    )
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text("Sign Out") },
                                        leadingIcon = {
                                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                                        },
                                        onClick = {
                                            showMenu = false
                                            signOutUser(credentialManager, scope)
                                        },
                                        modifier = Modifier.testTag("menu_sign_out")
                                    )
                                }
                            }
                        }
                        HorizontalDivider(color = BorderStone.copy(alpha = 0.6f))
                    }
                }
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 4.dp,
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        AppTab.entries.forEach { tab ->
                            val selected = selectedTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { viewModel.selectTab(tab) },
                                icon = {
                                    TabIconWithBadge(
                                        tab = tab,
                                        selected = selected,
                                        alertCount = totalAlerts
                                    )
                                },
                                label = {
                                    Text(
                                        text = tab.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = OnForestGreenContainer,
                                    selectedTextColor = ForestGreen,
                                    indicatorColor = ForestGreenContainer
                                ),
                                modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Spacer(modifier = Modifier.size(12.dp))
                        AppTab.entries.forEach { tab ->
                            val selected = selectedTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { viewModel.selectTab(tab) },
                                icon = {
                                    TabIconWithBadge(
                                        tab = tab,
                                        selected = selected,
                                        alertCount = totalAlerts
                                    )
                                },
                                label = { Text(tab.label, fontWeight = FontWeight.SemiBold) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = OnForestGreenContainer,
                                    selectedTextColor = ForestGreen,
                                    indicatorColor = ForestGreenContainer
                                ),
                                modifier = Modifier.testTag("rail_tab_${tab.name.lowercase()}")
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 900.dp)
                            .fillMaxSize()
                    ) {
                        if (databaseStatus is DatabaseStatus.Error) {
                            val dbError = (databaseStatus as DatabaseStatus.Error).message
                            Surface(
                                color = OutOfStockRedBg,
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .border(1.dp, OutOfStockRed.copy(alpha = 0.4f), MaterialTheme.shapes.medium)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.ErrorOutline,
                                        contentDescription = "Database error",
                                        tint = OutOfStockRed
                                    )
                                    Text(
                                        text = "Database Connection Error: $dbError",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = OutOfStockRed,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        FeedbackBanner(
                            message = feedback,
                            onDismiss = { viewModel.clearFeedback() }
                        )

                        Box(modifier = Modifier.weight(1f)) {
                            when (selectedTab) {
                                AppTab.HOME -> HomeScreen(
                                    dashboardState = dashboardState,
                                    hasProducts = allProducts.isNotEmpty(),
                                    hasDemoData = hasDemoData,
                                    isDemoBusy = isDemoBusy,
                                    onToggleDemoPreview = viewModel::setIncludeDemoPreview,
                                    onRecordSaleClick = { viewModel.openBuySell(TradeMode.SALE) },
                                    onAddPurchaseClick = { viewModel.openBuySell(TradeMode.PURCHASE) },
                                    onOpenAlertsClick = { viewModel.selectTab(AppTab.ALERTS) },
                                    onLoadSampleDataClick = { viewModel.loadSampleShopData() }
                                )

                                AppTab.PRODUCTS -> ProductsScreen(
                                    productsState = productsState,
                                    filteredProducts = filteredProducts,
                                    searchQuery = searchQuery,
                                    activeFilter = productFilter,
                                    selectedCategory = selectedCategory,
                                    onSearchQueryChange = viewModel::updateSearchQuery,
                                    onFilterChange = viewModel::updateProductFilter,
                                    onCategoryChange = viewModel::updateCategoryFilter,
                                    onAddProductClick = {
                                        editingProduct = null
                                        showProductSheet = true
                                    },
                                    onProductClick = { product ->
                                        detailProductId = product.id
                                    },
                                    onEditProductClick = { product ->
                                        editingProduct = product
                                        showProductSheet = true
                                    }
                                )

                                AppTab.BUY_SELL -> BuySellScreen(
                                    products = allProducts,
                                    tradeMode = tradeMode,
                                    selectedProductId = selectedTradeProductId,
                                    isSubmitting = isSubmitting,
                                    onTradeModeChange = viewModel::setTradeMode,
                                    onSelectProduct = viewModel::selectTradeProduct,
                                    onConfirmMultiTrade = viewModel::confirmMultiTrade
                                )

                                AppTab.ALERTS -> AlertsScreen(
                                    outOfStockProducts = alertProducts.first,
                                    lowStockProducts = alertProducts.second,
                                    onRestockClick = { product ->
                                        viewModel.openBuySell(TradeMode.PURCHASE, product.id)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (activeDetailProduct != null && !showProductSheet) {
        ProductDetailSheet(
            product = activeDetailProduct,
            sales = allSales,
            purchases = allPurchases,
            adjustments = allAdjustments,
            isSubmitting = isSubmitting,
            onDismiss = { detailProductId = null },
            onEditClick = {
                editingProduct = activeDetailProduct
                showProductSheet = true
            },
            onRecordSaleClick = {
                val id = activeDetailProduct.id
                detailProductId = null
                viewModel.openBuySell(TradeMode.SALE, id)
            },
            onBuyStockClick = {
                val id = activeDetailProduct.id
                detailProductId = null
                viewModel.openBuySell(TradeMode.PURCHASE, id)
            },
            onArchiveToggle = { archive ->
                viewModel.setProductArchived(activeDetailProduct, archive) {
                    if (archive) detailProductId = null
                }
            },
            onDeleteDemoProduct = if (activeDetailProduct.isDemo) {
                { viewModel.deleteProduct(activeDetailProduct) }
            } else null,
            onAdjustStock = { delta, reason, notes, onSuccess, onError ->
                viewModel.adjustStock(
                    product = activeDetailProduct,
                    adjustmentDelta = delta,
                    reason = reason,
                    notes = notes,
                    onSuccess = onSuccess,
                    onError = onError
                )
            }
        )
    }

    if (showProductSheet) {
        ProductFormSheet(
            editingProduct = editingProduct,
            suggestedCode = viewModel.suggestNextItemCode(),
            isSubmitting = isSubmitting,
            onDismiss = { showProductSheet = false },
            onSave = { input, onError ->
                viewModel.saveProduct(
                    input = input,
                    onSuccess = { showProductSheet = false },
                    onError = onError
                )
            },
            onDeleteDemo = if (editingProduct?.isDemo == true) { prod ->
                viewModel.deleteProduct(prod)
            } else null
        )
    }

    if (showAccountSheet) {
        AccountSheetDialog(
            currentUserId = currentUserId,
            userEmail = userEmail,
            userPhone = userPhone,
            onDismiss = { showAccountSheet = false },
            onSignOut = { signOutUser(credentialManager, scope) }
        )
    }

    if (showResetDemoConfirm) {
        AlertDialog(
            onDismissRequest = { showResetDemoConfirm = false },
            title = { Text("Reset Demo Data?") },
            text = {
                Text("This removes only the sample demo products, sales, purchases, and adjustments marked as demo data. Your real shop records will stay safe.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetDemoConfirm = false
                        viewModel.resetDemoData()
                    }
                ) {
                    Text("Reset Demo Data", color = OutOfStockRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDemoConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun TabIconWithBadge(
    tab: AppTab,
    selected: Boolean,
    alertCount: Int
) {
    val icon = when (tab) {
        AppTab.HOME -> if (selected) Icons.Filled.Home else Icons.Outlined.Home
        AppTab.PRODUCTS -> if (selected) Icons.Filled.Inventory2 else Icons.Outlined.Inventory2
        AppTab.BUY_SELL -> if (selected) Icons.Filled.SwapHoriz else Icons.Outlined.SwapHoriz
        AppTab.ALERTS -> if (selected) Icons.Filled.NotificationsActive else Icons.Outlined.NotificationsActive
    }

    if (tab == AppTab.ALERTS && alertCount > 0) {
        BadgedBox(
            badge = {
                Badge(containerColor = OutOfStockRed, contentColor = Color.White) {
                    Text(if (alertCount > 99) "99+" else alertCount.toString())
                }
            }
        ) {
            Icon(imageVector = icon, contentDescription = tab.label)
        }
    } else {
        Icon(imageVector = icon, contentDescription = tab.label)
    }
}
