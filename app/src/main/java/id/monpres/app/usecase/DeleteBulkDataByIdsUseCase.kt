package id.monpres.app.usecase

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case for deleting multiple Firestore documents by their IDs in a single batch operation.
 *
 * This use case queries documents by their IDs and deletes them using Firestore's batch write
 * functionality, ensuring atomic execution of all delete operations.
 */
@Singleton
class DeleteBulkDataByIdsUseCase @Inject constructor(private val firestore: FirebaseFirestore) {
    /**
     * Deletes multiple documents from a Firestore collection based on their IDs.
     *
     * @param collectionName The name of the Firestore collection containing the documents
     * @param documentIds List of document IDs to be deleted
     */
    suspend operator fun invoke(collectionName: String, documentIds: List<String>) {
        val collectionRef =
            firestore.collection(collectionName).whereIn("id", documentIds).get().await()
        val batch = firestore.batch()
        for (document in collectionRef.documents) {
            batch.delete(document.reference)
        }
        batch.commit().await()
    }
}