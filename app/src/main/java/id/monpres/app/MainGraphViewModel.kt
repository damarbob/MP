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

    // --- UI States ---
    private val _userVehiclesState = MutableStateFlow<UiState<List<Vehicle>>>(Loading)
    val userVehiclesState: StateFlow<UiState<List<Vehicle>>> = _userVehiclesState.asStateFlow()

    private val _userVehicles = MutableStateFlow<List<Vehicle>>(emptyList())
    val userVehicles: StateFlow<List<Vehicle>> = _userVehicles.asStateFlow()

    private val _currentUser: MutableStateFlow<MontirPresisiUser?> = MutableStateFlow(null)
    val currentUser: StateFlow<MontirPresisiUser?> = _currentUser.asStateFlow()

    // A single StateFlow for recent orders, adaptable to the user's role.
    private val _recentOrderServicesState =
        MutableStateFlow<UiState<List<OrderService>>>(Loading)
    val recentOrderServicesState: StateFlow<UiState<List<OrderService>>> =
        _recentOrderServicesState.asStateFlow()

    // A flow for a single opened order, for ServiceProcessFragment
    private val _openedOrderServiceState = MutableStateFlow<UiState<OrderService>>(Loading)
    val openedOrderServiceState: StateFlow<UiState<OrderService>> =
        _openedOrderServiceState.asStateFlow()

    // Event channel for one-time error messages
    private val _errorEvent = MutableSharedFlow<Throwable>()
    val errorEvent: SharedFlow<Throwable> = _errorEvent.asSharedFlow()

    // --- Jobs ---
    private var dataObserverJob: Job? = null
    private var openedOrderJob: Job? = null

    private var vehiclesJob: Job? = null

    init {
        Log.d(TAG, "ViewModel init")
        viewModelScope.launch(Dispatchers.Default) {
            networkConnectivityObserver.networkStatus.collect { status ->
                when (status.state) {
                    ConnectionState.Connected -> {
                        if (auth.currentUser != null && _currentUser.value?.id != auth.currentUser?.uid) {
                            observeData()
                        }
                        else if (auth.currentUser == null) {
                            dataObserverJob?.cancel()
                            _currentUser.value = null
                            changeStates()
                        }
                    }

                    ConnectionState.Disconnected -> {
                        changeStates()
                    }
                }
            }
        }
    }

    private fun observeData() {
        dataObserverJob?.cancel()
        dataObserverJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                _currentUser.value = withTimeout(TimeUnit.SECONDS.toMillis(10)) {
                    userRepository.userRecord.filterNotNull().first()
                }
                currentUser.value?.let {
                    Log.d(TAG, "User: $it")

                    orderServiceRepository.startRealTimeOrderUpdates(
                        it.id!!,
                        it.role ?: UserRole.CUSTOMER
                    )

                    if (it.role == UserRole.CUSTOMER) observeUserVehicles()

                    // Now, observe the LOCAL DATABASE for changes.
                    orderServiceRepository.observeRecentOrderServices(
                        it.id,
                        it.role ?: UserRole.CUSTOMER
                    )
                        .onStart {
                            _recentOrderServicesState.value = Loading
                        }
                        .catch { e ->
                            _recentOrderServicesState.value =
                                Error(e.message ?: "")
                        }
                        .collect { orders ->
                            _recentOrderServicesState.value =
                                if (orders.isEmpty()) Empty else Success(
                                    orders
                                )
                        }
                }

            } catch (e: Exception) {
                if (e !is CancellationException) {
                    Log.e(TAG, "Error fetching user or observing data", e)
                    _recentOrderServicesState.value = Error(e.message ?: "")
                }
                changeStates()
                _errorEvent.emit(e)
            }
        }
    }

    /**
     * Observes a single order from the local database.
     * For use by ServiceProcessFragment.
     */
    fun observeOrderServiceById(orderId: String) {
        openedOrderJob?.cancel()
        openedOrderJob = viewModelScope.launch(Dispatchers.IO) {
            orderServiceRepository.observeOrderServiceById(orderId)
                .collect { order ->
                    _openedOrderServiceState.value = when (order) {
                        null -> Empty
                        else -> Success(order)
                    }
                }
        }
    }

    fun stopObservingOpenedOrder() {
        openedOrderJob?.cancel()
        openedOrderJob = null
    }

    private fun changeStates() {
        _userVehiclesState.value =
            if (_userVehiclesState.value == Loading) Empty else _userVehiclesState.value
        _recentOrderServicesState.value =
            if (_recentOrderServicesState.value == Loading) Empty else _recentOrderServicesState.value
        _openedOrderServiceState.value =
            if (_openedOrderServiceState.value == Loading) Empty else _openedOrderServiceState.value
    }

    private fun observeUserVehicles() {
        vehiclesJob?.cancel()

        vehiclesJob = viewModelScope.launch(Dispatchers.Default) {
            vehicleRepository.getVehiclesByUserIdFlow(this)
                .takeUntilSignal(sessionManager.externalSignOutSignal)
                .onStart { _userVehiclesState.value = Loading }
                .catch { e ->
                    if (e is CancellationException) {
                        Log.i(TAG, "Vehicle observation cancelled.", e)
                        throw e
                    }
                    Log.e(TAG, "Error in vehicles flow collection", e)
                    _userVehiclesState.value = Empty
                    _errorEvent.emit(e)
                }
                .collect { vehicles ->
                    Log.d(TAG, "User vehicles: $vehicles")
                    _userVehiclesState.value =
                        if (vehicles.isEmpty()) Empty else Success(vehicles)
                    _userVehicles.value = vehicles
                }
        }
    }

    fun manageLocationServiceLifecycle() {
        viewModelScope.launch(Dispatchers.Default) {
            Log.d(TAG, "manageLocationServiceLifecycle")
            val role = userRepository.userRecord.filterNotNull().first().role

            val orderFlow = recentOrderServicesState

            val trackedOrderIds = mutableSetOf<String>()

            orderFlow.collect { uiState ->
                if (uiState is Success) {
                    val allOrders = uiState.data
                    val onTheWayOrderIds = allOrders
                        .filter { it.status == OrderStatus.ON_THE_WAY }
                        .map { it.id }
                        .toSet()
                    Log.d(TAG, "onTheWayOrderIds: $onTheWayOrderIds")

                    val toStop = trackedOrderIds - onTheWayOrderIds
                    toStop.forEach { orderId ->
                        stopTrackingForOrder(orderId)
                        trackedOrderIds.remove(orderId)
                    }

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
        emit(Loading)

        if (!networkConnectivityObserver.isConnected()) {
            _errorEvent.emit(IOException(ErrorLocalizer.FIREBASE_PENDING_WRITE))
            emit(Empty)
            return@flow
        }

        try {
            orderServiceRepository.updateOrderService(orderService)
            emit(Success(orderService))
        } catch (e: Exception) {
            _errorEvent.emit(e)
            Log.e(TAG, "Error updating orderService", e)
            emit(Empty)
        }
    }.catch { e ->
        Log.e(TAG, "Error updating orderService", e)
        if (e is CancellationException) throw e
        _errorEvent.emit(e)
        emit(Empty)
    }

    fun updateVehicle(vehicle: Vehicle): Flow<UiState<Vehicle>> = flow {
        Log.d(TAG, "Updating vehicle: $vehicle")
        emit(Loading)

        if (!networkConnectivityObserver.isConnected()) {
            _errorEvent.emit(IOException(ErrorLocalizer.FIREBASE_PENDING_WRITE))
            emit(Error(""))
            return@flow
        }

        try {
            val updatedVehicle =
                vehicleRepository.updateVehicle(vehicle.copy(userId = currentUser.value?.id))
            emit(Success(updatedVehicle))
        } catch (e: Exception) {
            _errorEvent.emit(e)
            Log.e(TAG, "Error updating vehicle", e)
            emit(Empty)
        }
    }.catch { e ->
        Log.e(TAG, "Error updating vehicle", e)
        if (e is CancellationException) throw e
        _errorEvent.emit(e)
        emit(Empty)
    }

    fun insertVehicle(vehicle: Vehicle): Flow<UiState<Vehicle>> = flow {
        Log.d(TAG, "Insert vehicle: $vehicle")
        emit(Loading)

        if (!networkConnectivityObserver.isConnected()) {
            _errorEvent.emit(IOException(ErrorLocalizer.FIREBASE_PENDING_WRITE))
            emit(Error(""))
            return@flow
        }

        try {
            val newVehicle = vehicleRepository.insertVehicle(vehicle)
            emit(Success(newVehicle))
        } catch (e: Exception) {
            _errorEvent.emit(e)
            Log.e(TAG, "Error insert vehicle", e)
            emit(Empty)
        }
    }.catch { e ->
        Log.e(TAG, "Error Insert vehicle", e)
        if (e is CancellationException) throw e
        _errorEvent.emit(e)
        emit(Empty)
    }

    fun getCurrentUser() = userRepository.getCurrentUserRecord()

    override fun onCleared() {
        super.onCleared()
        // Stop the listener when the app is fully closed.
        orderServiceRepository.stopRealTimeOrderUpdates()
    }
}