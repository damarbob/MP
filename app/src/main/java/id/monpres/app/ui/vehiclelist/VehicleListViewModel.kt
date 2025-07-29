package id.monpres.app.ui.vehiclelist

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import id.monpres.app.repository.VehicleRepository
import javax.inject.Inject

@HiltViewModel
class VehicleListViewModel @Inject constructor(
    private val vehicleRepository: VehicleRepository
) : ViewModel() {

    fun getVehiclesFlow() = vehicleRepository.getVehiclesByUserIdFlow()
    fun deleteVehicles(vehicleIds: List<String>) = vehicleRepository.deleteVehicles(vehicleIds)
}