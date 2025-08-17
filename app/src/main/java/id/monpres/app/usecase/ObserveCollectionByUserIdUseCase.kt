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
import kotlin.collections.emptyList

@Singleton
class ObserveCollectionByUserIdUseCase @Inject constructor(private val firestore: FirebaseFirestore) {
    companion object {
        val TAG = ObserveCollectionByUserIdUseCase::class.simpleName
    }
    /**
     * Observes a Firestore collection for documents matching the given userId.
     *
     * @param T The type of the objects to deserialize from Firestore. Must be a class
     *          suitable for Firestore deserialization (e.g., a data class).
     * @param userId The ID of the user whose data is to be fetched.
     * @param collection The Firestore collection to query.
     * @param itemClass The class of the items to be deserialized.
     * @return A Flow emitting a list of objects of type T.
     */
    operator fun <T : Any> invoke(
        userId: String,
        collection: String, // Using the enum for type safety
        itemClass: Class<T> // Explicitly passing the class for deserialization
    ): Flow<List<T>> = callbackFlow {
        val listenerRegistration = firestore
            .collection(collection) // Use enum's path
            .whereEqualTo("userId", userId)
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, error -> // Renamed 'e' to 'error' for clarity
                if (error != null) {
                    // It's good practice to log the error before canceling
                    Log.e(TAG, "Error fetching data from $collection", error)
                    cancel("Error fetching data from $collection", error)
                    return@addSnapshotListener
                }

                // Defensive null check for snapshots, though Firestore listener usually provides it
                if (snapshots == null) {
                    Log.w(TAG, "Null snapshots received for $collection")
                    trySend(emptyList()) // Emit empty list if snapshots are unexpectedly null
                    return@addSnapshotListener
                }

                val data = snapshots.documents.mapNotNull { documentSnapshot ->
                    try {
                        documentSnapshot.toObject(itemClass)
                    } catch (e: Exception) {
                        // Log deserialization error for a specific document if needed
                         Log.w(TAG, "Failed to deserialize document ${documentSnapshot.id}", e)
                        null // This document will be filtered out by mapNotNull
                    }
                }
                Log.d(TAG, "Received ${data.size} items from $collection")
                trySend(data) // Simply trySend the data
            }

        // This block is called when the Flow is cancelled or completes.
        awaitClose {
             Log.d(TAG, "Removing Firestore listener for $collection and userId: $userId")
            listenerRegistration.remove()
        }
    }.flowOn(Dispatchers.IO)
}