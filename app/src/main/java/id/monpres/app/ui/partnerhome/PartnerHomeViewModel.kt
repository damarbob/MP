package id.monpres.app.ui.partnerhome

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class PartnerHomeViewModel @Inject constructor() : ViewModel() {

    // State for the selected chip ID. Null means no chip is selected.
    private val _selectedChipId = MutableStateFlow<Int?>(null)
    val selectedChipId: StateFlow<Int?> = _selectedChipId.asStateFlow()

    fun setSelectedChipId(chipId: Int?) {
        _selectedChipId.value = chipId
    }
}