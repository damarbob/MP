package id.monpres.app.usecase

import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import id.monpres.app.model.OrderService

class GetOrderServicesUseCase {
    // In your Repository or ViewModel
    operator fun invoke(userId: String? = null, onResult: (Result<List<OrderService>>) -> Unit) {
        val baseQuery = Firebase
            .firestore
            .collection("orderServices") // TODO: Hardcoded collection name

        var query = if (userId !== null) {
            baseQuery
                .whereEqualTo("userId", userId)
        } else {
            baseQuery
        }

        query
            .get()
            .addOnSuccessListener { querySnapshot ->
                val orders = querySnapshot.map { document ->
                    document.toObject(OrderService::class.java).apply {
                        id = document.id // Capture Firestore document ID
                    }
                }
                onResult(Result.success(orders))
            }
            .addOnFailureListener { exception ->
                onResult(Result.failure(exception))
            }
    }
}