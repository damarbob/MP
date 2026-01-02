package id.monpres.app.ui.orderserviceedit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.mapbox.geojson.Point
import dagger.hilt.android.lifecycle.HiltViewModel
import id.monpres.app.enums.OrderStatus
import id.monpres.app.model.OrderItem
import id.monpres.app.model.OrderService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class OrderServiceEditViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _orderService = MutableStateFlow<OrderService?>(null)
    val orderService: StateFlow<OrderService?> = _orderService.asStateFlow()

    fun initialize(order: OrderService) {
        if (_orderService.value == null) {
            _orderService.value = order
        }
    }

    fun updateLocation(point: Point) {
        _orderService.value = _orderService.value?.copy(
            selectedLocationLat = point.latitude(),
            selectedLocationLng = point.longitude()
        )
    }

    fun updateDetails(address: String, category: String, description: String) {
        _orderService.value = _orderService.value?.copy(
            userAddress = address,
            issue = category,
            issueDescription = description
        )
    }

    // NEW: Update Status
    fun updateStatus(status: OrderStatus) {
        _orderService.value = _orderService.value?.copy(
            status = status
        )
    }

    fun updateOrderItems(items: List<OrderItem>) {
        val newPrice = OrderService.getPriceFromOrderItems(items)
        _orderService.value = _orderService.value?.copy(
            orderItems = items,
            price = newPrice
        )
    }
}
