package id.monpres.app

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.lifecycle.HiltViewModel
import id.monpres.app.enums.UserRole
import id.monpres.app.model.MontirPresisiUser
import id.monpres.app.repository.UserIdentityRepository
import id.monpres.app.repository.UserRepository
import id.monpres.app.state.NavigationGraphState
import id.monpres.app.state.UserEligibilityState
import id.monpres.app.usecase.CheckEmailVerificationUseCase
import id.monpres.app.usecase.GetOrCreateUserIdentityUseCase
import id.monpres.app.usecase.GetOrCreateUserUseCase
import id.monpres.app.usecase.ResendVerificationEmailUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val userRepository: UserRepository,
    private val sessionManager: SessionManager,
    private val getOrCreateUserUseCase: GetOrCreateUserUseCase,
    private val getOrCreateUserIdentityUseCase: GetOrCreateUserIdentityUseCase,
    private val userIdentityRepository: UserIdentityRepository
) : ViewModel() {
    companion object {
        private val TAG = MainViewModel::class.simpleName
    }

    // --- STATE FLOWS FOR THE UI ---
    private val _navigationGraphState =
        MutableStateFlow<NavigationGraphState>(NavigationGraphState.Loading)
    val navigationGraphState: StateFlow<NavigationGraphState> = _navigationGraphState.asStateFlow()

    private val _userEligibilityState =
        MutableStateFlow<UserEligibilityState>(UserEligibilityState.Eligible)
    val userEligibilityState: StateFlow<UserEligibilityState> = _userEligibilityState.asStateFlow()


    private val _mainLoadingState = MutableLiveData(true)
    val mainLoadingState: MutableLiveData<Boolean> = _mainLoadingState

    private val _signOutEvent = MutableSharedFlow<Unit>()
    val signOutEvent: SharedFlow<Unit> = _signOutEvent.asSharedFlow()

    private val _isUserVerified = MutableStateFlow<Boolean?>(null)
    val isUserVerified: StateFlow<Boolean?> = _isUserVerified.asStateFlow()

    private val _isResendEmailVerificationSuccess = MutableStateFlow<Boolean?>(null)
    val isResendEmailVerificationSuccess: StateFlow<Boolean?> = _isResendEmailVerificationSuccess.asStateFlow()

    // --- NEW SHARED FLOW FOR UI EVENTS ---
    private val _errorEvent = MutableSharedFlow<String>() // No replay needed for one-time events
    val errorEvent: SharedFlow<String> = _errorEvent.asSharedFlow()

    private val checkEmailVerificationUseCase = CheckEmailVerificationUseCase()
    private val resendVerificationEmailUseCase = ResendVerificationEmailUseCase()

    init {
        Log.d(TAG, "MainViewModel init")
        // 1. Start the authentication check as soon as the ViewModel is created.
        runAuthenticationCheck()

        // 2. Launch a coroutine that waits for verification before initializing the session.
        waitForVerificationAndInitialize()
    }

    fun runAuthenticationCheck() {
        // If there's no logged-in user, trigger sign-out immediately.
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.d(TAG, "No current user, signing out.")
            signOut()
            return
        }

        Log.d(TAG, "Current user: ${currentUser.displayName}. Checking email verification.")
        checkEmailVerificationUseCase(
            { isVerified ->
                // This will trigger the flow below if isVerified is true.
                _isUserVerified.value = isVerified
            },
            { errorMessage ->
                // Try to emit the error. tryEmit is non-suspending.
                _errorEvent.tryEmit(errorMessage)
            }
        )
    }

    private fun waitForVerificationAndInitialize() {
        viewModelScope.launch {
            // This coroutine will suspend here until _isUserVerified becomes non-null.
            val isVerified = _isUserVerified.filterNotNull().first()

            if (isVerified) {
                // Once verified, proceed with session initialization.
                initializeSession()
            } else {
                // If the user is not verified, stop the main loading spinner.
                // The UI will be showing the "Please Verify" screen.
                _mainLoadingState.postValue(false)
            }
        }
    }

    private suspend fun initializeSession() {
        _mainLoadingState.postValue(true)

        // Get user data and identity
        getUserData()

        // Wait for the user record to be populated in the repository
        val user = userRepository.userRecord.filterNotNull().first()

        // Determine Navigation and Eligibility based on the fetched user
        determineNavigationGraph(user)
        checkUserEligibility(user)

        _mainLoadingState.postValue(false)
    }

    fun resendVerificationEmail() {
        resendVerificationEmailUseCase(
            { isSuccessful ->
                _isResendEmailVerificationSuccess.value = isSuccessful
            },
            { errorMessage ->
                _errorEvent.tryEmit(errorMessage)
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
        if (user.role == UserRole.PARTNER) {
            _navigationGraphState.value = NavigationGraphState.Partner(R.navigation.nav_main)
        } else if (user.role == UserRole.CUSTOMER) {
            _navigationGraphState.value = NavigationGraphState.Customer(R.navigation.nav_main)
        }
    }

    private fun checkUserEligibility(user: MontirPresisiUser) {
        val isPartnerMissingLocation = user.role == UserRole.PARTNER &&
                (user.locationLat.isNullOrBlank() || user.locationLng.isNullOrBlank())

        val isCustomerMissingPhone =
            user.role == UserRole.CUSTOMER && user.phoneNumber.isNullOrBlank()

        _userEligibilityState.value = when {
            isPartnerMissingLocation -> UserEligibilityState.PartnerMissingLocation
            isCustomerMissingPhone -> UserEligibilityState.CustomerMissingPhoneNumber
            else -> UserEligibilityState.Eligible
        }
    }

    // You can also add a function to re-check eligibility when needed
    fun recheckEligibility() {
        val user = userRepository.getCurrentUserRecord() ?: return
        checkUserEligibility(user)
    }

    fun signOut() {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            performSignOut() // If no user, just sign out
            return
        }

        // Clear the local record
        userRepository.clearRecord()

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
            sessionManager.triggerSignOut()
            _signOutEvent.emit(Unit)  // Signal to UI
        }
        auth.signOut()  // Firebase sign-out (no context needed)
    }

    fun getCurrentUser() = userRepository.getCurrentUserRecord()
}