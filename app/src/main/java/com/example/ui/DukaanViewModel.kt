package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.InsufficientStockException
import com.example.data.ProductInput
import com.example.data.PurchaseLineInput
import com.example.data.SaleLineInput
import com.example.data.ShopRepository
import com.example.domain.model.ActivityEntry
import com.example.domain.model.AdjustmentReason
import com.example.domain.model.DailySalesBar
import com.example.domain.model.DashboardSummary
import com.example.domain.model.HardwareCategory
import com.example.domain.model.PaymentMode
import com.example.domain.model.Product
import com.example.domain.model.ProductFilter
import com.example.domain.model.Purchase
import com.example.domain.model.Sale
import com.example.domain.model.StockAdjustment
import com.example.domain.model.StockStatus
import com.example.domain.model.TopSellingProduct
import com.example.util.FinancialCalculator
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

enum class AppTab(val label: String) {
    HOME("Home"),
    PRODUCTS("Products"),
    BUY_SELL("Buy / Sell"),
    ALERTS("Alerts")
}

enum class TradeMode(val label: String) {
    SALE("Record Sale (Out)"),
    PURCHASE("Record Purchase (In)")
}

data class TradeCartLine(
    val product: Product,
    val quantity: Double,
    val unitPrice: Double
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

sealed interface DatabaseStatus {
    data object Syncing : DatabaseStatus
    data object Connected : DatabaseStatus
    data class Error(val message: String) : DatabaseStatus
}

data class FeedbackMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isError: Boolean
)

data class DashboardViewState(
    val summary: DashboardSummary = DashboardSummary(),
    val last7DaysSales: List<DailySalesBar> = emptyList(),
    val topSellingProducts: List<TopSellingProduct> = emptyList(),
    val recentActivity: List<ActivityEntry> = emptyList(),
    val isDemoPreview: Boolean = false
)

class DukaanViewModel(
    private val repository: ShopRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(AppTab.HOME)
    val selectedTab: StateFlow<AppTab> = _selectedTab.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _productFilter = MutableStateFlow(ProductFilter.ALL)
    val productFilter: StateFlow<ProductFilter> = _productFilter.asStateFlow()

    private val _selectedCategory = MutableStateFlow<HardwareCategory?>(null)
    val selectedCategory: StateFlow<HardwareCategory?> = _selectedCategory.asStateFlow()

    private val _tradeMode = MutableStateFlow(TradeMode.SALE)
    val tradeMode: StateFlow<TradeMode> = _tradeMode.asStateFlow()

    private val _selectedTradeProductId = MutableStateFlow<String?>(null)
    val selectedTradeProductId: StateFlow<String?> = _selectedTradeProductId.asStateFlow()

    private val _includeDemoPreview = MutableStateFlow(false)
    val includeDemoPreview: StateFlow<Boolean> = _includeDemoPreview.asStateFlow()

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    private val _isDemoBusy = MutableStateFlow(false)
    val isDemoBusy: StateFlow<Boolean> = _isDemoBusy.asStateFlow()

    private val _feedback = MutableStateFlow<FeedbackMessage?>(null)
    val feedback: StateFlow<FeedbackMessage?> = _feedback.asStateFlow()

    private fun newOwnTxId(): String = "tx_${currentUserId}_${UUID.randomUUID()}"

    private var activeBillSeed: String = UUID.randomUUID().toString()

    val productsState: StateFlow<UiState<List<Product>>> = repository.observeProducts(currentUserId)
        .map<List<Product>, UiState<List<Product>>> { UiState.Success(it) }
        .catch { error -> emit(unwrapFirestoreError(error, "products")) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val purchasesState: StateFlow<UiState<List<Purchase>>> = repository.observePurchases(currentUserId)
        .map<List<Purchase>, UiState<List<Purchase>>> { UiState.Success(it) }
        .catch { error -> emit(unwrapFirestoreError(error, "purchases")) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val salesState: StateFlow<UiState<List<Sale>>> = repository.observeSales(currentUserId)
        .map<List<Sale>, UiState<List<Sale>>> { UiState.Success(it) }
        .catch { error -> emit(unwrapFirestoreError(error, "sales")) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val stockAdjustmentsState: StateFlow<UiState<List<StockAdjustment>>> = repository.observeStockAdjustments(currentUserId)
        .map<List<StockAdjustment>, UiState<List<StockAdjustment>>> { UiState.Success(it) }
        .catch { error -> emit(unwrapFirestoreError(error, "stock_adjustments")) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val databaseStatus: StateFlow<DatabaseStatus> = combine(
        productsState,
        purchasesState,
        salesState,
        stockAdjustmentsState
    ) { prod, pur, sale, adj ->
        val firstError = listOf(prod, pur, sale, adj).filterIsInstance<UiState.Error>().firstOrNull()
        when {
            firstError != null -> DatabaseStatus.Error(firstError.message)
            prod is UiState.Loading || pur is UiState.Loading || sale is UiState.Loading || adj is UiState.Loading -> DatabaseStatus.Syncing
            else -> DatabaseStatus.Connected
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = DatabaseStatus.Syncing
    )

    val dashboardState: StateFlow<DashboardViewState> = combine(
        productsState,
        purchasesState,
        salesState,
        stockAdjustmentsState,
        includeDemoPreview
    ) { prodState, purState, saleState, adjState, showDemo ->
        val products = (prodState as? UiState.Success)?.data.orEmpty()
        val purchases = (purState as? UiState.Success)?.data.orEmpty()
        val sales = (saleState as? UiState.Success)?.data.orEmpty()
        val adjustments = (adjState as? UiState.Success)?.data.orEmpty()
        val now = System.currentTimeMillis()
        DashboardViewState(
            summary = FinancialCalculator.computeDashboardSummary(
                products = products,
                sales = sales,
                purchases = purchases,
                nowMillis = now,
                includeDemo = showDemo
            ),
            last7DaysSales = FinancialCalculator.computeLast7DaysSales(
                sales = sales,
                nowMillis = now,
                includeDemo = showDemo
            ),
            topSellingProducts = FinancialCalculator.computeTopSellingProducts(
                sales = sales,
                limit = 5,
                includeDemo = showDemo
            ),
            recentActivity = FinancialCalculator.buildRecentActivity(
                sales = sales,
                purchases = purchases,
                adjustments = adjustments,
                limit = 5,
                nowMillis = now,
                includeDemo = showDemo
            ),
            isDemoPreview = showDemo
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = DashboardViewState()
    )

    val filteredProducts: StateFlow<List<Product>> = combine(
        productsState,
        searchQuery,
        productFilter,
        selectedCategory
    ) { state, query, filter, category ->
        val items = (state as? UiState.Success)?.data.orEmpty()
        val trimmed = query.trim().lowercase()
        items.filter { product ->
            val matchesArchive = if (filter == ProductFilter.ARCHIVED) product.archived else !product.archived
            val matchesQuery = trimmed.isEmpty() ||
                product.name.lowercase().contains(trimmed) ||
                product.itemCode.lowercase().contains(trimmed) ||
                product.brand.lowercase().contains(trimmed) ||
                product.sizeSpec.lowercase().contains(trimmed)
            val matchesCategory = category == null || product.categoryEnum() == category
            val matchesStatus = when (filter) {
                ProductFilter.ALL, ProductFilter.ARCHIVED -> true
                ProductFilter.AVAILABLE -> product.stockStatus() == StockStatus.AVAILABLE
                ProductFilter.LOW_STOCK -> product.stockStatus() == StockStatus.LOW_STOCK
                ProductFilter.OUT_OF_STOCK -> product.stockStatus() == StockStatus.OUT_OF_STOCK
            }
            matchesArchive && matchesQuery && matchesCategory && matchesStatus
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = emptyList()
    )

    val alertProducts: StateFlow<Pair<List<Product>, List<Product>>> = combine(
        productsState,
        includeDemoPreview
    ) { state, showDemo ->
        val items = (state as? UiState.Success)?.data.orEmpty()
            .filter { !it.archived && (showDemo || !it.isDemo) }
        val outOfStock = items.filter { it.stockStatus() == StockStatus.OUT_OF_STOCK }
        val lowStock = items.filter { it.stockStatus() == StockStatus.LOW_STOCK }
        outOfStock to lowStock
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = emptyList<Product>() to emptyList()
    )

    val hasDemoData: StateFlow<Boolean> = combine(productsState, purchasesState, salesState) { p, pu, s ->
        val pDemo = (p as? UiState.Success)?.data?.any { it.isDemo } == true
        val puDemo = (pu as? UiState.Success)?.data?.any { it.isDemo } == true
        val sDemo = (s as? UiState.Success)?.data?.any { it.isDemo } == true
        pDemo || puDemo || sDemo
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = false
    )

    private fun unwrapFirestoreError(error: Throwable, label: String): UiState.Error {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error observing $label", rootError)
        return UiState.Error(
            rootError.localizedMessage ?: "Database connection error while loading $label."
        )
    }

    fun selectTab(tab: AppTab) {
        _selectedTab.value = tab
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateProductFilter(filter: ProductFilter) {
        _productFilter.value = filter
    }

    fun updateCategoryFilter(category: HardwareCategory?) {
        _selectedCategory.value = category
    }

    fun setIncludeDemoPreview(include: Boolean) {
        _includeDemoPreview.value = include
    }

    fun openBuySell(mode: TradeMode, productId: String? = null) {
        _tradeMode.value = mode
        if (productId != null) {
            _selectedTradeProductId.value = productId
        }
        activeBillSeed = UUID.randomUUID().toString()
        _selectedTab.value = AppTab.BUY_SELL
    }

    fun setTradeMode(mode: TradeMode) {
        _tradeMode.value = mode
        activeBillSeed = UUID.randomUUID().toString()
    }

    fun selectTradeProduct(productId: String?) {
        _selectedTradeProductId.value = productId
        activeBillSeed = UUID.randomUUID().toString()
    }

    fun clearFeedback() {
        _feedback.value = null
    }

    fun suggestNextItemCode(): String {
        val existing = (productsState.value as? UiState.Success)?.data.orEmpty()
        var candidate = 1001 + existing.size
        val used = existing.map { it.itemCode.uppercase() }.toSet()
        while ("HW-$candidate" in used) {
            candidate++
        }
        return "HW-$candidate"
    }

    fun saveProduct(
        input: ProductInput,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (_isSubmitting.value) return
        val currentProducts = (productsState.value as? UiState.Success)?.data.orEmpty()
        viewModelScope.launch {
            _isSubmitting.value = true
            val result = repository.saveProduct(currentUserId, input, currentProducts)
            _isSubmitting.value = false
            result.fold(
                onSuccess = {
                    val verb = if (input.id.isNullOrBlank()) "added" else "updated"
                    _feedback.value = FeedbackMessage(
                        text = "${input.name.trim()} $verb successfully.",
                        isError = false
                    )
                    onSuccess()
                },
                onFailure = { err ->
                    onError(err.localizedMessage ?: "Failed to save product.")
                }
            )
        }
    }

    fun setProductArchived(
        product: Product,
        archived: Boolean,
        onSuccess: () -> Unit = {}
    ) {
        if (_isSubmitting.value) return
        viewModelScope.launch {
            _isSubmitting.value = true
            val result = repository.setProductArchived(currentUserId, product.id, archived)
            _isSubmitting.value = false
            result.fold(
                onSuccess = {
                    _feedback.value = FeedbackMessage(
                        text = if (archived) {
                            "${product.name} archived. You can restore it anytime under Archived."
                        } else {
                            "${product.name} restored to active catalogue."
                        },
                        isError = false
                    )
                    onSuccess()
                },
                onFailure = { err ->
                    _feedback.value = FeedbackMessage(
                        text = err.localizedMessage ?: "Failed to update archive state.",
                        isError = true
                    )
                }
            )
        }
    }

    fun deleteProduct(product: Product) {
        if (_isSubmitting.value) return
        viewModelScope.launch {
            _isSubmitting.value = true
            val result = repository.deleteProduct(currentUserId, product.id)
            _isSubmitting.value = false
            result.fold(
                onSuccess = {
                    _feedback.value = FeedbackMessage(
                        text = "Removed demo product ${product.name}.",
                        isError = false
                    )
                },
                onFailure = { err ->
                    _feedback.value = FeedbackMessage(
                        text = err.localizedMessage ?: "Failed to delete product.",
                        isError = true
                    )
                }
            )
        }
    }

    fun adjustStock(
        product: Product,
        adjustmentDelta: Double,
        reason: AdjustmentReason,
        notes: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (_isSubmitting.value) return
        val txId = newOwnTxId()
        viewModelScope.launch {
            _isSubmitting.value = true
            val result = repository.adjustStock(
                userId = currentUserId,
                productId = product.id,
                adjustmentDelta = adjustmentDelta,
                reason = reason,
                notes = notes,
                transactionId = txId
            )
            _isSubmitting.value = false
            result.fold(
                onSuccess = {
                    _feedback.value = FeedbackMessage(
                        text = "Stock adjusted for ${product.name} (${reason.label}).",
                        isError = false
                    )
                    onSuccess()
                },
                onFailure = { err ->
                    val msg = when (err) {
                        is InsufficientStockException -> err.message ?: "Insufficient stock for adjustment."
                        else -> err.localizedMessage ?: "Failed to adjust stock."
                    }
                    onError(msg)
                }
            )
        }
    }

    fun confirmMultiTrade(
        mode: TradeMode,
        lines: List<TradeCartLine>,
        partyName: String,
        paymentMode: PaymentMode,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (_isSubmitting.value) return
        val billSeed = activeBillSeed
        val billGroupId = if (lines.size > 1) "bill_${currentUserId.take(12)}_${billSeed.take(12)}" else ""
        val txIds = lines.indices.map { idx ->
            "tx_${currentUserId}_${billSeed.take(24)}_$idx"
        }

        viewModelScope.launch {
            _isSubmitting.value = true
            val result = when (mode) {
                TradeMode.SALE -> repository.recordSaleBill(
                    userId = currentUserId,
                    items = lines.map { SaleLineInput(it.product.id, it.quantity, it.unitPrice) },
                    partyName = partyName,
                    paymentMode = paymentMode,
                    billGroupId = billGroupId,
                    transactionIds = txIds
                )
                TradeMode.PURCHASE -> repository.recordPurchaseBill(
                    userId = currentUserId,
                    items = lines.map { PurchaseLineInput(it.product.id, it.quantity, it.unitPrice) },
                    partyName = partyName,
                    paymentMode = paymentMode,
                    billGroupId = billGroupId,
                    transactionIds = txIds
                )
            }
            _isSubmitting.value = false
            result.fold(
                onSuccess = {
                    activeBillSeed = UUID.randomUUID().toString()
                    val label = if (mode == TradeMode.SALE) "Sale recorded" else "Purchase stock added"
                    val summaryText = if (lines.size == 1) {
                        "$label for ${lines.first().product.name}."
                    } else {
                        "$label for ${lines.size} items."
                    }
                    _feedback.value = FeedbackMessage(text = summaryText, isError = false)
                    onSuccess()
                },
                onFailure = { err ->
                    val msg = when (err) {
                        is InsufficientStockException -> err.message ?: "Insufficient stock."
                        else -> err.localizedMessage ?: "Transaction failed. Please check connection and retry."
                    }
                    onError(msg)
                }
            )
        }
    }

    fun loadSampleShopData() {
        if (_isDemoBusy.value) return
        viewModelScope.launch {
            _isDemoBusy.value = true
            val result = repository.loadSampleShopData(currentUserId)
            _isDemoBusy.value = false
            result.fold(
                onSuccess = { loaded ->
                    _includeDemoPreview.value = true
                    _feedback.value = if (loaded) {
                        FeedbackMessage(
                            text = "Loaded 8 Indian hardware demo items. Demo Preview enabled (your real totals remain separate).",
                            isError = false
                        )
                    } else {
                        FeedbackMessage(
                            text = "Demo data is already loaded. Switched to Demo Preview.",
                            isError = false
                        )
                    }
                },
                onFailure = { err ->
                    _feedback.value = FeedbackMessage(
                        text = err.localizedMessage ?: "Failed to load sample shop data.",
                        isError = true
                    )
                }
            )
        }
    }

    fun resetDemoData() {
        if (_isDemoBusy.value) return
        viewModelScope.launch {
            _isDemoBusy.value = true
            val result = repository.resetDemoData(currentUserId)
            _isDemoBusy.value = false
            result.fold(
                onSuccess = { deletedCount ->
                    _includeDemoPreview.value = false
                    _feedback.value = FeedbackMessage(
                        text = if (deletedCount > 0) {
                            "Removed $deletedCount demo records. Your real shop data is untouched."
                        } else {
                            "No demo records found to remove."
                        },
                        isError = false
                    )
                },
                onFailure = { err ->
                    _feedback.value = FeedbackMessage(
                        text = err.localizedMessage ?: "Failed to reset demo data.",
                        isError = true
                    )
                }
            )
        }
    }

    private companion object {
        const val TAG = "DukaanVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
