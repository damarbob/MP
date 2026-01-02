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

/**
 * Use case for retrieving paginated user lists with filtering and search capabilities.
 *
 * Supports two main modes:
 * - **Filter mode**: Filter users by verification status with pagination
 * - **Search mode**: Search users by search tokens
 */
@Singleton
class GetNewUsersUseCase @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    /**
     * Retrieves a paginated list of users with optional filtering and search.
     *
     * @param statusFilter Filter users by verification status (PENDING, VERIFIED, REJECTED, or null for all)
     * @param searchQuery Optional search query to filter users by search tokens
     * @param limit Maximum number of users to retrieve per page (default: 10)
     * @param startAfter Document snapshot to start pagination after (for subsequent pages)
     * @return UserPageResult containing the list of users and the last document for pagination
     */
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
