package id.monpres.app.ui.insertvehicle

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import id.monpres.app.model.Vehicle
import id.monpres.app.model.VehicleType
import id.monpres.app.repository.VehicleRepository
import javax.inject.Inject

@HiltViewModel
class InsertVehicleViewModel @Inject constructor(
    private val vehicleRepository: VehicleRepository
) : ViewModel() {
    private val _vehicleTypes = MutableLiveData<List<VehicleType>>(emptyList())
    val vehicleTypes: LiveData<List<VehicleType>> = _vehicleTypes

    init {
        _vehicleTypes.value = VehicleType.getSampleList()
    }

    fun insertVehicle(vehicle: Vehicle) = vehicleRepository.insertVehicle(vehicle)

}