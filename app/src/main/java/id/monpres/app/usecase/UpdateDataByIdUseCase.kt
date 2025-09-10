package id.monpres.app.usecase

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class UpdateDataByIdUseCase @Inject constructor(private val firestore: FirebaseFirestore) {
    suspend operator fun <T : Any> invoke(id: String, collection: String, data: T) {
        firestore.collection(collection).document(id).set(data, SetOptions.merge()).await()
    }
}