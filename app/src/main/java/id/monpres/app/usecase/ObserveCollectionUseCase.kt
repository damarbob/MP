package id.monpres.app.usecase

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ObserveCollectionUseCase @Inject constructor(private val firestore: FirebaseFirestore) {
    companion object {
        val TAG = ObserveCollectionUseCase::class.simpleName
    }

    /**
     * Observes a whole Firestore collection without user filtering.
     *
     * @param T The type of the objects to deserialize from Firestore.
     * @param collection The Firestore collection to query.
     * @param itemClass The class of the items to be deserialized.
     * @return A Flow emitting a list of objects of type T.
     */
    operator fun <T : Any> invoke(
        collection: String,
        itemClass: Class<T>
    ): Flow<List<T>> = callbackFlow {
        val listenerRegistration = firestore
            .collection(collection)
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e(TAG, "Error fetching data from $collection", error)
                    cancel("Error fetching data from $collection", error)
                    return@addSnapshotListener
                }

                if (snapshots == null) {
                    Log.w(TAG, "Null snapshots received for $collection")
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val data = snapshots.documents.mapNotNull { documentSnapshot ->
                    try {
                        documentSnapshot.toObject(itemClass)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to deserialize document ${documentSnapshot.id}", e)
                        null
                    }
                }
                Log.d(TAG, "Received ${data.size} items from $collection")
                trySend(data)
            }

        awaitClose {
            Log.d(TAG, "Removing Firestore listener for $collection")
            listenerRegistration.remove()
        }
    }.flowOn(Dispatchers.IO)
}