package id.monpres.app.ui.orderservicelist

import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import id.monpres.app.repository.OrderServiceRepository
import javax.inject.Inject

@HiltViewModel
class OrderServiceListViewModel @Inject constructor(firestore: FirebaseFirestore, firebaseAuth: FirebaseAuth) : ViewModel() {
    private val orderServiceRepository = OrderServiceRepository(firestore, firebaseAuth)

    fun getOrderService() = orderServiceRepository.observeOrderServiceByUserId()
}