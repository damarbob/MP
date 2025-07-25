package id.monpres.app.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import id.monpres.app.model.Vehicle

class HomeViewModel : ViewModel() {
    private val _vehicles = MutableLiveData<List<Vehicle>>(emptyList())
    val vehicles: LiveData<List<Vehicle>> = _vehicles
//    val vehicles: List<Vehicle> = Vehicle.getSampleList()

    private val _scrollPosition = MutableLiveData(0)
    val scrollPosition: LiveData<Int> = _scrollPosition

    private val _scrollOffset = MutableLiveData(0)
    val scrollOffset: LiveData<Int> = _scrollOffset

    init {
        _vehicles.value = Vehicle.getSampleList().take(5)
    }

    fun saveScrollPosition(position: Int) {
        _scrollPosition.value = position
    }

    fun saveScrollOffset(offset: Int) {
        _scrollOffset.value = offset
    }

    fun clearScrollPosition() {
        _scrollPosition.value = 0
    }
}