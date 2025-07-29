package id.monpres.app.ui.home

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import id.monpres.app.repository.VehicleRepository
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val vehicleRepository: VehicleRepository
) : ViewModel() {
    fun getVehiclesFlow() = vehicleRepository.getVehiclesByUserIdFlow()
}