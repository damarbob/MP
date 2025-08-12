package id.monpres.app.ui.serviceprocess

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import id.monpres.app.repository.OrderServiceRepository
import javax.inject.Inject

@HiltViewModel
class ServiceProcessViewModel @Inject constructor(firestore: FirebaseFirestore, firebaseAuth: FirebaseAuth) : ViewModel() {
    private val orderServiceRepository = OrderServiceRepository(firestore, firebaseAuth)

    suspend fun getOrderServiceById(id: String) = orderServiceRepository.observeOrderServiceById(id)
}