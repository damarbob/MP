package id.monpres.app

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.lifecycle.HiltViewModel
import id.monpres.app.enums.OrderStatus
import id.monpres.app.enums.UserRole
import id.monpres.app.model.OrderService
import id.monpres.app.model.Vehicle
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

@HiltViewModel
class MainViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val orderServiceRepository: OrderServiceRepository,
    private val vehicleRepository: VehicleRepository,
    private val userRepository: UserRepository
) : ViewModel() {
    companion object {
        private const val TAG = "MainViewModel"
    }

    private val _mainLoadingState = MutableLiveData(true)
    val mainLoadingState: MutableLiveData<Boolean> = _mainLoadingState

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

    private val _userVehiclesState = MutableStateFlow<UiState<List<Vehicle>>>(UiState.Loading)
    val userVehiclesState: StateFlow<UiState<List<Vehicle>>> = _userVehiclesState.asStateFlow()

    // To keep track of orders for which notifications have been shown/updated
    // Key: OrderID, Value: LastNotifiedStatus
    private val notifiedOrderStatusMap = mutableMapOf<String, OrderStatus>()
    private var justOpenedFromNotificationForOrderId: String? = null

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

    init {
        Log.d(TAG, "current firebaseAuth: $auth")
    }

    fun observeDataByRole() {
        Log.d(TAG, "current role: ${userRepository.getCurrentUserRecord()?.role}")
        if (userRepository.getCurrentUserRecord()?.role == UserRole.CUSTOMER) {
            observeUserOrderServices()
            observeUserVehicles()
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
                        is UiState.Error -> UiState.Error(partnerOrderServicesUiState.exception)
                        is UiState.Success -> {
                            val orderService =
                                partnerOrderServicesUiState.data.find { it.id == orderId }
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
                        // _partnerOrderServiceState.value = UiState.Loading // Or an error specific to cancellation
                        throw e // Re-throw to propagate cancellation
                    }
                    Log.e(TAG, "Error in OrderService by ID flow collection", e)
                    _partnerOrderServiceState.value = UiState.Error(e)
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
        val userId = auth.currentUser?.uid
        if (userId == null) {
            performSignOut() // If no user, just sign out
            return
        }

        // Get the current FCM token
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                Log.d(TAG, "Removing FCM token: $token for user: $userId")
                userRepository.removeFcmToken(token, {
                    performSignOut()
                }, {
                    performSignOut() // If error, just sign out
                })
            } else {
                Log.w(
                    TAG,
                    "Could not get FCM token for removal, signing out anyway.",
                    task.exception
                )
                performSignOut()
            }
        }
    }

    private fun performSignOut() {
        viewModelScope.launch {
            _externalSignOutSignal.emit(Unit)
            _signOutEvent.emit(Unit)  // Signal to UI
        }
        auth.signOut()  // Firebase sign-out (no context needed)
    }

    fun stopObserve() {
        viewModelScope.launch {
            _stopObserveSignal.emit(Unit)
        }
    }

    fun getCurrentUser() = userRepository.getCurrentUserRecord()

    fun setMainLoadingState(isLoading: Boolean) {
        _mainLoadingState.value = isLoading
    }
}