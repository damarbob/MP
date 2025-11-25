package id.monpres.app.ui.adminnewusers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.DocumentSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import id.monpres.app.enums.UserVerificationStatus
import id.monpres.app.model.MontirPresisiUser
import id.monpres.app.state.UiState
import id.monpres.app.usecase.GetNewUsersUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AdminNewUsersViewModel @Inject constructor(
    private val getNewUsersUseCase: GetNewUsersUseCase
) : ViewModel() {

    // State
    private val _uiState = MutableStateFlow<UiState<List<MontirPresisiUser>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<MontirPresisiUser>>> = _uiState.asStateFlow()

    // Loading More State (Separate from main UI state to avoid flickering the whole screen)
    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    // Pagination Internal Data
    private val _currentList = mutableListOf<MontirPresisiUser>()
    private var lastDocumentSnapshot: DocumentSnapshot? = null
    private var isLastPage = false
    private val pageSize: Long = 8

    // Current Filters
    private var currentStatus: UserVerificationStatus? = UserVerificationStatus.PENDING
    private var currentQuery: String = ""

    private var fetchJob: Job? = null

    init {
        loadData(reset = true)
    }

    fun setFilter(status: UserVerificationStatus?) {
        if (currentStatus != status) {
            currentStatus = status
            loadData(reset = true)
        }
    }

    fun setSearchQuery(query: String) {
        if (currentQuery != query) {
            currentQuery = query
            loadData(reset = true)
        }
    }

    fun loadMore() {
        if (_isLoadingMore.value || isLastPage || _uiState.value is UiState.Loading) return
        loadData(reset = false)
    }

    private fun loadData(reset: Boolean) {
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            if (reset) {
                _uiState.value = UiState.Loading
                _currentList.clear()
                lastDocumentSnapshot = null
                isLastPage = false
            } else {
                _isLoadingMore.value = true
            }

            try {
                val result = getNewUsersUseCase(
                    statusFilter = currentStatus,
                    searchQuery = currentQuery,
                    limit = pageSize,
                    startAfter = if (reset) null else lastDocumentSnapshot
                )

                if (result.data.size < pageSize) {
                    isLastPage = true
                }

                lastDocumentSnapshot = result.lastSnapshot
                _currentList.addAll(result.data)

                if (_currentList.isEmpty()) {
                    _uiState.value = UiState.Empty
                } else {
                    // Emit a COPY of the list to trigger StateFlow update
                    _uiState.value = UiState.Success(_currentList.toList())
                }

            } catch (e: Exception) {
                if (reset) {
                    _uiState.value = UiState.Error(e.message ?: "Unknown error")
                } else {
                    // If load more fails, you might want a Toast/Snackbar event
                    // instead of replacing the whole screen with Error state.
                    // For now, we just stop loading.
                }
            } finally {
                _isLoadingMore.value = false
            }
        }
    }
}