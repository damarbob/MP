package id.monpres.app.usecase

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * A suspend function UseCase to check the user's email verification status.
 * This forces a reload of the user's profile from Firebase.
 */
class CheckEmailVerificationUseCase @Inject constructor(
    private val auth: FirebaseAuth
) {
    suspend operator fun invoke(): Boolean {
        try {
            auth.currentUser?.reload()?.await()
            return auth.currentUser?.isEmailVerified ?: false
        } catch (e: Exception) {
            // If reload fails (e.g., no network), return the last known status
            return auth.currentUser?.isEmailVerified ?: false
        }
    }
}
