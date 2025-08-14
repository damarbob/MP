package id.monpres.app

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import id.monpres.app.model.OrderService
import id.monpres.app.model.Vehicle
import id.monpres.app.repository.OrderServiceRepository
import id.monpres.app.repository.VehicleRepository
import id.monpres.app.utils.UiState
import id.monpres.app.utils.takeUntilSignal
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

@HiltViewModel
class MainViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val orderServiceRepository: OrderServiceRepository,
    private val vehicleRepository: VehicleRepository
) : ViewModel() {

    private val _signOutEvent = MutableSharedFlow<Unit>()
    val signOutEvent: SharedFlow<Unit> = _signOutEvent.asSharedFlow()

    private val _externalSignOutSignal =
        MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)
    val externalSignOutSignal: SharedFlow<Unit> = _externalSignOutSignal.asSharedFlow()

    private val _stopObserveSignal =
        MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)
    val stopObserveSignal: SharedFlow<Unit> = _stopObserveSignal.asSharedFlow()

    private val _userOrderServicesState = MutableStateFlow<UiState<List<OrderService>>>(UiState.Loading)
    val userOrderServicesState: StateFlow<UiState<List<OrderService>>> = _userOrderServicesState.asStateFlow()

    private val _userOrderServiceState = MutableStateFlow<UiState<OrderService>>(UiState.Loading)
    val userOrderServiceState: StateFlow<UiState<OrderService>> = _userOrderServiceState.asStateFlow()

    private val _userVehiclesState = MutableStateFlow<UiState<List<Vehicle>>>(UiState.Loading)
    val userVehiclesState: StateFlow<UiState<List<Vehicle>>> = _userVehiclesState.asStateFlow()

    init {
        observeUserOrderServices()
        observeUserVehicles()
        Log.d("MainViewModel", "current firebaseAuth: $auth")
    }

    private fun observeUserOrderServices() {
        viewModelScope.launch {
            orderServiceRepository.observeOrderServicesByUserId()
                .takeUntilSignal(externalSignOutSignal) // Stop observation on logout
                .catch { e ->
                    if (e is CancellationException) {
                        Log.i("MainViewModel", "User orders observation cancelled.", e)
                        // Optionally reset state or re-throw
                        // _userOrdersState.value = UiState.Loading // Or an error specific to cancellation
                        throw e
                    }
                    Log.e("MainViewModel", "Error in user orders flow collection", e)
                    _userOrderServicesState.value = UiState.Error(e)
                }
                .collect { state ->
                    _userOrderServicesState.value = state
                }
        }
    }

    // If ServiceProcessFragment needs a specific order by ID
    fun observeOrderServiceById(orderId: String) {
        viewModelScope.launch {
            // Assuming _userOrderServicesState is already being populated by another Flow
            // from orderServiceRepository.observeAllOrderServices() or similar.
            // We'll transform that existing Flow.
            _userOrderServicesState
                .takeUntilSignal(stopObserveSignal) // Keep this if it's relevant for stopping observation
                .map { userOrderServicesUiState -> // Transform the UiState<List<OrderService>>
                    when (userOrderServicesUiState) {
                        is UiState.Loading -> UiState.Loading
                        is UiState.Error -> UiState.Error(userOrderServicesUiState.exception)
                        is UiState.Success -> {
                            val orderService = userOrderServicesUiState.data.find { it.id == orderId }
                            if (orderService != null) {
                                UiState.Success(orderService)
                            } else {
                                // Be more specific about the error if possible.
                                // Is it truly a "not found" scenario or an unexpected null?
                                UiState.Error(NoSuchElementException("OrderService with ID $orderId not found."))
                            }
                        }
                    }
                }
                .catch { e ->
                    if (e is CancellationException) {
                        Log.i("MainViewModel", "OrderService by ID observation cancelled.", e)
                        // Optionally reset state or re-throw
                        // _userOrderServiceState.value = UiState.Loading // Or an error specific to cancellation
                        throw e // Re-throw to propagate cancellation
                    }
                    Log.e("MainViewModel", "Error in OrderService by ID flow collection", e)
                    _userOrderServiceState.value = UiState.Error(e)
                }
                .collect { specificOrderServiceUiState ->
                    _userOrderServiceState.value = specificOrderServiceUiState
                }
        }
    }

    private fun observeUserVehicles() {
        viewModelScope.launch {
            vehicleRepository.getVehiclesByUserIdFlow()
                .takeUntilSignal(externalSignOutSignal) // Stop observation on logout
                .catch { e ->
                    if (e is CancellationException) {
                        Log.i("MainViewModel", "Vehicle observation cancelled.", e)
                        // Optionally reset state or re-throw
                        // _userOrdersState.value = UiState.Loading // Or an error specific to cancellation
                        throw e
                    }
                    Log.e("MainViewModel", "Error in vehicles flow collection", e)
                    _userVehiclesState.value = UiState.Error(e)
                }
                .collect { state ->
                    _userVehiclesState.value = state
                }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            _externalSignOutSignal.emit(Unit)
            auth.signOut()  // Firebase sign-out (no context needed)
            _signOutEvent.emit(Unit)  // Signal to UI
        }
    }

    fun stopObserve() {
        viewModelScope.launch {
            _stopObserveSignal.emit(Unit)
        }
    }
}