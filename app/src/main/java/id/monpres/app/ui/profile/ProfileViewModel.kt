package id.monpres.app.ui.profile

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.ActionCodeSettings
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.auth.auth

class ProfileViewModel : ViewModel() {

    protected val _updateProfileResult = MutableLiveData<Result<Boolean>>(null)
    val updateProfileResult: LiveData<Result<Boolean>> get() = _updateProfileResult

    fun updateProfile(fullName: String, emailAddress: String) {
        val user = Firebase.auth.currentUser
        if (user == null) {
            return
        }

        // If the email address is the same, update the display name
        if (user.email == emailAddress) {
            user.updateProfile(
                UserProfileChangeRequest.Builder()
                    .setDisplayName(fullName)
                    .build()
            ).addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    // Handle success
                    _updateProfileResult.postValue(Result.success(true))
                } else {
                    // Handle error
                    _updateProfileResult.postValue(
                        Result.failure(
                            task.exception ?: Exception("Unknown error")
                        )
                    )
                }
            }
            return
        }

        // If the email address is different, verify the current email address and update it
        user.verifyBeforeUpdateEmail(
            emailAddress
        ).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                user.updateEmail(emailAddress).addOnCompleteListener { task1 ->
                    if (task1.isSuccessful) {
                        user.updateProfile(
                            UserProfileChangeRequest.Builder()
                                .setDisplayName(fullName)
                                .build()
                        ).addOnCompleteListener { task2 ->
                            if (task2.isSuccessful) {
                                // Handle success
                                _updateProfileResult.postValue(Result.success(true))
                            } else {
                                // Handle error
                                _updateProfileResult.postValue(
                                    Result.failure(
                                        task2.exception ?: Exception("Unknown error")
                                    )
                                )
                            }
                        }
                    } else {
                        // Handle error
                        _updateProfileResult.postValue(
                            Result.failure(
                                task1.exception ?: Exception("Unknown error")
                            )
                        )
                    }
                }
            } else {
                // Handle error
                _updateProfileResult.postValue(
                    Result.failure(
                        task.exception ?: Exception("Unknown error")
                    )
                )
            }
        }
    }
}