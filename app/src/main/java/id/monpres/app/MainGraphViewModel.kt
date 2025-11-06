package id.monpres.app

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.monpres.app.enums.OrderStatus
import id.monpres.app.enums.UserRole
import id.monpres.app.model.MontirPresisiUser
import id.monpres.app.model.OrderService
import id.monpres.app.model.Vehicle
import id.monpres.app.repository.OrderServiceRepository
import id.monpres.app.repository.UserRepository
import id.monpres.app.repository.VehicleRepository
import id.monpres.app.service.OrderServiceLocationTrackingService
import id.monpres.app.state.UiState
import id.monpres.app.utils.takeUntilSignal
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

@HiltViewModel
class MainGraphViewModel @Inject constructor(
    private val orderServiceRepository: OrderServiceRepository,
    private val vehicleRepository: VehicleRepository,
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val application: Application
) : ViewModel() {
    companion object {
        private val TAG = MainGraphViewModel::class.simpleName
    }

    private val _userOrderServicesState =
        MutableStateFlow<UiState<List<OrderService>>>(UiState.Loading)
    val userOrderServicesState: StateFlow<UiState<List<OrderService>>> =
        _userOrderServicesState.asStateFlow()

    private val _partnerOrderServicesState =
        MutableStateFlow<UiState<List<OrderService>>>(UiState.Loading)
    val partnerOrderServicesState: StateFlow<UiState<List<OrderService>>> =
        _partnerOrderServicesState.asStateFlow()

    private val _openedOrderServiceState = MutableStateFlow<UiState<OrderService>>(UiState.Loading)
    val openedOrderServiceState: StateFlow<UiState<OrderService>> =
        _openedOrderServiceState.asStateFlow()

    private var openedOrderJob: Job? = null // To manage the collector coroutine

    private val _userVehiclesState = MutableStateFlow<UiState<List<Vehicle>>>(UiState.Loading)
    val userVehiclesState: StateFlow<UiState<List<Vehicle>>> = _userVehiclesState.asStateFlow()
    private val _userVehicles = MutableStateFlow<List<Vehicle>>(emptyList())
    val userVehicles: StateFlow<List<Vehicle>> = _userVehicles.asStateFlow()

    // Event channel for one-time error messages
    private val _errorEvent = MutableSharedFlow<Throwable>()
    val errorEvent: SharedFlow<Throwable> = _errorEvent.asSharedFlow()

    private lateinit var currentUser: MontirPresisiUser

    // To keep track of orders for which notifications have been shown/updated
    // Key: OrderID, Value: LastNotifiedStatus
    // TODO: Remove this if not needed
    private val notifiedOrderStatusMap = mutableMapOf<String, OrderStatus>()
    private var justOpenedFromNotificationForOrderId: String? = null

    init {
        // Start observing data as soon as this ViewModel is created
        Log.d(TAG, "ViewModel init")
        viewModelScope.launch {
            currentUser = userRepository.userRecord.filterNotNull().first()
            observeDataByRole(currentUser)

            manageLocationServiceLifecycle()
        }
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
        viewModelScope.launch {
            orderServiceRepository.observeOrderServicesByUserId()
                .takeUntilSignal(sessionManager.externalSignOutSignal)
                .onStart { _userOrderServicesState.value = UiState.Loading }
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
                    _userOrderServicesState.value = UiState.Empty
                }
                .collect { orders ->
                    // The repository gives us clean data.
                    _userOrderServicesState.value =
                        if (orders.isEmpty()) UiState.Empty else UiState.Success(orders)
                }
        }
    }

    private fun observePartnerOrderServices() {
        viewModelScope.launch {
            orderServiceRepository.observeOrderServicesByPartnerId()
                .takeUntilSignal(sessionManager.externalSignOutSignal)
                .onStart { _partnerOrderServicesState.value = UiState.Loading }
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
                    _partnerOrderServicesState.value = UiState.Empty
                }
                .collect { orders ->
                    _partnerOrderServicesState.value =
                        if (orders.isEmpty()) UiState.Empty else UiState.Success(orders)
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
            _openedOrderServiceState.value = UiState.Empty // Or an error state
            return
        }

        openedOrderJob = viewModelScope.launch {
            masterListFlow.collect { listState ->
                when (listState) {
                    is UiState.Loading -> _openedOrderServiceState.value = UiState.Loading
                    is UiState.Empty -> _openedOrderServiceState.value = UiState.Empty
                    is UiState.Success -> {
                        val orderService = listState.data.find { it.id == orderId }
                        if (orderService != null) {
                            _openedOrderServiceState.value = UiState.Success(orderService)
                        } else {
                            // The order was not found in the master list.
                            _openedOrderServiceState.value = UiState.Empty
                        }
                    }
                }
            }
        }
    }

    fun stopObservingOpenedOrder() {
        openedOrderJob?.cancel()
        openedOrderJob = null
    }

    private fun observeUserVehicles() {
        viewModelScope.launch {
            vehicleRepository.getVehiclesByUserIdFlow(this)
                .takeUntilSignal(sessionManager.externalSignOutSignal) // Stop observation on logout
                .onStart {
                    _userVehiclesState.value = UiState.Loading
                }
                .catch { e ->
                    if (e is CancellationException) {
                        Log.i(TAG, "Vehicle observation cancelled.", e)
                        // Optionally reset state or re-throw
                        // _userOrdersState.value = UiState.Loading // Or an error specific to cancellation
                        throw e
                    }
                    Log.e(TAG, "Error in vehicles flow collection", e)
                    _userVehiclesState.value = UiState.Empty
                    _errorEvent.emit(e)
                }
                .collect { vehicles ->
                    Log.d(TAG, "User vehicles: $vehicles")
                    Log.d(TAG, "User vehicles state: ${_userVehiclesState.value}")
                    _userVehiclesState.value =
                        if (vehicles.isEmpty()) UiState.Empty else UiState.Success(vehicles)
                    _userVehicles.value = vehicles
                }
        }
    }

    private fun manageLocationServiceLifecycle() {
        viewModelScope.launch {
            val role = currentUser.role
            val orderFlow = when (role) {
                UserRole.CUSTOMER -> userOrderServicesState
                UserRole.PARTNER -> partnerOrderServicesState
                else -> null
            }

            // Keep track of which orders we are currently tracking
            val trackedOrderIds = mutableSetOf<String>()

            orderFlow?.collect { uiState ->
                if (uiState is UiState.Success) {
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
                } else if (uiState is UiState.Empty) {
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

    // Call this from your Activity after handling intent extras
    fun setOpenedFromNotification(orderId: String?) {
        justOpenedFromNotificationForOrderId = orderId
        Log.d(TAG, "ViewModel informed: opened from notification for order $orderId")
    }

    fun getCurrentUser() = userRepository.getCurrentUserRecord()

    fun getNotifiedOrderStatusMap() = notifiedOrderStatusMap
    fun getJustOpenedFromNotificationOrderId() = justOpenedFromNotificationForOrderId
    fun setJustOpenedFromNotificationOrderId(orderId: String?) {
        justOpenedFromNotificationForOrderId = orderId
    }

    fun clearJustOpenedFromNotificationOrderId() {
        justOpenedFromNotificationForOrderId = null
    }

    fun clearNotifiedOrderStatusMap() {
        notifiedOrderStatusMap.clear()
    }

    fun setNotifiedOrderStatusMap(map: MutableMap<String, OrderStatus>) {
        notifiedOrderStatusMap.clear()
        notifiedOrderStatusMap.putAll(map)
    }

    fun removeNotifiedOrderStatus(orderId: String) {
        notifiedOrderStatusMap.remove(orderId)
    }
}