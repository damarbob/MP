package id.monpres.app

import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import id.monpres.app.enums.Language
import id.monpres.app.enums.UserRole
import id.monpres.app.enums.UserVerificationStatus
import id.monpres.app.model.MontirPresisiUser
import id.monpres.app.notification.OrderServiceNotification
import id.monpres.app.repository.AppPreferences
import id.monpres.app.repository.UserRepository
import id.monpres.app.state.ConnectionState
import id.monpres.app.state.NavigationGraphState
import id.monpres.app.usecase.CheckEmailVerificationUseCase
import id.monpres.app.usecase.GetOrCreateUserIdentityUseCase
import id.monpres.app.usecase.GetOrCreateUserUseCase
import id.monpres.app.usecase.GetUserVerificationStatusUseCase
import id.monpres.app.usecase.ResendVerificationEmailUseCase
import id.monpres.app.usecase.SignOutUseCase
import id.monpres.app.utils.NetworkConnectivityObserver
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

// --- STATE CLASSES FOR UI ---

/**
 * Represents all possible dialogs the MainActivity can show.
 * This centralizes dialog logic.
 */
sealed class DialogState {
    data object None : DialogState()
    data object EmailVerificationPending : DialogState()
    data object AdminVerificationPending : DialogState() // Replaces AdminVerification(String)
    data object AdminVerificationRejected : DialogState() // Replaces AdminVerification(String)
    data object PartnerMissingLocation : DialogState()
    data object CustomerMissingPhoneNumber : DialogState()
    data object CustomerMissingSocialMedia : DialogState()
}

/**
 * Represents one-time navigation events.
 */
sealed class NavigationEvent {
    data class ToServiceProcess(val orderId: String) : NavigationEvent()
    data object ToLogin : NavigationEvent()
}

/**
 * Represents one-time toast/snackbar messages.
 * TODO: Use data class with message string parameter and use application context.
 */
sealed class ToastEvent {
    data object VerificationEmailSent : ToastEvent() // Replaces Show(String)
}


@HiltViewModel
class MainViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val userRepository: UserRepository,
    private val networkConnectivityObserver: NetworkConnectivityObserver,
    // --- USE CASES (Assumed to be refactored as suspend functions) ---
    private val getOrCreateUserUseCase: GetOrCreateUserUseCase,
    private val getOrCreateUserIdentityUseCase: GetOrCreateUserIdentityUseCase,
    private val getUserVerificationStatusUseCase: GetUserVerificationStatusUseCase,
    private val checkEmailVerificationUseCase: CheckEmailVerificationUseCase,
    private val resendVerificationEmailUseCase: ResendVerificationEmailUseCase,
    private val signOutUseCase: SignOutUseCase,
    private val appPreferences: AppPreferences
) : ViewModel() {

    companion object {
        private val TAG = MainViewModel::class.simpleName
    }

    // --- SETTINGS ---
    val isDynamicColorApplied = appPreferences.isDynamicColorEnabled
    val themeMode = appPreferences.theme
    val language = appPreferences.language

    // --- STATE FLOWS ---
    private val _navigationGraphState =
        MutableStateFlow<NavigationGraphState>(NavigationGraphState.Loading)
    val navigationGraphState: StateFlow<NavigationGraphState> = _navigationGraphState.asStateFlow()

    private val _dialogState = MutableStateFlow<DialogState>(DialogState.None)
    val dialogState: StateFlow<DialogState> = _dialogState.asStateFlow()

    private val _userVerificationStatus =
        MutableStateFlow<UserVerificationStatus?>(null)
    val userVerificationStatus: StateFlow<UserVerificationStatus?> =
        _userVerificationStatus.asStateFlow()

    private val _mainLoadingState = MutableStateFlow(true)
    val mainLoadingState: StateFlow<Boolean> = _mainLoadingState.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    // --- EVENT FLOWS ---
    private val _navigationEvent = MutableSharedFlow<NavigationEvent>()
    val navigationEvent: SharedFlow<NavigationEvent> = _navigationEvent.asSharedFlow()

    private val _toastEvent = MutableSharedFlow<ToastEvent>()
    val toastEvent: SharedFlow<ToastEvent> = _toastEvent.asSharedFlow()

    private val _errorEvent = MutableSharedFlow<Exception>()
    val errorEvent: SharedFlow<Exception> = _errorEvent.asSharedFlow()

    private val _pendingIntent = MutableStateFlow<Intent?>(null)

    init {
        Log.d(TAG, "MainViewModel init")
        observeNetwork()
        observePendingIntent()
        observeAdminVerification()
    }

    private fun observeNetwork() {
        viewModelScope.launch {
            networkConnectivityObserver.networkStatus.collect { status ->
                Log.d(TAG, "Network status: ${status.state}")
                when (status.state) {
                    ConnectionState.Connected -> {
                        _isConnected.value = true
                        val userRecord = userRepository.getCurrentUserRecord()
                        // Only run auth check if we aren't already verified and loaded
                        if (userRecord == null) {
                            runAuthenticationCheck()
                        } else {
                            determineNavigationGraph(userRecord)
                        }
                    }

                    ConnectionState.Disconnected -> {
                        _isConnected.value = false
                        _mainLoadingState.value = false
                        val userRecord = userRepository.userRecord.value
                        if (userRecord != null) {
                            determineNavigationGraph(userRecord)
                        }
                    }
                }
            }
        }
    }

    /**
     * Processes notification intents only when the app is not loading.
     */
    private fun observePendingIntent() {
        viewModelScope.launch {
            mainLoadingState.combine(_pendingIntent) { isLoading, intent ->
                Pair(isLoading, intent)
            }
                .filterNotNull()
                .collect { (isLoading, intent) ->
                    if (!isLoading && intent != null) {
                        val orderId = intent.getStringExtra(OrderServiceNotification.ORDER_ID_KEY)
                        if (orderId != null) {
                            Log.d(TAG, "Processing pending intent for order: $orderId")
                            _navigationEvent.emit(NavigationEvent.ToServiceProcess(orderId))
                            _pendingIntent.value = null // Consume the intent
                        }
                    }
                }
        }
    }

    /**
     * Observes admin verification status and updates the dialog state.
     */
    private fun observeAdminVerification() {
        viewModelScope.launch {
            _userVerificationStatus.filterNotNull().collect { status ->
                val userRole = userRepository.getCurrentUserRecord()?.role

                // This collector will only run after email is verified
                // (triggered by initializeSession).

                when (status) {
                    UserVerificationStatus.VERIFIED -> {
                        // User is admin-verified. NOW check eligibility.
                        checkUserEligibility(userRepository.getCurrentUserRecord())
                    }

                    UserVerificationStatus.PENDING -> {
                        if (userRole == UserRole.CUSTOMER) {
                            Log.d(TAG, "User verification is PENDING for CUSTOMER, showing dialog.")
                            _dialogState.value =
                                DialogState.AdminVerificationPending // Use the new state
                            _mainLoadingState.value = false // We are "loaded" but blocked
                        } else {
                            // Partners/Admins are not blocked by PENDING, check eligibility
                            checkUserEligibility(userRepository.getCurrentUserRecord())
                        }
                    }

                    UserVerificationStatus.REJECTED -> {
                        if (userRole == UserRole.CUSTOMER) {
                            _dialogState.value =
                                DialogState.AdminVerificationRejected // Use the new state
                            _mainLoadingState.value = false // We are "loaded" but blocked
                        } else {
                            // Partners/Admins are not blocked by REJECTED, check eligibility
                            checkUserEligibility(userRepository.getCurrentUserRecord())
                        }
                    }
                }
            }
        }
    }

    /**
     * Called by Activity to buffer an intent for processing.
     */
    fun setPendingIntent(intent: Intent?) {
        if (intent?.hasExtra(OrderServiceNotification.ORDER_ID_KEY) == true) {
            _pendingIntent.value = intent
        }
    }

    /**
     * Checks if the current Firebase user is email-verified.
     */
    fun runAuthenticationCheck() {
        _mainLoadingState.value = true
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.d(TAG, "No current user, signing out.")
            signOut() // This will emit a navigation event
            return
        }

        Log.d(TAG, "Current user: ${currentUser.displayName}. Checking email verification.")
        viewModelScope.launch {
            try {
                // Assumes checkEmailVerificationUseCase is a suspend function
                val isVerified = checkEmailVerificationUseCase()
                Log.d(TAG, "Email verification check result: $isVerified")

                if (isVerified) {
                    initializeSession()
                } else {
                    _dialogState.value = DialogState.EmailVerificationPending
                    _mainLoadingState.value = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking email verification", e)
                _errorEvent.emit(e)
                _mainLoadingState.value = false
            }
        }
    }

    /**
     * Fetches user data, determines navigation, and starts observing admin verification.
     */
    private suspend fun initializeSession() {
        try {
            // Get user data and identity
            val user = getUserData()
            if (user != null) {
                // Determine Navigation and Eligibility
                determineNavigationGraph(user)

                // Start collecting the admin verification status
                getUserVerificationStatusUseCase().collect { status ->
                    _userVerificationStatus.value = status
                }
            } else {
                // Failed to get user data
                _mainLoadingState.value = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing session", e)
            _errorEvent.emit(e)
            _mainLoadingState.value = false
        }
    }

    /**
     * Fetches and/or creates user data from repositories.
     * Assumes UseCases are suspend functions returning Result<T>
     */
    private suspend fun getUserData(): MontirPresisiUser? {
        try {
            /* Get user profile */
            val user = getOrCreateUserUseCase(UserRole.CUSTOMER).getOrThrow()
            Log.d(TAG, "User: ${user.userId}")

            /* Get user identity */
            getOrCreateUserIdentityUseCase().getOrThrow()
            Log.d(TAG, "User Identity: ${user.userId}")

            return user

        } catch (exception: Exception) {
            Log.e(TAG, "Error getting user data", exception)
            _errorEvent.emit(exception)
            return null
        }
    }

    private fun determineNavigationGraph(user: MontirPresisiUser) {
        when (user.role) {
            UserRole.ADMIN -> _navigationGraphState.value =
                NavigationGraphState.Admin(R.navigation.nav_main)

            UserRole.PARTNER -> _navigationGraphState.value =
                NavigationGraphState.Partner(R.navigation.nav_main)

            UserRole.CUSTOMER -> _navigationGraphState.value =
                NavigationGraphState.Customer(R.navigation.nav_main)

            null -> _navigationGraphState.value =
                NavigationGraphState.Customer(R.navigation.nav_main)
        }
    }

    private fun checkUserEligibility(user: MontirPresisiUser?) {
        if (user == null) {
            _mainLoadingState.value = false // Handle null user case
            return
        }

        val isPartnerMissingLocation = user.role == UserRole.PARTNER &&
                (user.locationLat.isNullOrBlank() || user.locationLng.isNullOrBlank())

        val isCustomerMissingPhone =
            user.role == UserRole.CUSTOMER && user.phoneNumber.isNullOrBlank()

        val isCustomerMissingSocialMedia =
            user.role == UserRole.CUSTOMER &&
                    (user.instagramId.isNullOrBlank() && user.facebookId.isNullOrBlank())

        val newDialogState = when {
            isPartnerMissingLocation -> DialogState.PartnerMissingLocation
            isCustomerMissingPhone -> DialogState.CustomerMissingPhoneNumber
            isCustomerMissingSocialMedia -> DialogState.CustomerMissingSocialMedia
            else -> DialogState.None // Eligible
        }

        _dialogState.value = newDialogState

        // If we are eligible (None), we are done loading.
        // If we are *not* eligible, we are also "done" loading, but blocked by a dialog.
        _mainLoadingState.value = false
    }

    fun recheckEligibility() {
        val user = userRepository.getCurrentUserRecord() ?: return
        Log.d(TAG, "Rechecking eligibility for user: ${user.userId}")
        checkUserEligibility(user)
    }

    fun refreshAdminVerificationStatus() {
        viewModelScope.launch {
            getUserVerificationStatusUseCase().collect { status ->
                _userVerificationStatus.value = status
            }
        }
    }

    /**
     * Resends the verification email.
     * Assumes resendVerificationEmailUseCase is a suspend function.
     */
    fun resendVerificationEmail() {
        viewModelScope.launch {
            try {
                resendVerificationEmailUseCase()
                _toastEvent.emit(ToastEvent.VerificationEmailSent) // Use the new event
            } catch (e: Exception) {
                Log.e(TAG, "Error resending verification email", e)
                _errorEvent.emit(e)
            }
        }
    }

    /**
     * Signs the user out using the SignOutUseCase.
     */
    fun signOut() {
        viewModelScope.launch {
            try {
                // Assumes signOutUseCase is a suspend function
                signOutUseCase()
                Log.d(TAG, "Sign-out process complete. Emitting event to UI.")
                _navigationEvent.emit(NavigationEvent.ToLogin)
            } catch (e: Exception) {
                Log.e(TAG, "Sign out failed", e)
                _errorEvent.emit(e)
            }
        }
    }

    fun getCurrentUser() = userRepository.getCurrentUserRecord()

    fun syncLanguageWithSystem(systemLangTag: String) {
        viewModelScope.launch {
            // Get the last known value from our DataStore
            val lastKnownLang = appPreferences.language.first()
            val systemLanguage = Language.fromCode(systemLangTag) ?: Language.SYSTEM

            if (lastKnownLang != systemLanguage.name) {
                Log.i(
                    TAG,
                    "Language mismatch detected! System: '$systemLangTag', DataStore: '$lastKnownLang'. Syncing..."
                )
                // The value is different, so update our DataStore to match the system.
                appPreferences.setLanguage(systemLanguage)
            } else {
                // The values match, no action needed.
                Log.d(TAG, "Language is already in sync with system setting.")
            }
        }
    }
}