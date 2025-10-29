package id.monpres.app.ui.profile

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.mapbox.geojson.Point
import dagger.hilt.android.lifecycle.HiltViewModel
import id.monpres.app.model.MontirPresisiUser
import id.monpres.app.repository.UserRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {
    private val _selectedPrimaryLocationPoint = MutableLiveData<Point?>(null)
    val selectedPrimaryLocationPoint: LiveData<Point?> get() = _selectedPrimaryLocationPoint
    private val _updateProfileResult = MutableLiveData<Result<Boolean>>(null)
    val updateProfileResult: LiveData<Result<Boolean>> get() = _updateProfileResult

    fun setSelectedPrimaryLocationPoint(point: Point) {
        _selectedPrimaryLocationPoint.value = point
    }

    // This function should not be suspend; it should launch a coroutine
    fun updateProfile(
        fullName: String,
        emailAddress: String, // emailAddress is not used, consider removing or using it
        whatsAppNumber: String?,
        active: Boolean,
        location: Point?,
        address: String?,
    ) {
        viewModelScope.launch {
            try {
                val user = Firebase.auth.currentUser
                    ?: throw IllegalStateException("User not authenticated")

                // 1. Prepare all Firestore updates in a single map
                val firestoreUpdates = mutableMapOf<String, Any>()

                if (!whatsAppNumber.isNullOrBlank()) {
                    firestoreUpdates["phoneNumber"] = whatsAppNumber
                }
                if (location != null) {
                    firestoreUpdates["locationLat"] = location.latitude()
                    firestoreUpdates["locationLng"] = location.longitude()
                }
                if (!address.isNullOrBlank()) {
                    firestoreUpdates["address"] = address
                }
                // Always include the active status and display name in the Firestore document
                firestoreUpdates["active"] = active
                firestoreUpdates["displayName"] = fullName


                // 2. Create the two independent asynchronous tasks
                val profileUpdateRequest = UserProfileChangeRequest.Builder()
                    .setDisplayName(fullName)
                    .build()

                // Task for updating Firebase Auth profile (returns nothing)
                val authUpdateJob = async { user.updateProfile(profileUpdateRequest).await() }

                // Task for updating Firestore document (returns nothing)
                val firestoreUpdateJob = async {
                    if (firestoreUpdates.isNotEmpty()) {
                        FirebaseFirestore.getInstance()
                            .collection(MontirPresisiUser.COLLECTION)
                            .document(user.uid)
                            .update(firestoreUpdates)
                            .await()
                    }
                }

                // 3. Wait for both concurrent tasks to complete
                authUpdateJob.await()
                firestoreUpdateJob.await()

                // 4. Update the local repository cache AFTER all network operations succeed
                updateLocalUserCache(
                    fullName,
                    whatsAppNumber,
                    active,
                    location,
                    address
                )

                _updateProfileResult.postValue(Result.success(true))
            } catch (e: Exception) {
                // Log the actual exception for debugging
                // Log.e("ProfileViewModel", "Failed to update profile", e)
                _updateProfileResult.postValue(Result.failure(e))
            }
        }
    }

    private fun updateLocalUserCache(
        fullName: String,
        whatsAppNumber: String?,
        active: Boolean,
        location: Point?,
        address: String?
    ) {
        val user = Firebase.auth.currentUser ?: return
        val userProfile = userRepository.getRecordByUserId(user.uid) ?: return

        userProfile.displayName = fullName
        userProfile.active = active

        if (!whatsAppNumber.isNullOrBlank()) {
            userProfile.phoneNumber = whatsAppNumber
        }
        if (location != null) {
            userProfile.locationLat = location.latitude().toString()
            userProfile.locationLng = location.longitude().toString()
        }
        if (!address.isNullOrBlank()) {
            userProfile.address = address
        }
        // After updating the fields, save the object back to the repository if needed
         userRepository.setUserRecord(userProfile) // or similar method
    }
}