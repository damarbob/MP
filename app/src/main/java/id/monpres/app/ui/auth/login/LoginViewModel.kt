package id.monpres.app.ui.auth.login

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class LoginViewModel : ViewModel() {
    companion object {
        private val TAG = LoginViewModel::class.simpleName
    }

    private val _loginFormVisibilityState = MutableLiveData(false)
    val loginFormVisibilityState: LiveData<Boolean> get() = _loginFormVisibilityState

    fun toggleFormLayoutState() {
        _loginFormVisibilityState.value = !_loginFormVisibilityState.value!!
    }
}
