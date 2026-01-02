package id.monpres.app.usecase

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * A suspend function UseCase to resend the verification email.
 * Throws an exception if the user is null or the send fails.
 */
class ResendVerificationEmailUseCase @Inject constructor(
    private val auth: FirebaseAuth
) {
    /**
     * Sends a verification email to the currently authenticated user.
     *
     * @throws Exception if user is not authenticated or Firebase send operation fails
     */
    suspend operator fun invoke() {
        val user = auth.currentUser ?: throw Exception("User not authenticated")
        user.sendEmailVerification().await() // Throws exception on Firebase failure
    }
}
