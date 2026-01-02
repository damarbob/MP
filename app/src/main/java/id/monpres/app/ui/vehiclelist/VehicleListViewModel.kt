package id.monpres.app.ui.vehiclelist

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import id.monpres.app.repository.VehicleRepository
import id.monpres.app.state.UiState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

@HiltViewModel
class VehicleListViewModel @Inject constructor(
    private val vehicleRepository: VehicleRepository
) : ViewModel() {
    fun deleteVehicles(vehicleIds: List<String>): Flow<UiState<Unit>> = flow {

        emit(UiState.Loading)

        vehicleRepository.deleteVehicles(vehicleIds)

        emit(UiState.Success(Unit))
    }.catch { e ->
        emit(UiState.Empty)
        e.printStackTrace()
    }
}
