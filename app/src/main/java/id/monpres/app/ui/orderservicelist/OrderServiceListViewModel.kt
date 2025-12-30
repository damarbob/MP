package id.monpres.app.ui.orderservicelist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.DocumentSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import id.monpres.app.R
import id.monpres.app.enums.OrderStatus
import id.monpres.app.enums.OrderStatusType
import id.monpres.app.enums.UserRole
import id.monpres.app.model.OrderService
import id.monpres.app.repository.UserRepository
import id.monpres.app.usecase.GetPagedOrderServicesUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OrderServiceListViewModel @Inject constructor(
    private val getPagedOrderServicesUseCase: GetPagedOrderServicesUseCase,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _orderListState = MutableStateFlow<List<OrderService>>(emptyList())
    val orderListState: StateFlow<List<OrderService>> = _orderListState.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // For the small bottom loader
    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _isEmptyState = MutableStateFlow(false)
    val isEmptyState: StateFlow<Boolean> = _isEmptyState.asStateFlow()

    // Pagination State
    private var lastVisibleDocument: DocumentSnapshot? = null
    private var isLastPage = false
    private val pageSize: Long = 10

    // Filter State
    private var currentSearchQuery = ""
    private var currentChipId = R.id.fragmentOrderServiceListChipOrderStatusOngoing

    private var searchJob: Job? = null

    init {
        // Initial Load
        refreshData()
    }

    fun onScrollBottomReached() {
        if (isLastPage || _isLoading.value || _isLoadingMore.value) return
        loadNextPage()
    }

    fun setSearchQuery(query: String) {
        if (currentSearchQuery == query) return
        currentSearchQuery = query

        // Debounce Search (Server-side optimization)
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(500) // Wait 500ms after user stops typing
            refreshData()
        }
    }

    fun setSelectedChipId(chipId: Int) {
        if (currentChipId == chipId) return
        currentChipId = chipId
        // Cancel any pending search immediately if filter changes
        searchJob?.cancel()
        refreshData()
    }

    private fun refreshData() {
        viewModelScope.launch {
            _isLoading.value = true
            resetPagination()

            try {
                val result = fetchFromUseCase()
                _orderListState.value = result.first
                lastVisibleDocument = result.second

                if (result.first.size < pageSize) {
                    isLastPage = true
                }

                _isEmptyState.value = result.first.isEmpty()
            } catch (e: Exception) {
                // Handle error
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun loadNextPage() {
        viewModelScope.launch {
            _isLoadingMore.value = true

            try {
                val result = fetchFromUseCase()
                val currentList = _orderListState.value.toMutableList()
                currentList.addAll(result.first)

                _orderListState.value = currentList
                lastVisibleDocument = result.second

                if (result.first.size < pageSize) {
                    isLastPage = true
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    private fun resetPagination() {
        lastVisibleDocument = null
        isLastPage = false
        _orderListState.value = emptyList()
    }

    private suspend fun fetchFromUseCase(): Pair<List<OrderService>, DocumentSnapshot?> {
        val currentUser = userRepository.getCurrentUserRecord() ?: return Pair(emptyList(), null)

        // Map Chips to Filter Logic
        var statusTypes: List<OrderStatusType>? = null
        var exactStatus: OrderStatus? = null

        when (currentChipId) {
            R.id.fragmentOrderServiceListChipOrderStatusOngoing -> {
                statusTypes = listOf(OrderStatusType.OPEN, OrderStatusType.IN_PROGRESS)
            }
            R.id.fragmentOrderServiceListChipOrderStatusCompleted -> {
                exactStatus = OrderStatus.COMPLETED
            }
            R.id.fragmentOrderServiceListChipOrderStatusCancelled -> {
                exactStatus = OrderStatus.CANCELLED
            }
        }

        val pagedResult = getPagedOrderServicesUseCase(
            limit = pageSize,
            lastSnapshot = lastVisibleDocument,
            searchQuery = currentSearchQuery,
            statusTypeFilter = statusTypes,
            exactStatus = exactStatus,
            userRole = currentUser.role ?: UserRole.CUSTOMER
        )

        return Pair(pagedResult.data, pagedResult.lastSnapshot)
    }
}