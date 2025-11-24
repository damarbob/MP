package id.monpres.app.usecase

import android.util.Log
import com.google.firebase.firestore.Filter
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import id.monpres.app.enums.UserRole
import id.monpres.app.enums.UserVerificationStatus
import id.monpres.app.model.MontirPresisiUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetNewUsersUseCase @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    /**
     * @param statusFilter
     * - PENDING: Shows CUSTOMERS with Pending or Null status.
     * - VERIFIED: Shows ALL Verified users.
     * - REJECTED: Shows ALL Rejected users.
     * - NULL: Shows ALL users (no status filter).
     * @param searchQuery Search by UID (overrides filters for scalability).
     */
    operator fun invoke(
        statusFilter: UserVerificationStatus? = UserVerificationStatus.PENDING,
        searchQuery: String? = null
    ): Flow<List<MontirPresisiUser>> = callbackFlow {

        var query: Query = firestore.collection(MontirPresisiUser.COLLECTION)

        if (!searchQuery.isNullOrBlank()) {
            // --- SEARCH MODE ---
            // Scalability: When searching, we drop complex filters to avoid
            // needing exponential index combinations. We search strictly by UID.
            val endQuery = searchQuery + "\uf8ff"
            query = query
                .whereGreaterThanOrEqualTo("userId", searchQuery)
                .whereLessThanOrEqualTo("userId", endQuery)
                .orderBy("userId") // Firestore requires OrderBy to match Range Filter

        } else {
            // --- FILTER MODE ---

            when (statusFilter) {
                UserVerificationStatus.PENDING -> {
                    // Logic: "Pending should show CUSTOMER only"
                    // AND (Status is Pending OR Status is Null)
                    val roleCustomer = Filter.equalTo("role", UserRole.CUSTOMER)
                    val isPending =
                        Filter.equalTo("verificationStatus", UserVerificationStatus.PENDING)
                    val isNull = Filter.equalTo("verificationStatus", null)

                    // (Role == Customer) AND (Status == Pending OR Status == Null)
                    query = query.where(Filter.and(roleCustomer, Filter.or(isPending, isNull)))
                }

                UserVerificationStatus.VERIFIED -> {
                    // Logic: "Accepted should show all VERIFIED users" (implies ignore Role)
                    query =
                        query.whereEqualTo("verificationStatus", UserVerificationStatus.VERIFIED)
                }

                UserVerificationStatus.REJECTED -> {
                    // Logic: "Rejected should show all REJECTED users"
                    query =
                        query.whereEqualTo("verificationStatus", UserVerificationStatus.REJECTED)
                }

                null -> {
                    // Logic: "All should show all users" (No filters applied)
                    // We do nothing here, querying the raw collection.
                }
            }

            // Always sort by newest first when not searching
            query = query.orderBy("createdAt", Query.Direction.DESCENDING)
        }

        val listenerRegistration = query.addSnapshotListener { snapshots, error ->
            if (error != null) {
                // IMPORTANT: If this logs "FAILED_PRECONDITION", you need to create an Index.
                // Check the Logcat link provided by Firebase in the error message.
                Log.e("GetNewUsersUseCase", "Firestore Listen Failed: ${error.message}", error)
                close(error)
                return@addSnapshotListener
            }

            val users = snapshots?.toObjects(MontirPresisiUser::class.java) ?: emptyList()
            trySend(users)
        }

        awaitClose { listenerRegistration.remove() }
    }.flowOn(Dispatchers.IO)
}