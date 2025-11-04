package id.monpres.app.ui.editvehicle

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import id.monpres.app.model.Vehicle
import id.monpres.app.model.VehicleType
import id.monpres.app.repository.VehicleRepository
import id.monpres.app.state.UiState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
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

    /**
     * Updates a vehicle.
     * This now correctly returns a Flow that `observeUiStateOneShot` can consume.
     * It emits Loading, then attempts the update, and emits Success or Error.
     */
    fun updateVehicle(vehicle: Vehicle): Flow<UiState<Vehicle>> = flow {
        // 1. Emit Loading state first.
        emit(UiState.Loading)

        // 2. Perform the suspend function call to the repository.
        // Make sure your repository's updateVehicle function is a 'suspend' function.
        val updatedVehicle = vehicleRepository.updateVehicle(vehicle.copy(userId = auth.currentUser?.uid))

        // 3. On success, emit the Success state.
        emit(UiState.Success(updatedVehicle))
    }.catch { e ->
        // 4. If the repository call fails, the catch block will execute.
        // We emit the error to be handled by a separate error observer in the Fragment.
        // For observeUiStateOneShot, we can just let it end or emit Empty.
        // In this case, not emitting anything in catch is fine, as the flow will just end on an exception.
        // Or you could emit an empty state if your helper handles it.
        // Let's assume the error is handled by a global error SharedFlow and this flow just terminates.
        emit(UiState.Empty)
        throw e // Re-throwing ensures the flow completes with an error, which can be caught elsewhere.
    }

}