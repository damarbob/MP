package id.monpres.app.usecase

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class DeleteBulkDataByIdsUseCase @Inject constructor(private val firestore: FirebaseFirestore) {
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