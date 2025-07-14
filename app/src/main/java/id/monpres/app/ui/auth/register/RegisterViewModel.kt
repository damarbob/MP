package id.monpres.app.ui.auth.register

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
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
        email: String,
        password: String,
    ) {
        _progressVisibility.value = true // Show loading indicator

        val auth = Firebase.auth

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = task.result?.user
                    Log.d(TAG, "Sign up successful")
                    Log.d(TAG, "User: $user")

                    _authResult.value =
                        Result.success(user!!) // Return user
                    _progressVisibility.value = false
                } else {
                    Log.d(TAG, "Sign up failed")
                    // If sign up fails, display a message to the user.
                    _authResult.value =
                        Result.failure(task.exception ?: Exception("Sign up failed"))
                    _progressVisibility.value = false
                }
            }
    }
}