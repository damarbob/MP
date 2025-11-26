package id.monpres.app.ui.auth.adminverification

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import id.monpres.app.libraries.ErrorLocalizer
import id.monpres.app.model.MontirPresisiUser
import id.monpres.app.repository.UserRepository
import id.monpres.app.state.UiState
import id.monpres.app.state.UiState.Empty
import id.monpres.app.state.UiState.Loading
import id.monpres.app.state.UiState.Success
import id.monpres.app.utils.NetworkConnectivityObserver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

@HiltViewModel
class AdminVerificationViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val networkConnectivityObserver: NetworkConnectivityObserver,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    companion object {
        private val TAG = AdminVerificationViewModel::class.simpleName
    }
    private val _errorEvent = MutableSharedFlow<Throwable>()
    val errorEvent: SharedFlow<Throwable> = _errorEvent.asSharedFlow()

    val facebookId = savedStateHandle.getStateFlow("facebookId", "")
    val instagramId = savedStateHandle.getStateFlow("instagramId", "")
    val uiState = savedStateHandle.getStateFlow("UiState", AdminVerificationUiState.VERIFICATION_UI.name)

    fun setFacebookId(facebookId: String) {
        savedStateHandle["facebookId"] = facebookId
    }
    fun setInstagramId(instagramId: String) {
        savedStateHandle["instagramId"] = instagramId
    }
    fun setUiState(uiState: AdminVerificationUiState) {
        savedStateHandle["UiState"] = uiState.name
    }

    // Sealed classes for state
    enum class AdminVerificationUiState {
        VERIFICATION_UI,
        EDIT_FORM_UI
    }

    fun updateSocMed(user: MontirPresisiUser): Flow<UiState<MontirPresisiUser>> = flow {
        Log.d(TAG, "Updating user: $user")
        emit(Loading)

        if (!networkConnectivityObserver.isConnected()) {
            _errorEvent.emit(IOException(ErrorLocalizer.FIREBASE_PENDING_WRITE))
            emit(Empty)
            return@flow
        }

        try {
            userRepository.updateUser(user)
            emit(Success(user))
        } catch (e: Exception) {
            _errorEvent.emit(e)
            Log.e(TAG, "Error updating user", e)
            emit(Empty)
        }
    }.catch { e ->
        Log.e(TAG, "Error updating user", e)
        if (e is CancellationException) throw e
        _errorEvent.emit(e)
        emit(Empty)
    }
}