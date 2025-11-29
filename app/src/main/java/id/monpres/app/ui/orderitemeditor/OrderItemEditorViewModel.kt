package id.monpres.app.ui.orderitemeditor

import android.app.Application
import android.location.Location
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.monpres.app.enums.UserRole
import id.monpres.app.model.MontirPresisiUser
import id.monpres.app.model.OrderItem
import id.monpres.app.model.OrderService
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.ceil

@HiltViewModel
class OrderItemEditorViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val application: Application,
) : ViewModel() {
    companion object {
        private val TAG = OrderItemEditorViewModel::class.simpleName
        private const val ITEMS_KEY = "items"
    }

    val items: StateFlow<List<OrderItem>?> = savedStateHandle.getStateFlow(ITEMS_KEY, null)

    fun setItems(items: List<OrderItem>) {
        savedStateHandle[ITEMS_KEY] = items
    }

    fun addItem(item: OrderItem) {
        val currentItems = items.value?.toMutableList() ?: mutableListOf()
        currentItems.add(item)
        setItems(currentItems)
    }

    fun removeItem(item: OrderItem) {
        val currentItems = items.value?.toMutableList() ?: mutableListOf()
        currentItems.remove(item)
        setItems(currentItems)
    }

    fun addAdditionalItems(user: MontirPresisiUser, orderService: OrderService, initialItems: List<OrderItem>?) {
        viewModelScope.launch {
            val currentItems = items.first()?.toMutableList() ?: initialItems?.toMutableList() ?: mutableListOf()
            val isAdmin = user.role == UserRole.ADMIN

            // Check if items missing and add them with correct fixed state
            if (currentItems.none { it.id == OrderItem.PLATFORM_FEE_ID }) {
                currentItems.add(
                    if (currentItems.isEmpty()) 0 else currentItems.size, // Add to end or specific logic
                    OrderItem(
                        id = OrderItem.PLATFORM_FEE_ID,
                        name = application.getString(OrderItem.PLATFORM_FEE_NAME),
                        price = OrderItem.PLATFORM_FEE,
                        quantity = 1.0,
                        isFixed = !isAdmin // False if Admin, True otherwise
                    )
                )
            }

            if (currentItems.none { it.id == OrderItem.DISTANCE_FEE_ID }) {
                val distance = calculateDistane(user, orderService)
                currentItems.add(
                    index = if (currentItems.isEmpty()) 0 else currentItems.size,
                    element = OrderItem(
                        id = OrderItem.DISTANCE_FEE_ID,
                        name = application.getString(OrderItem.DISTANCE_FEE_NAME),
                        price = OrderItem.DISTANCE_FEE,
                        quantity = distance,
                        isFixed = !isAdmin // False if Admin, True otherwise
                    )
                )
            Log.d(TAG, "currentItems after: $currentItems")
            }
            setItems(currentItems.toList())

            // Ensure fees are at the top if preferred, or just let them be added.
            // (Your original code added them at index 0 or size-1. Keeping logic simple here).
        }
    }

    private fun calculateDistane(user: MontirPresisiUser, orderService: OrderService): Double {
        val partnerLocation = if (user.role == UserRole.PARTNER) {
            Location("partner").apply {
                latitude = user.locationLat?.toDouble() ?: 0.0
                longitude = user.locationLng?.toDouble() ?: 0.0
            }
        } else {
            // Admin uses partner's location from order object
            Location("order").apply {
                latitude = orderService.partner?.locationLat?.toDouble() ?: 0.0
                longitude = orderService.partner?.locationLng?.toDouble() ?: 0.0
            }
        }

        val targetLocation = Location("target").apply {
            latitude = orderService.selectedLocationLat ?: 0.0
            longitude = orderService.selectedLocationLng ?: 0.0
        }

        val distanceInMeters = partnerLocation.distanceTo(targetLocation).toDouble()
        return ceil(distanceInMeters / 1000)
    }
}