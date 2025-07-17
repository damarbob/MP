package id.monpres.app.usecase

import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser

class ResendVerificationEmailUseCase {
    operator fun invoke(
        onSuccess: (Boolean) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val user: FirebaseUser? = FirebaseAuth.getInstance().currentUser
        user?.sendEmailVerification()?.addOnCompleteListener { task: Task<Void?> ->
            if (task.isSuccessful) {
                onSuccess(true)
            } else {
                val errorMessage = task.exception?.localizedMessage ?: "Failed to send the verification email."
                onFailure(errorMessage)
            }
        }
    }
}