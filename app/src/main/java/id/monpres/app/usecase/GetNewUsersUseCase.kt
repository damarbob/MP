package id.monpres.app.usecase

import android.util.Log
import com.google.firebase.firestore.Filter
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import id.monpres.app.enums.UserRole
import id.monpres.app.enums.UserVerificationStatus
import id.monpres.app.model.MontirPresisiUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetNewUsersUseCase @Inject constructor(
    private val firestore: FirebaseFirestore
    // No longer need newUserRepository for this, the Flow is the source of truth
) {
    companion object {
        private val TAG = GetNewUsersUseCase::class.java.simpleName
    }

    /**
     * Invokes the use case to listen for real-time updates to new users.
     * Returns a Flow that emits a new list of users whenever there's a change.
     */
    operator fun invoke(): Flow<List<MontirPresisiUser>> = callbackFlow {
        // Use Filter.or() to combine the 'PENDING' and 'null' status checks
        val pendingFilter = Filter.equalTo("verificationStatus", UserVerificationStatus.PENDING)
        val nullFilter = Filter.equalTo("verificationStatus", null)

        val listenerRegistration = firestore
            .collection(MontirPresisiUser.COLLECTION)
            .where(
                Filter.and(
                    Filter.equalTo("role", UserRole.CUSTOMER),
                    Filter.or(pendingFilter, nullFilter) // Combine checks into one query
                )
            )
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, error ->
                // Handle errors
                if (error != null) {
                    Log.w(TAG, "Listen failed.", error)
                    cancel("Error fetching new users", error) // Cancel the flow on error
                    return@addSnapshotListener
                }

                // Handle null snapshots (defensive check)
                if (snapshots == null) {
                    Log.w(TAG, "Null snapshots received")
                    trySend(emptyList()) // Send an empty list
                    return@addSnapshotListener
                }

                // Deserialize documents
                val users = snapshots.toObjects(MontirPresisiUser::class.java)
                Log.d(TAG, "New users updated: ${users.size} users")

                // Send the new list into the flow
                trySend(users)
            }

        // This block is called when the flow is cancelled (e.g., ViewModel is cleared)
        awaitClose {
            Log.d(TAG, "Stopping new user listener")
            listenerRegistration.remove() // Detach the listener
        }
    }.flowOn(Dispatchers.IO) // Run the listener logic on the IO dispatcher
}