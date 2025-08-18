package id.monpres.app.ui.auth.register

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.auth.auth

class RegisterViewModel : ViewModel() {
    companion object {
        private val TAG = RegisterViewModel::class.simpleName
    }

    private val _authResult = MutableLiveData<Result<FirebaseUser>?>()
    val authResult: LiveData<Result<FirebaseUser>?> get() = _authResult

    private val _progressVisibility = MutableLiveData(false)
    val progressVisibility: LiveData<Boolean> get() = _progressVisibility

    fun registerWithEmailPassword(
        fullName: String,
        email:    String,
        password: String,
    ) {
        // Show spinner
        _progressVisibility.value = true

        val auth = Firebase.auth
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { createTask ->
                if (!createTask.isSuccessful) {
                    // Sign-up failed
                    _authResult.value = Result.failure(
                        createTask.exception ?: Exception("Sign up failed")
                    )
                    _progressVisibility.value = false
                    return@addOnCompleteListener
                }

                // Account created — grab the FirebaseUser
                val user = createTask.result?.user
                if (user == null) {
                    _authResult.value = Result.failure(Exception("User is null after sign-up"))
                    _progressVisibility.value = false
                    return@addOnCompleteListener
                }

                // Build a profile update request
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(fullName)
                    .build()

                // Push the displayName to Firebase
                user.updateProfile(profileUpdates)
                    .addOnCompleteListener { updateTask ->
                        // hide spinner
                        _progressVisibility.value = false

                        if (updateTask.isSuccessful) {
                            // 5) Success! return the fully-populated user
                            _authResult.value = Result.success(user)
                        } else {
                            // 6) profile update failed—bubble up the error
                            _authResult.value = Result.failure(
                                updateTask.exception ?: Exception("Failed to update profile")
                            )
                        }
                    }
            }
    }
}