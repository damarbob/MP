package id.monpres.app.ui.orderservicelist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import id.monpres.app.R
import id.monpres.app.enums.OrderStatus
import id.monpres.app.enums.OrderStatusType
import id.monpres.app.enums.UserRole
import id.monpres.app.model.OrderService
import id.monpres.app.repository.OrderServiceRepository
import id.monpres.app.repository.UserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class OrderServiceListViewModel @Inject constructor(
    private val orderServiceRepository: OrderServiceRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    // The ViewModel now holds a Flow of PagingData, not a List.
    var orderPagingDataFlow: Flow<PagingData<OrderService>>

    // State for filters
    private val _filterState = MutableStateFlow(FilterState())
    private val _currentUserState = MutableStateFlow<Pair<String, UserRole>?>(null)

    init {
        viewModelScope.launch {
            val user = userRepository.getCurrentUserRecord()
            if (user != null && user.id != null) {
                val userPair = Pair(user.id, user.role ?: UserRole.CUSTOMER)
                _currentUserState.value = userPair
            }
        }

        orderPagingDataFlow = combine(_currentUserState, _filterState) { userPair, filter ->
            Triple(userPair, filter.searchQuery, filter.chipId)
        }.filterNotNull() // Ensure user is loaded
            .flatMapLatest { (userPair, searchQuery, chipId) ->
                if (userPair == null) return@flatMapLatest emptyFlow()

                val (userId, userRole) = userPair

                val statusFilters = when (chipId) {
                    R.id.fragmentOrderServiceListChipOrderStatusOngoing ->
                        OrderStatus.entries.filter { it.type == OrderStatusType.IN_PROGRESS }.toList()
                    R.id.fragmentOrderServiceListChipOrderStatusOpen ->
                        OrderStatus.entries.filter { it.type == OrderStatusType.OPEN }.toList()
                    R.id.fragmentOrderServiceListChipOrderStatusCompleted ->
                        listOf(OrderStatus.COMPLETED)
                    R.id.fragmentOrderServiceListChipOrderStatusCancelled ->
                        listOf(OrderStatus.CANCELLED)
                    else -> OrderStatus.entries.toList() // Or emptyList() for "All"
                }
                val statusFilterName = statusFilters.map { it.name }

                orderServiceRepository.getOrderServiceStream(
                    searchQuery = searchQuery,
                    statusFilter = statusFilterName, // Pass nullable list
                    userRole = userRole,
                    userId = userId
                )
            }.cachedIn(viewModelScope)
    }

    fun setSearchQuery(query: String) {
        // Debouncing can still be applied if desired before updating the state
        _filterState.value = _filterState.value.copy(searchQuery = query)
    }

    fun setSelectedChipId(chipId: Int) {
        _filterState.value = _filterState.value.copy(chipId = chipId)
    }

    // Data class to hold all filter states together
    private data class FilterState(
        val searchQuery: String = "",
        val chipId: Int = R.id.fragmentOrderServiceListChipOrderStatusOngoing
    )

    override fun onCleared() {
        super.onCleared()
        orderServiceRepository.stopRealTimeOrderUpdates()
    }
}