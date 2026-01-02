package id.monpres.app.ui.adminhome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.monpres.app.model.MontirPresisiUser
import id.monpres.app.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AdminHomeViewModel @Inject constructor(
    userRepository: UserRepository
) : ViewModel() {

    private val _currentUser = MutableStateFlow<MontirPresisiUser?>(null)
    val currentUser = _currentUser.asStateFlow()

    var test: MontirPresisiUser? = null

    init {
        test = userRepository.getCurrentUserRecord()
        viewModelScope.launch {
            _currentUser.value = userRepository.userRecord.filterNotNull().first()
        }
    }

}
