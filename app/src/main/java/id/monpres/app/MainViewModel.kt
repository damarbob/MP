package id.monpres.app

import android.app.Application
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import id.monpres.app.enums.OrderStatus
import id.monpres.app.enums.OrderStatusType
import id.monpres.app.enums.UserRole
import id.monpres.app.model.OrderService
import id.monpres.app.model.OrderService.Companion.filterByStatuses
import id.monpres.app.model.Vehicle
import id.monpres.app.notification.OrderServiceNotification
import id.monpres.app.repository.OrderServiceRepository
import id.monpres.app.repository.UserRepository
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

@HiltViewModel
class MainViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val orderServiceRepository: OrderServiceRepository,
    private val vehicleRepository: VehicleRepository,
    private val userRepository: UserRepository,
    private val application: Application
) : ViewModel() {
    companion object {
        private const val TAG = "MainViewModel"
    }

    private val _signOutEvent = MutableSharedFlow<Unit>()
    val signOutEvent: SharedFlow<Unit> = _signOutEvent.asSharedFlow()

    private val _externalSignOutSignal =
        MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)
    val externalSignOutSignal: SharedFlow<Unit> = _externalSignOutSignal.asSharedFlow()

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

    private val _ongoingOrderServices = MutableStateFlow<List<OrderService>>(emptyList())
    val ongoingOrderServices: StateFlow<List<OrderService>> = _ongoingOrderServices

    private val _userVehiclesState = MutableStateFlow<UiState<List<Vehicle>>>(UiState.Loading)
    val userVehiclesState: StateFlow<UiState<List<Vehicle>>> = _userVehiclesState.asStateFlow()

    // To keep track of orders for which notifications have been shown/updated
    // Key: OrderID, Value: LastNotifiedStatus
    private val notifiedOrderStatusMap = mutableMapOf<String, OrderStatus>()
    private var justOpenedFromNotificationForOrderId: String? = null

    init {
        Log.d(TAG, "current firebaseAuth: $auth")
    }

    fun observeDataByRole() {
        Log.d(TAG, "current role: ${userRepository.getCurrentUserRecord()?.role}")
        if (userRepository.getCurrentUserRecord()?.role == UserRole.CUSTOMER) {
            observeUserOrderServices()
            observeUserVehicles()
            observeOngoingOrderService()
        } else if (userRepository.getCurrentUserRecord()?.role == UserRole.PARTNER) {
            observePartnerOrderServices()
        }
    }

    private fun observeUserOrderServices() {
        viewModelScope.launch {
            orderServiceRepository.observeOrderServicesByUserId()
                .takeUntilSignal(externalSignOutSignal) // Stop observation on logout
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
                    _userOrderServicesState.value = state
                }
        }
    }

    private fun observePartnerOrderServices() {
        viewModelScope.launch {
            orderServiceRepository.observeOrderServicesByPartnerId()
                .takeUntilSignal(externalSignOutSignal) // Stop observation on logout
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
                    _partnerOrderServicesState.value = state
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
                            val orderService =
                                userOrderServicesUiState.data.find { it.id == orderId }
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
                        Log.i(TAG, "OrderService by ID observation cancelled.", e)
                        // Optionally reset state or re-throw
                        // _userOrderServiceState.value = UiState.Loading // Or an error specific to cancellation
                        throw e // Re-throw to propagate cancellation
                    }
                    Log.e(TAG, "Error in OrderService by ID flow collection", e)
                    _userOrderServiceState.value = UiState.Error(e)
                }
                .collect { specificOrderServiceUiState ->
                    _userOrderServiceState.value = specificOrderServiceUiState
                }
        }
    }

    // If ServiceProcessFragment needs a specific order by ID
    fun observeOngoingOrderService() {
        viewModelScope.launch {
            // Assuming _userOrderServicesState is already being populated by another Flow
            // from orderServiceRepository.observeAllOrderServices() or similar.
            // We'll transform that existing Flow.
            _userOrderServicesState
                .takeUntilSignal(externalSignOutSignal) // Keep this if it's relevant for stopping observation
                .map { userOrderServicesUiState -> // Transform the UiState<List<OrderService>>
                    when (userOrderServicesUiState) {
                        is UiState.Loading -> listOf()
                        is UiState.Error -> listOf()
                        is UiState.Success ->
                            userOrderServicesUiState.data.filterByStatuses(OrderStatus.entries.filter { it.type != OrderStatusType.CLOSED })
                    }
                }
                .catch { e ->
                    if (e is CancellationException) {
                        Log.i(TAG, "Ongoing OrderService observation cancelled.", e)
                        // Optionally reset state or re-throw
                        // _userOrderServiceState.value = UiState.Loading // Or an error specific to cancellation
                        throw e // Re-throw to propagate cancellation
                    }
                    Log.e(TAG, "Error ongoing orders", e)
                }
                .collectLatest { orderServices ->
                    _ongoingOrderServices.value = orderServices

                    // Process notifications for these orders
                    processOrderNotifications(orderServices)
                }
        }
    }

    private fun processOrderNotifications(currentOrders: List<OrderService>) {
        val context = application.applicationContext
        val currentlyOpenedOrderId = justOpenedFromNotificationForOrderId // Cache it for this run
        // Clear it after use so subsequent non-notification updates are processed normally
        justOpenedFromNotificationForOrderId = null

        // Identify new or updated orders
        currentOrders.forEach { order ->
            val lastNotifiedStatus = notifiedOrderStatusMap[order.id]
            val currentStatus = order.status

            if (order.id == currentlyOpenedOrderId && lastNotifiedStatus == currentStatus) {
                // This specific order was just clicked to open the app,
                // and its status hasn't changed since the notification that was clicked.
                // Let's assume the notification for this state is already visible.
                // We *do* want to ensure it's in our notifiedOrderStatusMap so it's tracked.
                if (notifiedOrderStatusMap[order.id] == null) { // Ensure it's tracked even if we skip re-notifying
                    notifiedOrderStatusMap[order.id!!] = currentStatus!!
                }
                Log.d(TAG, "Skipping re-notification for order ${order.id} as it was just opened.")
                return@forEach // Skip to the next order for notification processing
            }

            if (lastNotifiedStatus == null || lastNotifiedStatus != currentStatus) {
                // New order or status has changed since last notification
                Log.d(TAG, "Order ${order.id} needs notification. Current: $currentStatus, Last Notified: $lastNotifiedStatus")

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    OrderServiceNotification.showOrUpdateNotification(
                        context,
                        order.id!!,
                        currentStatus!!
                    )
                }
                notifiedOrderStatusMap[order.id!!] = currentStatus as OrderStatus
            }
        }

        // Identify orders that were completed/cancelled and remove their notifications
        // (and remove from notifiedOrderStatusMap)
        val currentOrderIds = currentOrders.map { it.id }.toSet()
        val ordersToRemoveNotification = notifiedOrderStatusMap.filterKeys { !currentOrderIds.contains(it) }

        ordersToRemoveNotification.forEach { (orderId, _) ->
            Log.d(TAG, "Order $orderId no longer ongoing or status implies removal. Cancelling notification.")
            OrderServiceNotification.cancelNotification(context, orderId)
            notifiedOrderStatusMap.remove(orderId)
        }
    }

    private fun observeUserVehicles() {
        viewModelScope.launch {
            vehicleRepository.getVehiclesByUserIdFlow()
                .takeUntilSignal(externalSignOutSignal) // Stop observation on logout
                .catch { e ->
                    if (e is CancellationException) {
                        Log.i(TAG, "Vehicle observation cancelled.", e)
                        // Optionally reset state or re-throw
                        // _userOrdersState.value = UiState.Loading // Or an error specific to cancellation
                        throw e
                    }
                    Log.e(TAG, "Error in vehicles flow collection", e)
                    _userVehiclesState.value = UiState.Error(e)
                }
                .collect { state ->
                    _userVehiclesState.value = state
                }
        }
    }

    // Call this from your Activity after handling intent extras
    fun setOpenedFromNotification(orderId: String?) {
        justOpenedFromNotificationForOrderId = orderId
        Log.d(TAG, "ViewModel informed: opened from notification for order $orderId")
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