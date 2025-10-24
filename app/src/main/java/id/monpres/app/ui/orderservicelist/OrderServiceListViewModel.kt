package id.monpres.app.ui.orderservicelist

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

@HiltViewModel
class OrderServiceListViewModel @Inject constructor(

) : ViewModel() {

    // State for the selected chip ID. Null means no chip is selected.
    private val _selectedChipId = MutableStateFlow<Int?>(null)
    val selectedChipId: LiveData<Int?> = _selectedChipId.asLiveData()

    fun setSelectedChipId(chipId: Int?) {
        _selectedChipId.value = chipId
    }

}