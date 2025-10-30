package id.monpres.app

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.lifecycle.HiltViewModel
import id.monpres.app.repository.UserRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val userRepository: UserRepository,
    private val sessionManager: SessionManager
) : ViewModel() {
    companion object {
        private val TAG = MainViewModel::class.simpleName
    }

    private val _mainLoadingState = MutableLiveData(true)
    val mainLoadingState: MutableLiveData<Boolean> = _mainLoadingState

    private val _signOutEvent = MutableSharedFlow<Unit>()
    val signOutEvent: SharedFlow<Unit> = _signOutEvent.asSharedFlow()

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
            sessionManager.triggerSignOut()
            _signOutEvent.emit(Unit)  // Signal to UI
        }
        auth.signOut()  // Firebase sign-out (no context needed)
    }

    fun getCurrentUser() = userRepository.getCurrentUserRecord()

    fun setMainLoadingState(isLoading: Boolean) {
        _mainLoadingState.value = isLoading
    }
}