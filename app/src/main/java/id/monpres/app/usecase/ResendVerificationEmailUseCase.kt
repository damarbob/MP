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
    suspend operator fun invoke() {
        val user = auth.currentUser ?: throw Exception("User not authenticated")
        user.sendEmailVerification().await() // Throws exception on Firebase failure
    }
}