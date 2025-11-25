package id.monpres.app.usecase

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.qualifiers.ApplicationContext
import id.monpres.app.SessionManager
import id.monpres.app.repository.OrderServiceRepository
import id.monpres.app.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
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
    private val application: Application,
    @param:ApplicationContext private val context: Context
) {
    companion object {
        private val TAG = SignOutUseCase::class.simpleName
    }

    suspend operator fun invoke(additionalAction: () -> Unit = {}) {
        withContext(Dispatchers.IO) {
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

            // Clear the local record
            userRepository.clearRecord()
            orderServiceRepository.clearRecord()

            additionalAction()
            // Firebase sign-out
            auth.signOut()


            try {
                val credentialManager = CredentialManager.create(context)
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing credential state", e)
                // We still want to sign out, so we just log this error
            }

            // Perform the final sign-out actions
            Log.d(TAG, "Triggering session manager")
            sessionManager.triggerSignOut() // For other collectors to stop
        }
    }
}