package id.monpres.app.ui.vehiclelist

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import id.monpres.app.model.Vehicle

class VehicleListViewModel : ViewModel() {
    private val _vehicles = MutableLiveData<List<Vehicle>>(emptyList())
    val vehicles: LiveData<List<Vehicle>> = _vehicles
//    val vehicles: List<Vehicle> = Vehicle.getSampleList()

    private val _scrollPosition = MutableLiveData(0)
    val scrollPosition: LiveData<Int> = _scrollPosition

    private val _scrollOffset = MutableLiveData(0)
    val scrollOffset: LiveData<Int> = _scrollOffset

    init {
        _vehicles.value = Vehicle.getSampleList()
    }

    fun saveScrollPosition(position: Int) {
        _scrollPosition.value = position
    }

    fun saveScrollOffset(offset: Int) {
        _scrollOffset.value = offset
    }
}