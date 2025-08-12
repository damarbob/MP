package id.monpres.app.usecase

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class GetDataByUserIdUseCase @Inject constructor(private val firestore: FirebaseFirestore) {
    suspend operator fun <T: Any> invoke(userId: String, collection: String, itemClass: Class<T>): List<T>? {

        return try {
        firestore.collection(collection).whereEqualTo("userId", userId).get().await().toObjects(itemClass)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}