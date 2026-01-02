package id.monpres.app.ui.adminnewuser

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.monpres.app.enums.UserRole
import id.monpres.app.enums.UserVerificationStatus
import id.monpres.app.model.MontirPresisiUser
import id.monpres.app.usecase.UpdateUserUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AdminNewUserViewModel @Inject constructor(
    private val updateUserUseCase: UpdateUserUseCase,
    // @Inject private val deleteUserUseCase: DeleteUserUseCase,
    savedStateHandle: SavedStateHandle // Hilt injects this for us
) : ViewModel() {

    // --- STATE ---

    // Holds the state of the user object passed from the fragment arguments
    private val _user = MutableStateFlow<MontirPresisiUser?>(null)
    val user = _user.asStateFlow()

    // Holds the loading state (e.g., when an update is in progress)
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    // --- EVENTS ---
    private val _eventFlow = MutableSharedFlow<AdminNewUserEvent>()
    val eventFlow = _eventFlow.asSharedFlow()

    init {
        // Retrieve the user from the SavedStateHandle.
        // The key "user" MUST match the key you used in ARG_USER and newInstance.
        val userFromArgs: MontirPresisiUser? = savedStateHandle["user"]
        if (userFromArgs != null) {
            _user.value = userFromArgs
        } else {
            // This is a critical error; the fragment should not have been opened.
            reportError("Fatal Error: User data could not be loaded.")
        }
    }

    // --- PUBLIC ACTIONS ---

    fun onAcceptClicked() {
        updateUserStatus(UserVerificationStatus.VERIFIED, "User Accepted")
    }

    fun onRejectClicked() {
        updateUserStatus(UserVerificationStatus.REJECTED, "User Rejected")
    }

    fun onDeleteClicked() {
        // This would use a separate DeleteUserUseCase
        reportError("Delete function is not yet implemented.")
        // --- Example of what it would look like ---
        // val currentUser = _user.value ?: return
        // _isLoading.value = true
        // deleteUserUseCase(currentUser.userId) { result ->
        //     _isLoading.value = false
        //     viewModelScope.launch {
        //         result.onSuccess {
        //             _eventFlow.emit(AdminNewUserEvent.ActionSuccess("User Deleted"))
        //         }.onFailure { e ->
        //             _eventFlow.emit(AdminNewUserEvent.ShowToast(e.message ?: "Delete failed"))
        //         }
        //     }
        // }
    }

    // --- PRIVATE HELPERS ---

    /**
     * Private helper to handle both Accept and Reject actions.
     */
    private fun updateUserStatus(newStatus: UserVerificationStatus, successMessage: String) {
        val currentUser = _user.value ?: return reportError("Cannot update null user")

        _isLoading.value = true // Start loading
        val updatedUser = currentUser.copy(verificationStatus = newStatus)

        updateUserUseCase(updatedUser) { result ->
            _isLoading.value = false // Stop loading
            viewModelScope.launch {
                result.onSuccess {
                    // Send the success event
                    val event =
                        when (newStatus) {
                            UserVerificationStatus.VERIFIED -> AdminNewUserEvent.ActionVerified
                            UserVerificationStatus.REJECTED -> AdminNewUserEvent.ActionRejected
                            else -> AdminNewUserEvent.ActionOther
                        }
                    _eventFlow.emit(event)
                }.onFailure { exception ->
                    // Send the toast event
                    _eventFlow.emit(AdminNewUserEvent.ActionFailed)
                }
            }
        }
    }

    // --- PUBLIC ACTIONS ---

    // Add this function
    fun onRoleSelected(roleName: String) {
        val currentUser = _user.value ?: return
        try {
            // Convert String back to Enum
            val newRole = UserRole.valueOf(roleName)

            // Update the local state.
            // Assuming your MontirPresisiUser has a 'role' property of type UserRole.
            // If the property name is different (e.g. userRole), change it here.
            _user.value = currentUser.copy(role = newRole)
        } catch (e: IllegalArgumentException) {
            reportError("Invalid Role Selected")
        }
    }

    private fun reportError(message: String) {
        viewModelScope.launch {
            _eventFlow.emit(AdminNewUserEvent.Error(message))
        }
    }
}
