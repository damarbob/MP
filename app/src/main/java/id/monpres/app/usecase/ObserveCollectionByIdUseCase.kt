package id.monpres.app.usecase

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ObserveCollectionByIdUseCase @Inject constructor(private val firestore: FirebaseFirestore) {
    companion object {
        val TAG = ObserveCollectionByIdUseCase::class.simpleName
    }
    /**
     * Observes a single Firestore document by its ID.
     *
     * @param T The type of the object to deserialize from Firestore. Must be a class
     *          suitable for Firestore deserialization (e.g., a data class with a no-arg constructor).
     * @param id The ID of the document to observe.
     * @param collection The Firestore collection where the document resides.
     * @param itemClass The class of the item to be deserialized.
     * @return A Flow emitting the deserialized object of type T?, or null if the document
     *         doesn't exist or cannot be deserialized. The flow will be cancelled on error.
     */
    operator fun <T : Any> invoke(
        id: String,
        collection: String, // Consider using an enum for type safety here as well
        itemClass: Class<T>
    ): Flow<T?> = callbackFlow { // Return Flow<T?> to allow emitting null
        if (id.isBlank()) {
            Log.w(TAG, "Document ID is blank. Closing flow.")
            cancel("Document ID is blank") // Or send(null) then close(), depending on desired behavior
            return@callbackFlow
        }

        val listenerRegistration = firestore
            .collection(collection)
            .document(id)
            .addSnapshotListener { documentSnapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error fetching document $id from $collection", error)
                    cancel("Error fetching document $id from $collection", error)
                    return@addSnapshotListener
                }

                if (documentSnapshot != null && documentSnapshot.exists()) {
                    try {
                        val data = documentSnapshot.toObject(itemClass)
                        if (data != null) {
                            trySend(data)
                        } else {
                            // Document exists but could not be converted to T (e.g. type mismatch)
                            Log.w(TAG, "Document $id exists but failed to convert to ${itemClass.simpleName}")
                            trySend(null) // Or cancel with a more specific error
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error deserializing document $id to ${itemClass.simpleName}", e)
                        // Decide if you want to cancel or send null
                        // cancel("Deserialization error for document $id", e)
                        trySend(null) // Sending null if deserialization fails
                    }
                } else {
                    // Document does not exist or snapshot is null
                    Log.d(TAG, "Document $id does not exist in $collection")
                    trySend(null) // Emit null if the document doesn't exist
                }
            }

        // This block is called when the Flow is cancelled or completes.
        awaitClose {
            Log.d(TAG, "Removing Firestore listener for $collection/$id")
            listenerRegistration.remove()
        }
    }.flowOn(Dispatchers.IO)
}
