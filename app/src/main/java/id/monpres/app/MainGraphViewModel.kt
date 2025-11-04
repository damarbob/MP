package id.monpres.app

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
import id.monpres.app.state.UiState
import id.monpres.app.utils.takeUntilSignal
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

@HiltViewModel
class MainGraphViewModel @Inject constructor(
    private val orderServiceRepository: OrderServiceRepository,
    private val vehicleRepository: VehicleRepository,
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository
) : ViewModel() {
    companion object {
        private val TAG = MainGraphViewModel::class.simpleName
    }

    private val _stopObserveSignal =
        MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)
    val stopObserveSignal: SharedFlow<Unit> = _stopObserveSignal.asSharedFlow()

    private val _userOrderServicesState =
        MutableStateFlow<UiState<List<OrderService>>>(UiState.Loading)
    val userOrderServicesState: StateFlow<UiState<List<OrderService>>> =
        _userOrderServicesState.asStateFlow()

    private val _userOrderServiceState = MutableStateFlow<UiState<OrderService>>(UiState.Loading)
    val userOrderServiceState: StateFlow<UiState<OrderService>> =
        _userOrderServiceState.asStateFlow()

    private val _partnerOrderServicesState =
        MutableStateFlow<UiState<List<OrderService>>>(UiState.Loading)
    val partnerOrderServicesState: StateFlow<UiState<List<OrderService>>> =
        _partnerOrderServicesState.asStateFlow()

    private val _partnerOrderServiceState = MutableStateFlow<UiState<OrderService>>(UiState.Loading)
    val partnerOrderServiceState: StateFlow<UiState<OrderService>> =
        _partnerOrderServiceState.asStateFlow()

    private val _userVehiclesState = MutableStateFlow<UiState<List<Vehicle>>>(UiState.Loading)
    val userVehiclesState: StateFlow<UiState<List<Vehicle>>> = _userVehiclesState.asStateFlow()
    private val _userVehicles = MutableStateFlow<List<Vehicle>>(emptyList())
    val userVehicles: StateFlow<List<Vehicle>> = _userVehicles.asStateFlow()

    // Event channel for one-time error messages
    private val _errorEvent = MutableSharedFlow<Throwable>()
    val errorEvent: SharedFlow<Throwable> = _errorEvent.asSharedFlow()

    // To keep track of orders for which notifications have been shown/updated
    // Key: OrderID, Value: LastNotifiedStatus
    // TODO: Remove this if not needed
    private val notifiedOrderStatusMap = mutableMapOf<String, OrderStatus>()
    private var justOpenedFromNotificationForOrderId: String? = null

    init {
        // Start observing data as soon as this ViewModel is created
        Log.d(TAG, "ViewModel init")
        viewModelScope.launch {
            val user = userRepository.userRecord.filterNotNull().first()
            observeDataByRole(user)
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
                    _userOrderServicesState.value = if (orders.isEmpty()) UiState.Empty else UiState.Success(orders)
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
                    _partnerOrderServicesState.value = if (orders.isEmpty()) UiState.Empty else UiState.Success(orders)
                }
        }
    }

    /*private fun observeUserOrderServices() {
        viewModelScope.launch {
            orderServiceRepository.observeOrderServicesByUserId()
                .takeUntilSignal(sessionManager.externalSignOutSignal) // Stop observation on logout
                .catch { e ->
                    if (e is CancellationException) {
                        Log.i(TAG, "User orders observation cancelled.", e)
                        // Optionally reset state or re-throw
                        // _userOrdersState.value = UiState.Loading // Or an error specific to cancellation
                        throw e
                    }
                    Log.e(TAG, "Error in user orders flow collection", e)
                    _userOrderServicesState.value = UiState.Error(e)
                }
                .collect { state ->
                    Log.d(TAG, "User orders state: $state")
                    _userOrderServicesState.value = state
                }
        }
    }

    private fun observePartnerOrderServices() {
        viewModelScope.launch {
            orderServiceRepository.observeOrderServicesByPartnerId()
                .takeUntilSignal(sessionManager.externalSignOutSignal) // Stop observation on logout
                .catch { e ->
                    if (e is CancellationException) {
                        Log.i(TAG, "Partner orders observation cancelled.", e)
                        // Optionally reset state or re-throw
                        // _partnerOrdersState.value = UiState.Loading // Or an error specific to cancellation
                        throw e
                    }
                    Log.e(TAG, "Error in partner orders flow collection", e)
                    _partnerOrderServicesState.value = UiState.Error(e)
                }
                .collect { state ->
                    Log.d(TAG, "Partner orders state: $state")
                    _partnerOrderServicesState.value = state
                }
        }
    }*/

    private fun observeUserOrderServiceById(orderId: String) {
        viewModelScope.launch {
            // Assuming _userOrderServicesState is already being populated by another Flow
            // from orderServiceRepository.observeAllOrderServices() or similar.
            // We'll transform that existing Flow.
            _userOrderServicesState
                .takeUntilSignal(stopObserveSignal) // Keep this if it's relevant for stopping observation
                .map { userOrderServicesUiState -> // Transform the UiState<List<OrderService>>
                    when (userOrderServicesUiState) {
                        is UiState.Loading -> UiState.Loading
                        is UiState.Empty -> UiState.Empty
                        is UiState.Success -> {
                            val orderService =
                                userOrderServicesUiState.data.find { it.id == orderId }
                            if (orderService != null) {
                                UiState.Success(orderService)
                            } else {
                                // Be more specific about the error if possible.
                                // Is it truly a "not found" scenario or an unexpected null?
                                _errorEvent.emit(NoSuchElementException())
                                UiState.Empty
                            }
                        }
                    }
                }
                .catch { e ->
                    if (e is CancellationException) {
                        Log.i(TAG, "OrderService by ID observation cancelled.", e)
                        // Optionally reset state or re-throw
                        // _userOrderServiceState.value = UiState.Loading // Or an error specific to cancellation
                        throw e // Re-throw to propagate cancellation
                    }
                    Log.e(TAG, "Error in OrderService by ID flow collection", e)
                    _userOrderServiceState.value = UiState.Empty
                    _errorEvent.emit(e)
                }
                .collect { specificOrderServiceUiState ->
                    _userOrderServiceState.value = specificOrderServiceUiState
                }
        }
    }

    private fun observePartnerOrderServiceById(orderId: String) {
        viewModelScope.launch {
            // Assuming _userOrderServicesState is already being populated by another Flow
            // from orderServiceRepository.observeAllOrderServices() or similar.
            // We'll transform that existing Flow.
            _partnerOrderServicesState
                .takeUntilSignal(stopObserveSignal) // Keep this if it's relevant for stopping observation
                .map { partnerOrderServicesUiState -> // Transform the UiState<List<OrderService>>
                    when (partnerOrderServicesUiState) {
                        is UiState.Loading -> UiState.Loading
                        is UiState.Empty -> UiState.Empty
                        is UiState.Success -> {
                            val orderService =
                                partnerOrderServicesUiState.data.find { it.id == orderId }
                            if (orderService != null) {
                                UiState.Success(orderService)
                            } else {
                                // Be more specific about the error if possible.
                                // Is it truly a "not found" scenario or an unexpected null?
                                _errorEvent.emit(NoSuchElementException())
                                UiState.Empty
                            }
                        }
                    }
                }
                .catch { e ->
                    if (e is CancellationException) {
                        Log.i(TAG, "OrderService by ID observation cancelled.", e)
                        // Optionally reset state or re-throw
                        // _partnerOrderServiceState.value = UiState.Loading // Or an error specific to cancellation
                        throw e // Re-throw to propagate cancellation
                    }
                    Log.e(TAG, "Error in OrderService by ID flow collection", e)
                    _partnerOrderServiceState.value = UiState.Empty
                    _errorEvent.emit(e)
                }
                .collect { specificOrderServiceUiState ->
                    _partnerOrderServiceState.value = specificOrderServiceUiState
                }
        }
    }

    // If ServiceProcessFragment needs a specific order by ID
    fun observeOrderServiceById(orderId: String) {
        when (getCurrentUser()?.role) {
            UserRole.CUSTOMER -> observeUserOrderServiceById(orderId)
            UserRole.PARTNER -> observePartnerOrderServiceById(orderId)
            else -> return
        }
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
                    _userVehiclesState.value = if (vehicles.isEmpty()) UiState.Empty else UiState.Success(vehicles)
                    _userVehicles.value = vehicles
                }
        }
    }

    // Call this from your Activity after handling intent extras
    fun setOpenedFromNotification(orderId: String?) {
        justOpenedFromNotificationForOrderId = orderId
        Log.d(TAG, "ViewModel informed: opened from notification for order $orderId")
    }

    fun stopObserve() {
        viewModelScope.launch {
            _stopObserveSignal.emit(Unit)
        }
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