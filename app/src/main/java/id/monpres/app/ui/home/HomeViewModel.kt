package id.monpres.app.ui.home

import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import id.monpres.app.repository.OrderServiceRepository
import id.monpres.app.repository.VehicleRepository
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val vehicleRepository: VehicleRepository,
    firestore: FirebaseFirestore,
    firebaseAuth: FirebaseAuth
) : ViewModel() {
    private val orderServiceRepository = OrderServiceRepository(firestore, firebaseAuth)
    fun getVehiclesFlow() = vehicleRepository.getVehiclesByUserIdFlow()
    fun getOrderServiceFlow() = orderServiceRepository.observeOrderServiceByUserId()
}