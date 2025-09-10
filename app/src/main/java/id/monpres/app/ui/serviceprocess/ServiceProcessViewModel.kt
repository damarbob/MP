package id.monpres.app.ui.serviceprocess

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import id.monpres.app.model.OrderService
import id.monpres.app.repository.OrderServiceRepository
import javax.inject.Inject

@HiltViewModel
class ServiceProcessViewModel @Inject constructor(
    private val orderServiceRepository: OrderServiceRepository
) : ViewModel() {
    fun updateOrderService(orderService: OrderService) = orderServiceRepository.updateOrderService(orderService)
}