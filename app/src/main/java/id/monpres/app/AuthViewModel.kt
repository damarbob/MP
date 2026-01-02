package id.monpres.app

import android.app.Application
import android.os.CountDownTimer
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import id.monpres.app.enums.Language
import id.monpres.app.enums.UserRole
import id.monpres.app.enums.UserVerificationStatus
import id.monpres.app.model.MontirPresisiUser
import id.monpres.app.module.CoroutineModule
import id.monpres.app.repository.AppPreferences
import id.monpres.app.repository.UserRepository
import id.monpres.app.usecase.GetOrCreateUserUseCase
import id.monpres.app.usecase.GetUserVerificationStatusUseCase
import id.monpres.app.usecase.SignOutUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val application: Application,
    private val auth: FirebaseAuth,
    private val appPreferences: AppPreferences,
    private val getUserVerificationStatusUseCase: GetUserVerificationStatusUseCase,
    private val getOrCreateUserUseCase: GetOrCreateUserUseCase,
    private val userRepository: UserRepository,
    @param:CoroutineModule.ApplicationScope private val applicationScope: CoroutineScope,
    private val signOutUseCase: SignOutUseCase
) : ViewModel() {
    companion object {
        private val TAG = AuthViewModel::class.simpleName
    }

    // --- SETTINGS ---
    val isDynamicColorApplied = appPreferences.isDynamicColorEnabled
    val themeMode = appPreferences.theme
    val language = appPreferences.language

    // StateFlow for authentication state
    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    // StateFlow for UI states
    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState.asStateFlow()

    private val _registerState = MutableStateFlow<RegisterState>(RegisterState.Idle)
    val registerState: StateFlow<RegisterState> = _registerState.asStateFlow()

    private val _emailVerificationState =
        MutableStateFlow<EmailVerificationState>(EmailVerificationState.Idle)
    val emailVerificationState: StateFlow<EmailVerificationState> =
        _emailVerificationState.asStateFlow()

    private val _countdownState = MutableStateFlow<CountdownState>(CountdownState.Idle)
    val countdownState: StateFlow<CountdownState> = _countdownState.asStateFlow()

    private val _adminVerificationState =
        MutableStateFlow<AdminVerificationState>(AdminVerificationState.Idle)
    val adminVerificationState: StateFlow<AdminVerificationState> =
        _adminVerificationState.asStateFlow()

    private val _errorEvent = MutableSharedFlow<Exception>(extraBufferCapacity = 1)
    val errorEvent: SharedFlow<Exception> = _errorEvent.asSharedFlow()

    private val _monpresUser = MutableStateFlow<MontirPresisiUser?>(null)
    val monpresUser: StateFlow<MontirPresisiUser?> = _monpresUser.asStateFlow()

    private var countdownTimer: CountDownTimer? = null
    private var authStateListener: FirebaseAuth.AuthStateListener? = null

    private var adminVerificationJob: Job? = null
    private var checkUserJob: Job? = null

    // Sealed classes for state management
    sealed class AuthState {
        object Idle : AuthState()
        object Loading : AuthState()
        data class FullyVerified(val user: MontirPresisiUser) : AuthState()
        data class Unauthenticated(val reason: String? = null) : AuthState()
        data class EmailNotVerified(val user: FirebaseUser) : AuthState()
        data class AdminVerificationPending(val user: MontirPresisiUser? = null) : AuthState()
        data class AdminVerificationRejected(val user: MontirPresisiUser) : AuthState()
        data class Error(val exception: Exception) : AuthState()
    }

    sealed class LoginState {
        object Idle : LoginState()
        object Loading : LoginState()
        object Success : LoginState()
        data class Error(val message: String) : LoginState()
    }

    sealed class RegisterState {
        object Idle : RegisterState()
        object Loading : RegisterState()
        object Success : RegisterState()
        data class Error(val message: String) : RegisterState()
    }

    sealed class EmailVerificationState {
        object Idle : EmailVerificationState()
        object Sending : EmailVerificationState()
        object Sent : EmailVerificationState()
        data class Error(val message: String) : EmailVerificationState()
    }

    sealed class CountdownState {
        object Idle : CountdownState()
        data class Active(val remainingSeconds: Long) : CountdownState()
    }

    sealed class AdminVerificationState {
        object Idle : AdminVerificationState()
        object Pending : AdminVerificationState()
        object Verified : AdminVerificationState()
        object Rejected : AdminVerificationState()
    }

    init {
        checkCurrentUser() // initial check
        checkExistingCountdown()
        setupAuthStateListener()
    }

    /**
     * Initial check for current user (for when ViewModel is created)
     */
    private fun checkCurrentUser() {
        checkUserJob?.cancel()
        checkUserJob = viewModelScope.launch {
            userRepository.observeUserById()
                .onCompletion {
                    Log.d(TAG, "User record collection completed")
                }
                .collect { userRecord ->
                    Log.d(TAG, "User record: $userRecord")
                    _monpresUser.value = userRecord
                    ensureActive()
                }
        }
    }

    /**
     * Setup AuthStateListener for real-time authentication changes
     */
    private fun setupAuthStateListener() {
        authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            Log.d(TAG, "User is signed in or auth state changed")
            if (user != null) {
                // User is signed in - check email verification status
                checkUserVerificationStatus(user)
            } else {
                // User is signed out
                _authState.value = AuthState.Unauthenticated()
                adminVerificationJob?.cancel()
            }
        }

        // Register the listener
        auth.addAuthStateListener(authStateListener!!)
    }

    /**
     * Check user verification status with reload to get latest data
     */
    private fun checkUserVerificationStatus(user: FirebaseUser) {
        // Show loading state while checking
        _authState.value = AuthState.Loading

        user.reload().addOnCompleteListener { reloadTask ->
            if (reloadTask.isSuccessful) {
                Log.d(TAG, "User reload successful")
                val updatedUser = auth.currentUser // Get fresh user data after reload
                if (updatedUser != null) {
                    if (updatedUser.isEmailVerified) {
                        Log.d(TAG, "User email is verified")
                        checkAdminVerification()
                        // Clear countdown when verified
                        viewModelScope.launch {
                            appPreferences.clearCooldown()
                        }
                        countdownTimer?.cancel()
                    } else {
                        adminVerificationJob?.cancel()
                        _authState.value = AuthState.EmailNotVerified(updatedUser)
                    }
                }
            } else {
                Log.w(TAG, "User reload failed", reloadTask.exception)
                // Reload failed, use current user data
                if (user.isEmailVerified) {
                    Log.d(TAG, "User email is verified")
                    checkAdminVerification()
                } else {
                    adminVerificationJob?.cancel()
                    _authState.value = AuthState.EmailNotVerified(user)
                }
                Log.e(TAG, "Failed to reload user: ${reloadTask.exception}")
            }
        }
    }

    fun checkUser() {
        auth.currentUser?.let { checkUserVerificationStatus(it) }
    }

    private fun checkAdminVerification() {
        adminVerificationJob = viewModelScope.launch {
            try {
                val user = getOrCreateUserUseCase(UserRole.CUSTOMER).getOrThrow()
                checkCurrentUser()
                // Start collecting the admin verification status
                getUserVerificationStatusUseCase().collectLatest { status ->
                    Log.d(TAG, "User verification status: $status, user: $user")
                    when (status) {
                        UserVerificationStatus.VERIFIED -> {
                            _authState.value = AuthState.FullyVerified(user)
                            _adminVerificationState.value =
                                AdminVerificationState.Verified
                        }

                        UserVerificationStatus.PENDING -> {
                            if (user.role == UserRole.CUSTOMER) {
                                _authState.value =
                                    AuthState.AdminVerificationPending(user)
                                _adminVerificationState.value =
                                    AdminVerificationState.Pending
                            } else {
                                _authState.value = AuthState.FullyVerified(user)
                                _adminVerificationState.value =
                                    AdminVerificationState.Verified
                            }
                        }

                        UserVerificationStatus.REJECTED -> {
                            if (user.role == UserRole.CUSTOMER) {
                                _authState.value =
                                    AuthState.AdminVerificationRejected(user)
                                _adminVerificationState.value =
                                    AdminVerificationState.Rejected
                            } else {
                                _authState.value = AuthState.FullyVerified(user)
                                _adminVerificationState.value =
                                    AdminVerificationState.Verified
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _authState.value = AuthState.Error(e)
                _errorEvent.emit(e)
            }
        }
    }

    fun loginWithEmailPassword(email: String, password: String) {
        _loginState.value = LoginState.Loading

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    _loginState.value = LoginState.Success
                    // AuthStateListener will automatically handle the state update
                } else {
                    Log.w(TAG, "Login failed: ${task.exception?.message}: ${(task.exception as FirebaseAuthException).errorCode}")
                    val errorMessage = task.exception?.message ?: "Login failed"
                    _loginState.value = LoginState.Error(errorMessage)
                    _authState.value = AuthState.Error(task.exception ?: Exception(errorMessage))
                    _errorEvent.tryEmit(task.exception ?: Exception(errorMessage))
                }
            }
    }

    fun registerWithEmailPassword(fullName: String, email: String, password: String) {
        _registerState.value = RegisterState.Loading

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { createTask ->
                if (!createTask.isSuccessful) {
                    val errorMessage = createTask.exception?.message ?: "Registration failed"
                    _registerState.value = RegisterState.Error(errorMessage)
                    _authState.value =
                        AuthState.Error(createTask.exception ?: Exception(errorMessage))
                    _errorEvent.tryEmit(createTask.exception ?: Exception(errorMessage))
                    return@addOnCompleteListener
                }

                val user = createTask.result?.user
                if (user == null) {
                    _registerState.value = RegisterState.Error("User is null after registration")
                    _authState.value = AuthState.Error(createTask.exception ?: Exception())
                    _errorEvent.tryEmit(createTask.exception ?: Exception())
                    return@addOnCompleteListener
                }

                // Update profile with display name
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(fullName)
                    .build()

                user.updateProfile(profileUpdates)
                    .addOnCompleteListener { updateTask ->
                        if (updateTask.isSuccessful) {
                            _registerState.value = RegisterState.Success
                            sendVerificationEmail() // Auto-send verification email after registration
                            // AuthStateListener will handle the state update
                        } else {
                            val errorMessage =
                                updateTask.exception?.message ?: "Profile update failed"
                            _registerState.value = RegisterState.Error(errorMessage)
                        }
                    }
            }
    }

    fun sendPasswordResetEmail(
        email: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        val auth = FirebaseAuth.getInstance()

        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    // Success: call lambda with true and null message
                    onResult(true, null)
                } else {
                    // Failure: call lambda with false and error message
                    val errorMessage =
                        task.exception?.localizedMessage ?: "Failed to send reset password email."
                    _errorEvent.tryEmit(task.exception ?: Exception(errorMessage))
                    onResult(false, errorMessage)
                }
            }
    }

    fun sendVerificationEmail() {
        val user = auth.currentUser
        if (user != null && !user.isEmailVerified) {
            _emailVerificationState.value = EmailVerificationState.Sending

            user.sendEmailVerification()
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        _emailVerificationState.value = EmailVerificationState.Sent
                        viewModelScope.launch {
                            appPreferences.startCooldown()
                            startCountdown()
                        }
                    } else {
                        val errorMessage =
                            task.exception?.message ?: "Failed to send verification email"
                        _emailVerificationState.value = EmailVerificationState.Error(errorMessage)
                        _errorEvent.tryEmit(task.exception ?: Exception(errorMessage))
                    }
                }
        }
    }

    private fun checkExistingCountdown() {
        viewModelScope.launch {
            if (appPreferences.cooldownTime.first() > 0) {
                startCountdown()
            } else {
                _countdownState.value = CountdownState.Idle
            }
        }
    }

    private fun startCountdown() {
        viewModelScope.launch {
            val remainingTime = appPreferences.cooldownTime.first() - System.currentTimeMillis()

            countdownTimer?.cancel()
            countdownTimer = object : CountDownTimer(remainingTime, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    val secondsRemaining = millisUntilFinished / 1000
                    _countdownState.value = CountdownState.Active(secondsRemaining)
                }

                override fun onFinish() {
                    _countdownState.value = CountdownState.Idle
                    launch {
                        appPreferences.clearCooldown()
                    }
                }
            }.start()
        }
    }

    fun signInWithGoogle(idToken: String) {
        _authState.value = AuthState.Loading

        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    // AuthStateListener will handle the state update
                } else {
                    val errorMessage = task.exception?.message ?: "Google sign-in failed"
                    _authState.value = AuthState.Error(task.exception ?: Exception(errorMessage))
                }
            }
    }

    fun logout() {
        applicationScope.launch {
            appPreferences.clearCooldown()
            countdownTimer?.cancel()
            signOutUseCase {
                _authState.value = AuthState.Unauthenticated()
            }
        }
    }

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

    suspend fun checkInitialAdminVerificationStatus(
        onVerified: () -> Unit,
        onPendingOrRejected: () -> Unit
    ) {
        combine(getUserVerificationStatusUseCase().filterNotNull(), _monpresUser.filterNotNull()) { status, monpresUser ->
            Pair(status, monpresUser)
        }.collect { (status, user) ->
            when (status) {
                UserVerificationStatus.VERIFIED -> {
                    Log.d(
                        TAG, "User is verified : ${System.currentTimeMillis()}"
                    )
                    onVerified()
                }

                UserVerificationStatus.PENDING, UserVerificationStatus.REJECTED -> {
                    if (user.role == UserRole.CUSTOMER) {
                        onPendingOrRejected()
                    } else {
                        onVerified() // Other than customer, they are always verified
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        countdownTimer?.cancel()
        authStateListener?.let { auth.removeAuthStateListener(it) }
    }
}