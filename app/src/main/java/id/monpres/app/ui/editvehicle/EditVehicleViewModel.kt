package id.monpres.app.ui.editvehicle

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import id.monpres.app.model.Vehicle
import id.monpres.app.model.VehicleType
import id.monpres.app.repository.VehicleRepository
import javax.inject.Inject

@HiltViewModel
class EditVehicleViewModel @Inject constructor(
    private val vehicleRepository: VehicleRepository,
    private val auth: FirebaseAuth
) : ViewModel() {
    private val _vehicleTypes = MutableLiveData<List<VehicleType>>(emptyList())
    val vehicleTypes: LiveData<List<VehicleType>> = _vehicleTypes

    init {
        _vehicleTypes.value = VehicleType.getSampleList()
    }

    fun updateVehicle(vehicle: Vehicle) = vehicleRepository.updateVehicle(vehicle.copy(userId = auth.currentUser?.uid))
}