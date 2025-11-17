package id.monpres.app.ui.profile

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.mapbox.geojson.Point
import dagger.hilt.android.lifecycle.HiltViewModel
import id.monpres.app.enums.PartnerCategory
import id.monpres.app.enums.UserRole
import id.monpres.app.model.MontirPresisiUser
import id.monpres.app.repository.UserRepository
import id.monpres.app.state.UiState
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    // --- State for the entire screen ---
    private val _uiState = MutableStateFlow<UiState<MontirPresisiUser>>(UiState.Loading)
    val uiState: StateFlow<UiState<MontirPresisiUser>> = _uiState.asStateFlow()

    private val _errorEvent = MutableSharedFlow<Throwable>()
    val errorEvent: SharedFlow<Throwable> = _errorEvent.asSharedFlow()

    // --- State for the selected location point ---
    private val _selectedPrimaryLocationPoint = MutableStateFlow<Point?>(null)
    val selectedPrimaryLocationPoint: StateFlow<Point?> =
        _selectedPrimaryLocationPoint.asStateFlow()

    private val _selectedCategories = MutableStateFlow<Set<PartnerCategory>>(emptySet())
    val selectedCategories: StateFlow<Set<PartnerCategory>> = _selectedCategories.asStateFlow()

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            val userAuth = auth.currentUser
            if (userAuth == null) {
                _uiState.value = UiState.Error("User not authenticated")
                return@launch
            }

            userRepository.userRecord.filterNotNull().first().let {
                _uiState.value = UiState.Success(it)
                // Initialize selected location point
                val lat = it.locationLat?.toDoubleOrNull()
                val lng = it.locationLng?.toDoubleOrNull()

                if (lat != null && lng != null) {
                    _selectedPrimaryLocationPoint.value = Point.fromLngLat(lng, lat)
                }

                _selectedCategories.value = it.partnerCategories?.toSet() ?: emptySet()
            }
        }
    }

    fun onLocationSelected(point: Point) {
        _selectedPrimaryLocationPoint.value = point
    }

    fun onCategoryChanged(category: PartnerCategory, isChecked: Boolean) {
        val currentCategories = _selectedCategories.value.toMutableSet()

        if (isChecked) {
            currentCategories.add(category)
        } else {
            currentCategories.remove(category)
        }
        _selectedCategories.value = currentCategories
    }

    // This function should not be suspend; it should launch a coroutine
    fun updateProfile(
        fullName: String?,
        emailAddress: String, // emailAddress is not used, consider removing or using it
        whatsAppNumber: String?,
        active: Boolean,
        address: String?,
        instagramId: String?,
        facebookId: String?,
    ) {
        viewModelScope.launch {
            try {
                _uiState.value = UiState.Loading

                val user = auth.currentUser
                    ?: run {
                        _errorEvent.emit(
                            FirebaseAuthException(
                                "AUTH_ERROR",
                                "User not authenticated"
                            )
                        )
                        _uiState.value = UiState.Error("User not authenticated")
                        return@launch
                    }

                val montirPresisiUser = userRepository.userRecord.filterNotNull().first()
                Log.d("ProfileViewModel", "montirPresisiUser: $montirPresisiUser")

                if (montirPresisiUser.role == UserRole.PARTNER && _selectedCategories.value.isEmpty()) {
                    _errorEvent.emit(IllegalArgumentException("Partner must select at least one category"))
                    return@launch
                }

                // 1. Prepare all Firestore updates in a single map
                val firestoreUpdates = mutableMapOf<String, Any>()
                val location = _selectedPrimaryLocationPoint.value

                if (!whatsAppNumber.isNullOrBlank()) {
                    firestoreUpdates["phoneNumber"] = whatsAppNumber
                }
                if (location != null) {
                    firestoreUpdates["locationLat"] = location.latitude().toString()
                    firestoreUpdates["locationLng"] = location.longitude().toString()
                }
                if (!address.isNullOrBlank()) {
                    firestoreUpdates["address"] = address
                }
                if (!instagramId.isNullOrBlank()) {
                    firestoreUpdates["instagramId"] = instagramId
                }
                if (!facebookId.isNullOrBlank()) {
                    firestoreUpdates["facebookId"] = facebookId
                }
                if (montirPresisiUser.role == UserRole.PARTNER) {
                    firestoreUpdates["partnerCategories"] = _selectedCategories.value.toList()
                }
                // Always include the active status and display name in the Firestore document
                firestoreUpdates["active"] = active
                var authJob: Deferred<Unit>? = null
                if (!fullName.isNullOrBlank()) {
                    firestoreUpdates["displayName"] = fullName
                    // 2. Create the two independent asynchronous tasks
                    val profileUpdateRequest = UserProfileChangeRequest.Builder()
                        .setDisplayName(fullName)
                        .build()

                    // Task for updating Firebase Auth profile (returns nothing)
                    authJob = async { user.updateProfile(profileUpdateRequest).await() }
                }

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
                authJob?.await()
                firestoreUpdateJob.await()

                // 4. Update the local repository cache AFTER all network operations succeed
                updateLocalUserCache(
                    fullName,
                    whatsAppNumber,
                    active,
                    location,
                    address,
                    instagramId,
                    facebookId
                )

                _uiState.value = UiState.Success(userRepository.getRecordByUserId(user.uid)!!)
            } catch (e: Exception) {
                // Log the actual exception for debugging
                // Log.e("ProfileViewModel", "Failed to update profile", e)
                _errorEvent.emit(e)
                _uiState.value = UiState.Error("Failed to update profile")
            }
        }
    }

    private fun updateLocalUserCache(
        fullName: String?,
        whatsAppNumber: String?,
        active: Boolean,
        location: Point?,
        address: String?,
        instagramId: String?,
        facebookId: String?,
    ) {
        val user = Firebase.auth.currentUser ?: return
        val userProfile = userRepository.getRecordByUserId(user.uid) ?: return

        if (!fullName.isNullOrBlank()) {
            userProfile.displayName = fullName
        }
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
        if (!instagramId.isNullOrBlank()) {
            userProfile.instagramId = instagramId
        }
        if (!facebookId.isNullOrBlank()) {
            userProfile.facebookId = facebookId
        }
        if (userProfile.role == UserRole.PARTNER) {
            userProfile.partnerCategories = _selectedCategories.value.toList()
        }
        // After updating the fields, save the object back to the repository if needed
        userRepository.setCurrentUserRecord(userProfile) // or similar method
    }
}