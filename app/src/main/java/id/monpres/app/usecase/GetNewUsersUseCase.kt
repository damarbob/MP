package id.monpres.app.usecase

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Filter
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import id.monpres.app.enums.UserRole
import id.monpres.app.enums.UserVerificationStatus
import id.monpres.app.model.MontirPresisiUser
import id.monpres.app.model.UserPageResult
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetNewUsersUseCase @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    suspend operator fun invoke(
        statusFilter: UserVerificationStatus? = UserVerificationStatus.PENDING,
        searchQuery: String? = null,
        limit: Long = 10,
        startAfter: DocumentSnapshot? = null
    ): UserPageResult {

        var query: Query = firestore.collection(MontirPresisiUser.COLLECTION)

        if (!searchQuery.isNullOrBlank()) {
            // SEARCH MODE: Usually we don't paginate search for small datasets,
            // but if needed, we apply the same logic.
            val endQuery = searchQuery + "\uf8ff"
            query = query
                .whereArrayContains("searchTokens", searchQuery)
                .orderBy("userId")
        } else {
            // FILTER MODE
            when (statusFilter) {
                UserVerificationStatus.PENDING -> {
                    val roleCustomer = Filter.equalTo("role", UserRole.CUSTOMER)
                    val isPending =
                        Filter.equalTo("verificationStatus", UserVerificationStatus.PENDING)
                    val isNull = Filter.equalTo("verificationStatus", null)
                    query = query.where(Filter.and(roleCustomer, Filter.or(isPending, isNull)))
                }

                UserVerificationStatus.VERIFIED -> {
                    query =
                        query.whereEqualTo("verificationStatus", UserVerificationStatus.VERIFIED)
                }

                UserVerificationStatus.REJECTED -> {
                    query =
                        query.whereEqualTo("verificationStatus", UserVerificationStatus.REJECTED)
                }

                null -> { /* No filter */
                }
            }

            // Ensure sorting is consistent for pagination
            query = query.orderBy("createdAt", Query.Direction.DESCENDING)
        }

        // Apply Pagination
        if (startAfter != null) {
            query = query.startAfter(startAfter)
        }

        query = query.limit(limit)

        // Execute
        val snapshot = query.get().await()
        val users = snapshot.toObjects(MontirPresisiUser::class.java)
        val lastDoc = snapshot.documents.lastOrNull()

        return UserPageResult(users, lastDoc)
    }
}
