package id.monpres.app.usecase

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * Use case for retrieving all documents from a Firestore collection filtered by user ID.
 *
 * Generic type-safe retrieval with error handling.
 */
class GetDataByUserIdUseCase @Inject constructor(private val firestore: FirebaseFirestore) {
    /**
     * Retrieves all documents for a specific user from a collection.
     *
     * @param userId The user's unique identifier
     * @param collection The Firestore collection name
     * @param itemClass Class type for deserialization
     * @return List of typed objects or null on error
     */
    suspend operator fun <T: Any> invoke(userId: String, collection: String, itemClass: Class<T>): List<T>? {

        return try {
        firestore.collection(collection).whereEqualTo("userId", userId).get().await().toObjects(itemClass)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}