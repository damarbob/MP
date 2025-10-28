package id.monpres.app.ui.auth.login

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth

class LoginViewModel : ViewModel() {
    companion object {
        private val TAG = LoginViewModel::class.simpleName
    }

    private val _authResult = MutableLiveData<Result<FirebaseUser>?>()
    val authResult: LiveData<Result<FirebaseUser>?> get() = _authResult

    private val _progressVisibility = MutableLiveData(false)
    val progressVisibility: LiveData<Boolean> get() = _progressVisibility

    private val _loginFormVisibilityState = MutableLiveData(false)
    val loginFormVisibilityState: LiveData<Boolean> get() = _loginFormVisibilityState

    fun loginWithEmailPassword(
        email: String,
        password: String,
    ) {
        _progressVisibility.value = true // Show loading indicator

        val auth = Firebase.auth

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = task.result?.user
                    Log.d(TAG, "Sign in successful")
                    Log.d(TAG, "User: $user")

                    _authResult.value =
                        Result.success(user!!) // Return user
                    _progressVisibility.value = false
                } else {
                    Log.d(TAG, "Sign in failed")
                    // If sign up fails, display a message to the user.
                    _authResult.value =
                        Result.failure(task.exception ?: Exception("Sign in failed"))
                    _progressVisibility.value = false
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
                    val errorMessage = task.exception?.localizedMessage ?: "Failed to send reset password email."
                    onResult(false, errorMessage)
                }
            }
    }

    fun toggleFormLayoutState() {
        _loginFormVisibilityState.value = !_loginFormVisibilityState.value!!
    }
}