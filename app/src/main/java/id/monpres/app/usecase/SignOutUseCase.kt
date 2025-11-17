package id.monpres.app.usecase

import android.app.Application
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import id.monpres.app.SessionManager
import id.monpres.app.repository.OrderServiceRepository
import id.monpres.app.repository.UserRepository
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * A suspend function UseCase to handle all sign-out logic.
 * This encapsulates clearing tokens, repositories, and signing out.
 */
class SignOutUseCase @Inject constructor(
    private val auth: FirebaseAuth,
    private val userRepository: UserRepository,
    private val orderServiceRepository: OrderServiceRepository,
    private val sessionManager: SessionManager,
    private val application: Application
) {
    companion object {
        private val TAG = SignOutUseCase::class.simpleName
    }

    suspend operator fun invoke() {
        try {
            // Get FCM token and remove it from the repository and database
            Log.d(TAG, "Attempting to get FCM token for removal...")
            val token = FirebaseMessaging.getInstance().token.await()
            Log.d(TAG, "Got FCM token. Removing from repository...")
            userRepository.removeFcmToken(token) // Assumes this suspend fun exists in UserRepository
            Log.d(TAG, "FCM token removed.")
        } catch (e: Exception) {
            Log.w(TAG, "Could not get/remove FCM token, signing out anyway.", e)
        }

        try {
            val credentialManager = CredentialManager.create(application)
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing credential state", e)
            // We still want to sign out, so we just log this error
        }

        // Clear the local record
        userRepository.clearRecord()
        orderServiceRepository.clearRecord()

        // Perform the final sign-out actions
        Log.d(TAG, "Triggering session manager and signing out from Firebase...")
        sessionManager.triggerSignOut() // For other collectors to stop
        auth.signOut()                 // Firebase sign-out
    }
}