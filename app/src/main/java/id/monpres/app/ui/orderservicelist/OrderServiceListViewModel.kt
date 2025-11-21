package id.monpres.app.ui.orderservicelist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.monpres.app.R
import id.monpres.app.enums.OrderStatus
import id.monpres.app.enums.OrderStatusType
import id.monpres.app.model.OrderService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class OrderServiceListViewModel @Inject constructor(

) : ViewModel() {
    private val _allOrderServices = MutableStateFlow<List<OrderService>>(emptyList())

    // Input: The currently selected filter chip
    private val _selectedChipId = MutableStateFlow<Int?>(null)
    val selectedChipId: StateFlow<Int?> = _selectedChipId.asStateFlow()

    // Input: Search Query
    private val _searchQuery = MutableStateFlow<String>("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // --- State to be observed by the UI ---

    // Output: The final, filtered, and sorted list for the RecyclerView
    val filteredOrderServices: StateFlow<List<OrderService>> = combine(
        _allOrderServices,
        _selectedChipId,
        _searchQuery
    ) { allOrders, chipId, query ->

        // 1. Priority: If search query exists, filter by ID and ignore chips
        if (query.isNotBlank()) {
            return@combine allOrders.filter {
                it.id?.contains(query, ignoreCase = true) == true
            }.sortedByDescending { it.updatedAt }
        }

        // 2. If no search, filter by Chips
        val ongoingOrders =
            allOrders.filter { it.status?.type == OrderStatusType.OPEN || it.status?.type == OrderStatusType.IN_PROGRESS }

        when (chipId) {
            R.id.fragmentOrderServiceListChipOrderStatusOngoing ->
                ongoingOrders.sortedByDescending { it.createdAt }

            R.id.fragmentOrderServiceListChipOrderStatusCompleted -> {
                val completedOrders = allOrders.filter { it.status == OrderStatus.COMPLETED }
                completedOrders.sortedByDescending { it.updatedAt }
            }

            R.id.fragmentOrderServiceListChipOrderStatusCancelled -> {
                val cancelledOrders = allOrders.filter { it.status == OrderStatus.CANCELLED }
                cancelledOrders.sortedByDescending { it.updatedAt }
            }

            else -> {
                // Default case: Show "All" or a default set
                allOrders.sortedByDescending { it.updatedAt }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Public functions to be called from the Fragment
    fun setAllOrderServices(orders: List<OrderService>) {
        _allOrderServices.value = orders

        // Smart Default: Set the default chip selection only if one isn't already set
        if (_selectedChipId.value == null) {
            val ongoingOrdersExist =
                orders.any { it.status?.type == OrderStatusType.OPEN || it.status?.type == OrderStatusType.IN_PROGRESS }
            _selectedChipId.value = if (ongoingOrdersExist) {
                R.id.fragmentOrderServiceListChipOrderStatusOngoing
            } else {
                R.id.fragmentOrderServiceListChipOrderStatusCompleted
            }
        }
    }

    fun setSelectedChipId(chipId: Int?) {
        _selectedChipId.value = chipId
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }
}