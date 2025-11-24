package id.monpres.app.ui.adminnewusers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.monpres.app.enums.UserVerificationStatus
import id.monpres.app.model.MontirPresisiUser
import id.monpres.app.state.UiState
import id.monpres.app.usecase.GetNewUsersUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class AdminNewUsersViewModel @Inject constructor(
    private val getNewUsersUseCase: GetNewUsersUseCase
) : ViewModel() {

    // Default to PENDING as per requirement
    private val _filterStatus = MutableStateFlow<UserVerificationStatus?>(UserVerificationStatus.PENDING)
    private val _searchQuery = MutableStateFlow("")

    private val _uiState = MutableStateFlow<UiState<List<MontirPresisiUser>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<MontirPresisiUser>>> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(_filterStatus, _searchQuery) { status, query ->
                status to query
            }
                .onEach { _uiState.value = UiState.Loading } // Show loading on any filter change
                .flatMapLatest { (status, query) ->
                    getNewUsersUseCase(statusFilter = status, searchQuery = query)
                }
                .catch { e ->
                    _uiState.value = UiState.Error(e.message ?: "Unknown error")
                }
                .collect { users ->
                    _uiState.value = if (users.isEmpty()) UiState.Empty else UiState.Success(users)
                }
        }
    }

    fun setFilter(status: UserVerificationStatus?) {
        _filterStatus.value = status
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }
}