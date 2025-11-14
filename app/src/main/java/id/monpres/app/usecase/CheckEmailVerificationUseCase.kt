package id.monpres.app.usecase

import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth

class CheckEmailVerificationUseCase {
    operator fun invoke(
        onEmailVerified: (Boolean) -> Unit,
        onFailure: (Exception?) -> Unit
    ) {
        val user: FirebaseUser? = Firebase.auth.currentUser
        user?.reload()?.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val isVerified = user.isEmailVerified
                onEmailVerified(isVerified)
            } else {
                onFailure(task.exception)
            }
        }
    }
}