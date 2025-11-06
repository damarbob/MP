package id.monpres.app.ui.serviceprocess

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import id.monpres.app.model.OrderService
import id.monpres.app.repository.LivePartnerLocationRepository
import id.monpres.app.repository.OrderServiceRepository
import id.monpres.app.state.UiState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

@HiltViewModel
class ServiceProcessViewModel @Inject constructor(
    private val orderServiceRepository: OrderServiceRepository,
    private val livePartnerLocationRepository: LivePartnerLocationRepository
) : ViewModel() {
    companion object {
        private const val TAG = "ServiceProcessViewModel"
    }

    private val _aerialDistanceBetweenPartnerAndSelectedLocationInMeters = MutableStateFlow(40_000_000f)
    val aerialDistanceBetweenPartnerAndSelectedLocationInMeters = _aerialDistanceBetweenPartnerAndSelectedLocationInMeters.asStateFlow()

    fun updateOrderService(orderService: OrderService): Flow<UiState<OrderService>> = flow {
        // 1. Emit Loading state immediately
        emit(UiState.Loading)

        // 2. Call the repository's suspend function to perform the update
        orderServiceRepository.updateOrderService(orderService)

        // 3. If the repository call succeeds, emit Success state
        emit(UiState.Success(orderService))
    }.catch { e ->
        // 4. If the repository call throws an exception, catch it and emit an Error state
        emit(UiState.Empty)
        e.printStackTrace()
    }

    fun observePartnerLocation(orderId: String) = livePartnerLocationRepository.observeLiveLocation(orderId)

}