package id.monpres.app

import android.app.Application
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.lifecycle.HiltViewModel
import id.monpres.app.enums.UserRole
import id.monpres.app.enums.UserVerificationStatus
import id.monpres.app.model.MontirPresisiUser
import id.monpres.app.repository.OrderServiceRepository
import id.monpres.app.repository.UserIdentityRepository
import id.monpres.app.repository.UserRepository
import id.monpres.app.state.ConnectionState
import id.monpres.app.state.NavigationGraphState
import id.monpres.app.state.NavigationGraphState.Admin
import id.monpres.app.state.NavigationGraphState.Customer
import id.monpres.app.state.NavigationGraphState.Partner
import id.monpres.app.state.UserEligibilityState
import id.monpres.app.usecase.CheckEmailVerificationUseCase
import id.monpres.app.usecase.GetOrCreateUserIdentityUseCase
import id.monpres.app.usecase.GetOrCreateUserUseCase
import id.monpres.app.usecase.GetUserVerificationStatusUseCase
import id.monpres.app.usecase.ResendVerificationEmailUseCase
import id.monpres.app.utils.NetworkConnectivityObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val userRepository: UserRepository,
    private val sessionManager: SessionManager,
    private val getOrCreateUserUseCase: GetOrCreateUserUseCase,
    private val getOrCreateUserIdentityUseCase: GetOrCreateUserIdentityUseCase,
    private val userIdentityRepository: UserIdentityRepository,
    private val orderServiceRepository: OrderServiceRepository,
    appPreferences: AppPreferences,
    private val networkConnectivityObserver: NetworkConnectivityObserver,
    private val getUserVerificationStatusUseCase: GetUserVerificationStatusUseCase,
    private val application: Application
) : ViewModel() {
    companion object {
        private val TAG = MainViewModel::class.simpleName
    }

    // --- STATE FLOWS FOR THE UI ---
    private val _navigationGraphState =
        MutableStateFlow<NavigationGraphState>(NavigationGraphState.Loading)
    val navigationGraphState: StateFlow<NavigationGraphState> = _navigationGraphState.asStateFlow()

    private val _userEligibilityState =
        MutableSharedFlow<UserEligibilityState>()
    val userEligibilityState: SharedFlow<UserEligibilityState> =
        _userEligibilityState.asSharedFlow()

    // --- FLOW FOR ACCOUNT VERIFICATION ---
    private val _userVerificationStatus =
        MutableStateFlow<UserVerificationStatus?>(null)
    val userVerificationStatus: StateFlow<UserVerificationStatus?> =
        _userVerificationStatus.asStateFlow()

    private val _mainLoadingState = MutableStateFlow(true)
    val mainLoadingState = _mainLoadingState.asStateFlow()

    private val _signOutEvent = MutableSharedFlow<Unit>()
    val signOutEvent: SharedFlow<Unit> = _signOutEvent.asSharedFlow()

    private val _isUserVerified = MutableStateFlow<Boolean?>(null)
    val isUserVerified: StateFlow<Boolean?> = _isUserVerified.asStateFlow()

    private val _isResendEmailVerificationSuccess = MutableStateFlow<Boolean?>(null)
    val isResendEmailVerificationSuccess: StateFlow<Boolean?> =
        _isResendEmailVerificationSuccess.asStateFlow()

    private val _isConnected: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _errorEvent =
        MutableSharedFlow<Exception?>() // No replay needed for one-time events
    val errorEvent: SharedFlow<Exception?> = _errorEvent.asSharedFlow()

    private val checkEmailVerificationUseCase = CheckEmailVerificationUseCase()
    private val resendVerificationEmailUseCase = ResendVerificationEmailUseCase()

    init {
        Log.d(TAG, "MainViewModel init")
        viewModelScope.launch(Dispatchers.Default) {
            networkConnectivityObserver.networkStatus.collect { status ->
                Log.d(TAG, "Network status: ${status.state}")

                when (status.state) {
                    ConnectionState.Connected -> {
                        _isConnected.value = true

                        if (_isUserVerified.value != true) {
                            runAuthenticationCheck()
                        }

                        if (userRepository.userRecord.value == null) {
                            // Launch a coroutine that waits for verification before initializing the session.
                            waitForVerificationAndInitialize()
                        } else {
                            determineNavGraphAndUserEligibility(
                                userRepository.userRecord.filterNotNull().first()
                            )
                        }

                        _mainLoadingState.value = false
                    }

                    ConnectionState.Disconnected -> {
                        _isConnected.value = false
                        _mainLoadingState.value = false
                    }
                }

            }
        }
    }

    fun runAuthenticationCheck() {
        _mainLoadingState.value = true
        // If there's no logged-in user, trigger sign-out immediately.
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.d(TAG, "No current user, signing out.")
            signOut()
            return
        }

        Log.d(TAG, "Current user: ${currentUser.displayName}. Checking email verification.")
        viewModelScope.launch(Dispatchers.IO) {
            Log.d(TAG, "Checking email verification...")
            checkEmailVerificationUseCase(
                { isVerified ->
                    Log.d(TAG, "Email verification check result: $isVerified")
                    // This will trigger the flow below if isVerified is true.
                    _isUserVerified.value = isVerified
                },
                { exception ->
                    Log.e(TAG, "Error checking email verification: $exception")
                    _mainLoadingState.value = false
                    // Try to emit the error. tryEmit is non-suspending.
                    viewModelScope.launch {
                        _errorEvent.emit(exception)
                    }
                }
            )
        }
    }

    private fun waitForVerificationAndInitialize() {
        viewModelScope.launch(Dispatchers.IO) {
            _mainLoadingState.value = true

            // This coroutine will suspend here until _isUserVerified becomes non-null.
            val isVerified = _isUserVerified.filterNotNull().first()
            Log.d(TAG, "Email verification result: $isVerified")

            if (isVerified) {
                // Start collecting the admin verification status as soon as email is verified
                launch {
                    getUserVerificationStatusUseCase().collect { status ->
                        _userVerificationStatus.value = status
                    }
                }

                // Once verified, proceed with session initialization.
                initializeSession()
            } else {
                // If the user is not verified, stop the main loading spinner.
                // The UI will be showing the "Please Verify" screen.
                _mainLoadingState.value = false
            }
        }
    }

    private suspend fun initializeSession() {
        // Get user data and identity
        getUserData()

        // Wait for the user record to be populated in the repository
        val user = userRepository.userRecord.filterNotNull().first()
        Log.d(TAG, "User record fetched: ${user.userId}")

        determineNavGraphAndUserEligibility(user)
    }

    private suspend fun determineNavGraphAndUserEligibility(user: MontirPresisiUser) {
        // Determine Navigation and Eligibility based on the fetched user
        determineNavigationGraph(user)
        checkUserEligibility(user)
        _mainLoadingState.value = false
    }

    fun resendVerificationEmail() {
        resendVerificationEmailUseCase(
            { isSuccessful ->
                _isResendEmailVerificationSuccess.value = isSuccessful
            },
            { exception ->
                viewModelScope.launch {
                    _errorEvent.emit(exception)
                }
            }
        )
    }

    private suspend fun getUserData() {
        /* Get user profile */
        getOrCreateUserUseCase(UserRole.CUSTOMER).onSuccess { user ->
            Log.d(TAG, "User: ${user.userId}")
            user.userId?.let {
                Log.d(
                    TAG,
                    "UserRepository record: ${userRepository.getRecordByUserId(it)}"
                )
            }
        }.onFailure { exception ->
            when (exception) {
                is GetOrCreateUserUseCase.UserNotAuthenticatedException -> {
                    // Handle unauthenticated user
                    Log.e(TAG, "User not authenticated")
                }

                is GetOrCreateUserUseCase.UserDataParseException,
                is GetOrCreateUserUseCase.FirestoreOperationException -> {
                    // Handle specific exceptions
                    Log.e(TAG, "Error: ${exception.message}")
                }

                else -> {
                    // Handle generic errors
                    Log.e(TAG, "Unexpected error: ${exception.message}")
                }
            }
        }

        /* Get user identity */
        getOrCreateUserIdentityUseCase().onSuccess { userIdentity ->
            Log.d(TAG, "User: ${userIdentity.userId}")
            userIdentity.userId?.let {
                Log.d(
                    TAG,
                    "UserIdentityRepository record: ${
                        userIdentityRepository.getRecordByUserId(
                            it
                        )
                    }"
                )
            }
        }.onFailure { exception ->
            // Handle generic errors
            Log.e(TAG, "Unexpected error: ${exception.message}")
        }
    }

    private fun determineNavigationGraph(user: MontirPresisiUser) {
        when (user.role) {
            UserRole.ADMIN -> {
                _navigationGraphState.value = Admin(R.navigation.nav_main)
            }

            UserRole.PARTNER -> {
                _navigationGraphState.value = Partner(R.navigation.nav_main)
            }

            UserRole.CUSTOMER -> {
                _navigationGraphState.value = Customer(R.navigation.nav_main)
            }

            null -> _navigationGraphState.value = Customer(R.navigation.nav_main)
        }
    }

    private suspend fun checkUserEligibility(user: MontirPresisiUser) {
        val isPartnerMissingLocation = user.role == UserRole.PARTNER &&
                (user.locationLat.isNullOrBlank() || user.locationLng.isNullOrBlank())

        val isCustomerMissingPhone =
            user.role == UserRole.CUSTOMER && user.phoneNumber.isNullOrBlank()

        val isCustomerMissingSocialMedia =
            user.role == UserRole.CUSTOMER &&
                    (user.instagramId.isNullOrBlank() && user.facebookId.isNullOrBlank())

        _userEligibilityState.emit(
            when {
                isPartnerMissingLocation -> UserEligibilityState.PartnerMissingLocation
                isCustomerMissingPhone -> UserEligibilityState.CustomerMissingPhoneNumber
                isCustomerMissingSocialMedia -> UserEligibilityState.CustomerMissingSocialMedia
                else -> UserEligibilityState.Eligible
            }
        )
    }

    // You can also add a function to re-check eligibility when needed
    fun recheckEligibility() {
        val user = userRepository.getCurrentUserRecord() ?: return
        Log.d(TAG, "Rechecking eligibility for user: ${user.userId}")
        viewModelScope.launch {
            checkUserEligibility(user)
        }
    }

    fun signOut() {
        viewModelScope.launch {

            try {
                // Get FCM token and remove it from the repository and database
                Log.d(TAG, "Attempting to get FCM token for removal...")
                val token = FirebaseMessaging.getInstance().token.await()
                Log.d(TAG, "Got FCM token. Removing from repository...")
                userRepository.removeFcmToken(token)
                Log.d(TAG, "FCM token removed.")

            } catch (e: Exception) {
                Log.w(TAG, "Could not get/remove FCM token, signing out anyway.", e)
            }

            try {
                val credentialManager = CredentialManager.create(application)
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                _errorEvent.emit(e)
                Log.e(TAG, "Error clearing credential state", e)
            }

            // Clear the local record
            userRepository.clearRecord()
            orderServiceRepository.clearRecord()
            _isUserVerified.value = null

            // Perform the final sign-out actions ---
            Log.d(TAG, "Triggering session manager and signing out from Firebase...")
            sessionManager.triggerSignOut() // For other collectors to stop
            auth.signOut()                 // Firebase sign-out

            // Signal to the UI that all cleanup is complete ---
            Log.d(TAG, "Sign-out process complete. Emitting event to UI.")
            _signOutEvent.emit(Unit)       // This tells MainActivity it's safe to navigate
        }
    }

    fun getCurrentUser() = userRepository.getCurrentUserRecord()
}