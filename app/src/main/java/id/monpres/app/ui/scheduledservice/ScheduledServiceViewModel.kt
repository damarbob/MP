package id.monpres.app.ui.scheduledservice

import dagger.hilt.android.lifecycle.HiltViewModel
import id.monpres.app.model.OrderService
import id.monpres.app.repository.VehicleRepository
import id.monpres.app.ui.baseservice.BaseServiceViewModel
import javax.inject.Inject

@HiltViewModel
class ScheduledServiceViewModel @Inject constructor(private val vehicleRepository: VehicleRepository) : BaseServiceViewModel() {
    override fun placeOrder(orderService: OrderService) {
        super.placeOrder(orderService)
    }

}