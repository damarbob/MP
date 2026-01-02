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

    private val _userOrderServicesState =
        MutableStateFlow<UiState<List<OrderService>>>(Loading)
    val userOrderServicesState: StateFlow<UiState<List<OrderService>>> =
        _userOrderServicesState.asStateFlow()

    private val _partnerOrderServicesState =
        MutableStateFlow<UiState<List<OrderService>>>(Loading)
    val partnerOrderServicesState: StateFlow<UiState<List<OrderService>>> =
        _partnerOrderServicesState.asStateFlow()

    // New State for ALL orders (e.g. for Admin or Public feed)
    private val _allOrderServicesState =
        MutableStateFlow<UiState<List<OrderService>>>(Loading)
    val allOrderServicesState: StateFlow<UiState<List<OrderService>>> =
        _allOrderServicesState.asStateFlow()

    private val _openedOrderServiceState = MutableStateFlow<UiState<OrderService>>(Loading)
    val openedOrderServiceState: StateFlow<UiState<OrderService>> =
        _openedOrderServiceState.asStateFlow()

    private val _userVehiclesState = MutableStateFlow<UiState<List<Vehicle>>>(Loading)
    val userVehiclesState: StateFlow<UiState<List<Vehicle>>> = _userVehiclesState.asStateFlow()

    private val _userVehicles = MutableStateFlow<List<Vehicle>>(emptyList())
    val userVehicles: StateFlow<List<Vehicle>> = _userVehicles.asStateFlow()

    private val _currentUser: MutableStateFlow<MontirPresisiUser?> = MutableStateFlow(null)
    val currentUser: StateFlow<MontirPresisiUser?> = _currentUser.asStateFlow()

    // Event channel for one-time error messages
    private val _errorEvent = MutableSharedFlow<Throwable>()
    val errorEvent: SharedFlow<Throwable> = _errorEvent.asSharedFlow()

    // --- Job Management (To prevent duplicate collectors) ---
    private var userOrdersJob: Job? = null
    private var partnerOrdersJob: Job? = null
    private var allOrdersJob: Job? = null // Job for the new observer
    private var openedOrderJob: Job? = null
    private var vehiclesJob: Job? = null

    init {
        Log.d(TAG, "ViewModel init")
        viewModelScope.launch {
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
        // Reset all orders state if connection is lost while loading
        _allOrderServicesState.value =
            if (_allOrderServicesState.value == Loading) Empty else _allOrderServicesState.value
    }

    fun observeDataByRole(user: MontirPresisiUser) {
        Log.d(TAG, "current role: ${user.role}")
        when (user.role) {
            UserRole.PARTNER -> {
                observePartnerOrderServices()
            }

            UserRole.ADMIN -> {
                observeAllOrderServices()
            }

            else -> {
                observeUserOrderServices()
                observeUserVehicles()
            }
        }
        // If you have an ADMIN role or want to trigger this for everyone, call observeAllOrderServices() here.
    }

    private fun observeUserOrderServices() {
        userOrdersJob?.cancel()

        userOrdersJob = viewModelScope.launch {
            orderServiceRepository.observeOrderServicesByUserId()
                .takeUntilSignal(sessionManager.externalSignOutSignal)
                .onStart { _userOrderServicesState.value = Loading }
                .catch { e ->
                    if (e is CancellationException) {
                        Log.i(TAG, "User orders observation was cancelled.")
                        throw e
                    }
                    Log.e(TAG, "Error observing user orders", e)
                    _errorEvent.emit(e)
                    _userOrderServicesState.value = Empty
                }
                .collect { orders ->
                    _userOrderServicesState.value =
                        if (orders.isEmpty()) Empty else Success(orders)
                }
        }
    }

    private fun observePartnerOrderServices() {
        partnerOrdersJob?.cancel()

        partnerOrdersJob = viewModelScope.launch {
            orderServiceRepository.observeOrderServicesByPartnerId()
                .takeUntilSignal(sessionManager.externalSignOutSignal)
                .onStart { _partnerOrderServicesState.value = Loading }
                .catch { e ->
                    if (e is CancellationException) {
                        Log.i(TAG, "Partner orders observation was cancelled.")
                        throw e
                    }
                    Log.e(TAG, "Error observing partner orders", e)
                    _errorEvent.emit(e)
                    _partnerOrderServicesState.value = Empty
                }
                .collect { orders ->
                    _partnerOrderServicesState.value =
                        if (orders.isEmpty()) Empty else Success(orders)
                }
        }
    }

    /**
     * Observes ALL order services (unfiltered).
     * Use this for Admin panels or public feeds.
     */
    fun observeAllOrderServices() {
        allOrdersJob?.cancel()

        allOrdersJob = viewModelScope.launch {
            orderServiceRepository.observeOrderServices()
                .takeUntilSignal(sessionManager.externalSignOutSignal)
                .onStart { _allOrderServicesState.value = Loading }
                .catch { e ->
                    if (e is CancellationException) {
                        Log.i(TAG, "All orders observation was cancelled.")
                        throw e
                    }
                    Log.e(TAG, "Error observing all orders", e)
                    _errorEvent.emit(e)
                    _allOrderServicesState.value = Empty
                }
                .collect { orders ->
                    _allOrderServicesState.value =
                        if (orders.isEmpty()) Empty else Success(orders)
                }
        }
    }

    fun observeOrderServiceById(orderId: String) {
        openedOrderJob?.cancel()

        val role = userRepository.getCurrentUserRecord()?.role
        // We prioritized role-based lists, but you could fall back to allOrderServicesState if needed
        val masterListFlow = when (role) {
            UserRole.CUSTOMER -> userOrderServicesState
            UserRole.PARTNER -> partnerOrderServicesState
            UserRole.ADMIN -> allOrderServicesState
            else -> null
        }

        if (masterListFlow == null) {
            _openedOrderServiceState.value = Empty
            return
        }

        openedOrderJob = viewModelScope.launch {
            masterListFlow.collect { listState ->
                when (listState) {
                    is Loading -> _openedOrderServiceState.value = Loading
                    is Empty -> _openedOrderServiceState.value = Empty
                    is Success -> {
                        val orderService = listState.data.find { it.id == orderId }
                        if (orderService != null) {
                            _openedOrderServiceState.value = Success(orderService)
                        } else {
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
        vehiclesJob?.cancel()

        vehiclesJob = viewModelScope.launch {
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

    private fun manageLocationServiceLifecycle() {
        viewModelScope.launch {
            currentUser.filterNotNull().first()

            val role = currentUser.value?.role
            val orderFlow = when (role) {
                UserRole.CUSTOMER -> userOrderServicesState
                UserRole.PARTNER -> partnerOrderServicesState
                else -> null
            }

            val trackedOrderIds = mutableSetOf<String>()

            orderFlow?.collect { uiState ->
                if (uiState is Success) {
                    val allOrders = uiState.data
                    val onTheWayOrderIds = allOrders
                        .filter { it.status == OrderStatus.ON_THE_WAY }
                        .mapNotNull { it.id }
                        .toSet()

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
}