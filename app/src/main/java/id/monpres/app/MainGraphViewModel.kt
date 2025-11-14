package id.monpres.app

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import id.monpres.app.enums.OrderStatus
import id.monpres.app.enums.UserRole
import id.monpres.app.libraries.ErrorLocalizer
import id.monpres.app.model.MontirPresisiUser
import id.monpres.app.model.OrderService
import id.monpres.app.model.Vehicle
import id.monpres.app.repository.OrderServiceRepository
import id.monpres.app.repository.UserRepository
import id.monpres.app.repository.VehicleRepository
import id.monpres.app.service.OrderServiceLocationTrackingService
import id.monpres.app.state.ConnectionState
import id.monpres.app.state.UiState
import id.monpres.app.state.UiState.Empty
import id.monpres.app.state.UiState.Error
import id.monpres.app.state.UiState.Loading
import id.monpres.app.state.UiState.Success
import id.monpres.app.utils.NetworkConnectivityObserver
import id.monpres.app.utils.takeUntilSignal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

@HiltViewModel
class MainGraphViewModel @Inject constructor(
    private val orderServiceRepository: OrderServiceRepository,
    private val vehicleRepository: VehicleRepository,
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val application: Application,
    private val networkConnectivityObserver: NetworkConnectivityObserver,
    private val auth: FirebaseAuth
) : ViewModel() {
    companion object {
        private val TAG = MainGraphViewModel::class.simpleName
    }

    private val _userOrderServicesState =
        MutableStateFlow<UiState<List<OrderService>>>(Loading)
    val userOrderServicesState: StateFlow<UiState<List<OrderService>>> =
        _userOrderServicesState.asStateFlow()

    private val _partnerOrderServicesState =
        MutableStateFlow<UiState<List<OrderService>>>(Loading)
    val partnerOrderServicesState: StateFlow<UiState<List<OrderService>>> =
        _partnerOrderServicesState.asStateFlow()

    private val _openedOrderServiceState = MutableStateFlow<UiState<OrderService>>(Loading)
    val openedOrderServiceState: StateFlow<UiState<OrderService>> =
        _openedOrderServiceState.asStateFlow()

    private var openedOrderJob: Job? = null // To manage the collector coroutine

    private val _userVehiclesState = MutableStateFlow<UiState<List<Vehicle>>>(Loading)
    val userVehiclesState: StateFlow<UiState<List<Vehicle>>> = _userVehiclesState.asStateFlow()
    private val _userVehicles = MutableStateFlow<List<Vehicle>>(emptyList())
    val userVehicles: StateFlow<List<Vehicle>> = _userVehicles.asStateFlow()

    // Event channel for one-time error messages
    private val _errorEvent = MutableSharedFlow<Throwable>()
    val errorEvent: SharedFlow<Throwable> = _errorEvent.asSharedFlow()

    private val _currentUser: MutableStateFlow<MontirPresisiUser?> = MutableStateFlow(null)
    val currentUser: StateFlow<MontirPresisiUser?> = _currentUser.asStateFlow()

    init {
        // Start observing data as soon as this ViewModel is created
        Log.d(TAG, "ViewModel init")
        viewModelScope.launch(Dispatchers.Default) {
            networkConnectivityObserver.networkStatus.collect { status ->
                when (status.state) {
                    ConnectionState.Connected -> {
                        if (auth.currentUser != null && _currentUser.value?.id != auth.currentUser?.uid) {
                            try {
                                _currentUser.value = withTimeout(TimeUnit.SECONDS.toMillis(10)) {
                                    userRepository.userRecord.filterNotNull().first()
                                }
                                currentUser.value?.let { observeDataByRole(it) }

                                manageLocationServiceLifecycle()
                            } catch (e: Exception) {
                                changeStates()
                                _errorEvent.emit(e)
                            }
                        }
                    }

                    ConnectionState.Disconnected -> {
                        changeStates()
                    }
                }
            }
        }
    }

    private fun changeStates() {
        _userOrderServicesState.value =
            if (_userOrderServicesState.value == Loading) Empty else _userOrderServicesState.value
        _userVehiclesState.value =
            if (_userVehiclesState.value == Loading) Empty else _userVehiclesState.value
        _partnerOrderServicesState.value =
            if (_partnerOrderServicesState.value == Loading) Empty else _partnerOrderServicesState.value
        _openedOrderServiceState.value =
            if (_openedOrderServiceState.value == Loading) Empty else _openedOrderServiceState.value
    }

    fun observeDataByRole(user: MontirPresisiUser) {
        Log.d(TAG, "current role: ${user.role}")
        if (user.role == UserRole.CUSTOMER) {
            observeUserOrderServices()
            observeUserVehicles()
        } else if (user.role == UserRole.PARTNER) {
            observePartnerOrderServices()
        }
    }

    private fun observeUserOrderServices() {
        viewModelScope.launch(Dispatchers.Default) {
            orderServiceRepository.observeOrderServicesByUserId()
                .takeUntilSignal(sessionManager.externalSignOutSignal)
                .onStart { _userOrderServicesState.value = Loading }
                .catch { e ->
                    // If the exception is for cancellation, re-throw it to stop the coroutine gracefully.
                    if (e is CancellationException) {
                        Log.i(TAG, "User orders observation was cancelled.")
                        throw e
                    }
                    // Now the ViewModel can handle specific exceptions if needed
                    Log.e(TAG, "Error observing user orders", e)
                    // On failure, emit the error as a one-time event
                    _errorEvent.emit(e)
                    // Set the UI state to Empty or keep the last successful state
                    _userOrderServicesState.value = Empty
                }
                .collect { orders ->
                    // The repository gives us clean data.
                    _userOrderServicesState.value =
                        if (orders.isEmpty()) Empty else Success(orders)
                }
        }
    }

    private fun observePartnerOrderServices() {
        viewModelScope.launch(Dispatchers.Default) {
            orderServiceRepository.observeOrderServicesByPartnerId()
                .takeUntilSignal(sessionManager.externalSignOutSignal)
                .onStart { _partnerOrderServicesState.value = Loading }
                .catch { e ->
                    // If the exception is for cancellation, re-throw it to stop the coroutine gracefully.
                    if (e is CancellationException) {
                        Log.i(TAG, "Partner orders observation was cancelled.")
                        throw e
                    }
                    Log.e(TAG, "Error observing partner orders", e)
                    // On failure, emit the error as a one-time event
                    _errorEvent.emit(e)
                    // Set the UI state to Empty or keep the last successful state
                    _partnerOrderServicesState.value = Empty
                }
                .collect { orders ->
                    _partnerOrderServicesState.value =
                        if (orders.isEmpty()) Empty else Success(orders)
                }
        }
    }

    fun observeOrderServiceById(orderId: String) {
        openedOrderJob?.cancel() // Cancel any previous observation

        // Get the correct master list flow based on user role
        val role = userRepository.getCurrentUserRecord()?.role
        val masterListFlow = when (role) {
            UserRole.CUSTOMER -> userOrderServicesState
            UserRole.PARTNER -> partnerOrderServicesState
            else -> null
        }

        if (masterListFlow == null) {
            _openedOrderServiceState.value = Empty // Or an error state
            return
        }

        openedOrderJob = viewModelScope.launch(Dispatchers.Default) {
            masterListFlow.collect { listState ->
                when (listState) {
                    is Loading -> _openedOrderServiceState.value = Loading
                    is Empty -> _openedOrderServiceState.value = Empty
                    is Success -> {
                        val orderService = listState.data.find { it.id == orderId }
                        if (orderService != null) {
                            _openedOrderServiceState.value = Success(orderService)
                        } else {
                            // The order was not found in the master list.
                            _openedOrderServiceState.value = Empty
                        }
                    }

                    is Error -> {}
                }
            }
        }
    }

    fun stopObservingOpenedOrder() {
        openedOrderJob?.cancel()
        openedOrderJob = null
    }

    private fun observeUserVehicles() {
        viewModelScope.launch(Dispatchers.Default) {
            vehicleRepository.getVehiclesByUserIdFlow(this)
                .takeUntilSignal(sessionManager.externalSignOutSignal) // Stop observation on logout
                .onStart {
                    _userVehiclesState.value = Loading
                }
                .catch { e ->
                    if (e is CancellationException) {
                        Log.i(TAG, "Vehicle observation cancelled.", e)
                        // Optionally reset state or re-throw
                        // _userOrdersState.value = UiState.Loading // Or an error specific to cancellation
                        throw e
                    }
                    Log.e(TAG, "Error in vehicles flow collection", e)
                    _userVehiclesState.value = Empty
                    _errorEvent.emit(e)
                }
                .collect { vehicles ->
                    Log.d(TAG, "User vehicles: $vehicles")
                    Log.d(TAG, "User vehicles state: ${_userVehiclesState.value}")
                    _userVehiclesState.value =
                        if (vehicles.isEmpty()) Empty else Success(vehicles)
                    _userVehicles.value = vehicles
                }
        }
    }

    private fun manageLocationServiceLifecycle() {
        viewModelScope.launch(Dispatchers.Default) {
            val role = currentUser.value?.role
            val orderFlow = when (role) {
                UserRole.CUSTOMER -> userOrderServicesState
                UserRole.PARTNER -> partnerOrderServicesState
                else -> null
            }

            // Keep track of which orders we are currently tracking
            val trackedOrderIds = mutableSetOf<String>()

            orderFlow?.collect { uiState ->
                if (uiState is Success) {
                    val allOrders = uiState.data
                    val onTheWayOrderIds = allOrders
                        .filter { it.status == OrderStatus.ON_THE_WAY }
                        .mapNotNull { it.id }
                        .toSet()

                    // --- Stop tracking orders that are no longer ON_THE_WAY ---
                    val toStop = trackedOrderIds - onTheWayOrderIds
                    toStop.forEach { orderId ->
                        stopTrackingForOrder(orderId)
                        trackedOrderIds.remove(orderId)
                    }

                    // --- Start tracking new orders that have become ON_THE_WAY ---
                    val toStart = onTheWayOrderIds - trackedOrderIds
                    toStart.forEach { orderId ->
                        val orderToTrack = allOrders.find { it.id == orderId }
                        if (orderToTrack != null) {
                            val mode =
                                if (role == UserRole.PARTNER) OrderServiceLocationTrackingService.MODE_PARTNER else OrderServiceLocationTrackingService.MODE_CUSTOMER
                            startTrackingForOrder(mode, orderToTrack)
                            trackedOrderIds.add(orderId)
                        }
                    }
                } else if (uiState is Empty) {
                    // If the whole list fails or is empty, stop all tracking
                    trackedOrderIds.forEach { stopTrackingForOrder(it) }
                    trackedOrderIds.clear()
                }
            }
        }
    }

    private fun startTrackingForOrder(mode: String, orderService: OrderService) {
        val intent = Intent(application, OrderServiceLocationTrackingService::class.java).apply {
            action = OrderServiceLocationTrackingService.ACTION_START
            putExtra(OrderServiceLocationTrackingService.EXTRA_ORDER_ID, orderService.id)
            putExtra(OrderServiceLocationTrackingService.EXTRA_MODE, mode)
            putExtra(OrderServiceLocationTrackingService.EXTRA_ORDER_SERVICE, orderService)
        }
        application.startService(intent)
        Log.d(TAG, "ViewModel requested to START Location Service in mode: $mode")
    }

    private fun stopTrackingForOrder(orderId: String) {
        val intent = Intent(application, OrderServiceLocationTrackingService::class.java).apply {
            action = OrderServiceLocationTrackingService.ACTION_STOP_ONE
            putExtra(OrderServiceLocationTrackingService.EXTRA_ORDER_ID, orderId)
        }
        application.startService(intent)
        Log.d(TAG, "ViewModel requested to STOP Location Service.")
    }

    fun updateOrderService(orderService: OrderService): Flow<UiState<OrderService>> = flow {
        Log.d(TAG, "Updating order service: $orderService")
        // Emit Loading state first.
        emit(Loading)

        if (!networkConnectivityObserver.isConnected()) {
            _errorEvent.emit(IOException(ErrorLocalizer.FIREBASE_PENDING_WRITE))
            emit(Empty)
        }

        try {
            Log.d(TAG, "Trying to update orderService: $orderService")
            orderServiceRepository.updateOrderService(orderService)

            Log.d(TAG, "Updated orderService: $orderService")
            // On success, emit the Success state.
            emit(Success(orderService))
        } catch (e: Exception) {
            _errorEvent.emit(e)
            Log.e(TAG, "Error updating orderService", e)
            emit(Empty)
        }
    }.catch { e ->
        Log.e(TAG, "Error updating orderService", e)
        if (e is CancellationException) {
            throw e
        }
        _errorEvent.emit(e)
        emit(Empty)
    }

    /**
     * Updates a vehicle.
     * This now correctly returns a Flow that `observeUiStateOneShot` can consume.
     * It emits Loading, then attempts the update, and emits Success or Error.
     */
    fun updateVehicle(vehicle: Vehicle): Flow<UiState<Vehicle>> = flow {
        Log.d(TAG, "Updating vehicle: $vehicle")
        // Emit Loading state first.
        emit(Loading)

        if (!networkConnectivityObserver.isConnected()) {
            _errorEvent.emit(IOException(ErrorLocalizer.FIREBASE_PENDING_WRITE))
            emit(Error(""))
        }

        try {
            Log.d(TAG, "Trying to update vehicle: $vehicle")
            // Perform the suspend function call to the repository.
            // Make sure your repository's updateVehicle function is a 'suspend' function.
            val updatedVehicle =
                vehicleRepository.updateVehicle(vehicle.copy(userId = currentUser.value?.id))

            Log.d(TAG, "Updated vehicle: $updatedVehicle")
            // On success, emit the Success state.
            emit(Success(updatedVehicle))
        } catch (e: Exception) {
            _errorEvent.emit(e)
            Log.e(TAG, "Error updating vehicle", e)
            emit(Empty)
        }
    }.catch { e ->
        Log.e(TAG, "Error updating vehicle", e)
        if (e is CancellationException) {
            throw e
        }
        _errorEvent.emit(e)
        // If the repository call fails, the catch block will execute.
        // We emit the error to be handled by a separate error observer in the Fragment.
        // For observeUiStateOneShot, we can just let it end or emit Empty.
        // In this case, not emitting anything in catch is fine, as the flow will just end on an exception.
        // Or you could emit an empty state if your helper handles it.
        // Let's assume the error is handled by a global error SharedFlow and this flow just terminates.
        emit(Empty)
//        throw e // Re-throwing ensures the flow completes with an error, which can be caught elsewhere.
    }

    fun insertVehicle(vehicle: Vehicle): Flow<UiState<Vehicle>> = flow {
        Log.d(TAG, "Insert vehicle: $vehicle")
        // Emit Loading state first.
        emit(Loading)

        if (!networkConnectivityObserver.isConnected()) {
            _errorEvent.emit(IOException(ErrorLocalizer.FIREBASE_PENDING_WRITE))
            emit(Error(""))
        }

        try {
            Log.d(TAG, "Trying to insert vehicle: $vehicle")
            // Perform the suspend function call to the repository.
            // Make sure your repository's updateVehicle function is a 'suspend' function.
            val newVehicle =
                vehicleRepository.insertVehicle(vehicle)

            Log.d(TAG, "New vehicle: $newVehicle")
            // On success, emit the Success state.
            emit(Success(newVehicle))
        } catch (e: Exception) {
            _errorEvent.emit(e)
            Log.e(TAG, "Error insert vehicle", e)
            emit(Empty)
        }
    }.catch { e ->
        Log.e(TAG, "Error Insert vehicle", e)
        if (e is CancellationException) {
            throw e
        }
        _errorEvent.emit(e)
        // If the repository call fails, the catch block will execute.
        // We emit the error to be handled by a separate error observer in the Fragment.
        // For observeUiStateOneShot, we can just let it end or emit Empty.
        // In this case, not emitting anything in catch is fine, as the flow will just end on an exception.
        // Or you could emit an empty state if your helper handles it.
        // Let's assume the error is handled by a global error SharedFlow and this flow just terminates.
        emit(Empty)
//        throw e // Re-throwing ensures the flow completes with an error, which can be caught elsewhere.
    }

    fun getCurrentUser() = userRepository.getCurrentUserRecord()
}