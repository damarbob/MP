package id.monpres.app.usecase

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * Use case for updating Firestore documents by ID with merge behavior.
 *
 * Generic type-safe update operation.
 */
class UpdateDataByIdUseCase @Inject constructor(private val firestore: FirebaseFirestore) {
    /**
     * Updates a document in Firestore with merge behavior.
     *
     * @param id Document identifier
     * @param collection Firestore collection name
     * @param data Typed data object to merge
     */
    suspend operator fun <T : Any> invoke(id: String, collection: String, data: T) {
        firestore.collection(collection).document(id).set(data, SetOptions.merge()).await()
    }
}
