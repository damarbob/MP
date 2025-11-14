package id.monpres.app.ui.adminnewusers

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.monpres.app.model.MontirPresisiUser
import id.monpres.app.state.UiState
import id.monpres.app.usecase.GetNewUsersUseCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AdminNewUsersViewModel @Inject constructor(
    private val getNewUsersUseCase: GetNewUsersUseCase
) : ViewModel() {

    // Private mutable state
    private val _uiState = MutableStateFlow<UiState<List<MontirPresisiUser>>>(UiState.Loading)

    // Public immutable state for the UI to observe
    val uiState: StateFlow<UiState<List<MontirPresisiUser>>> = _uiState.asStateFlow()

    init {
        listenForNewUsers()
    }

    private fun listenForNewUsers() {
        viewModelScope.launch {
            delay(1000)
            getNewUsersUseCase() // This returns the Flow
                .catch { e ->
                    // Handle errors from the Flow
                    Log.e("AdminNewUsersViewModel", "Error listening for users", e)
                    _uiState.value = UiState.Error(e.message ?: "Unknown error")
                }
                .collect { users ->
                    if (users.isEmpty()) {
                        // On success but empty, update the state to Empty
                        _uiState.value = UiState.Empty
                    } else {
                        // On success, update the state
                        _uiState.value = UiState.Success(users)
                    }
                }
        }
    }
}