package id.monpres.app.ui.profile

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.mapbox.geojson.Point
import dagger.hilt.android.lifecycle.HiltViewModel
import id.monpres.app.model.MontirPresisiUser
import id.monpres.app.repository.UserRepository
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

    suspend fun updateProfileNew(
        fullName: String,
        emailAddress: String,
        whatsAppNumber: String? = null,
        location: Point? = null
    ) {
        // Ensure user is signed in
        val user = Firebase.auth.currentUser
            ?: throw Exception("User not authenticated")

        val userProfile = userRepository.getRecordByUserId(user.uid)

        try {

            // Update display name
            val profileRequest = UserProfileChangeRequest.Builder()
                .setDisplayName(fullName)
                .build()
            user.updateProfile(profileRequest).await()

            // Apply local changes
            userProfile?.displayName = fullName

            /* Push extra fields into Firestore */

            if (!whatsAppNumber.isNullOrBlank()) {
                FirebaseFirestore.getInstance()
                    .collection(MontirPresisiUser.COLLECTION)
                    .document(user.uid)
                    .update(
                        mapOf(
                            "phoneNumber" to whatsAppNumber,
                        )
                    ).await()

                // Apply local changes
                userProfile?.phoneNumber = whatsAppNumber
            }

            if (location != null) {
                FirebaseFirestore.getInstance()
                    .collection(MontirPresisiUser.COLLECTION)
                    .document(user.uid)
                    .update(
                        mapOf(
                            "locationLat" to location.latitude().toString(),
                            "locationLng" to location.longitude().toString(),
                        )
                    ).await()

                // Apply local changes
                userProfile?.locationLat = location.latitude().toString()
                userProfile?.locationLng = location.longitude().toString()
            }

            _updateProfileResult.postValue(Result.success(true))
        } catch (e: Exception) {
            _updateProfileResult.postValue(Result.failure(e))
        }

    }
}