package id.monpres.app.usecase

import android.security.keystore.UserNotAuthenticatedException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import id.monpres.app.enums.OrderStatus
import id.monpres.app.enums.OrderStatusType
import id.monpres.app.enums.UserRole
import id.monpres.app.model.OrderService
import id.monpres.app.model.PagedOrderResult
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case for retrieving paginated order services with role-based access control and flexible filtering.
 *
 * Features:
 * - **Role-based security**: Filters orders by PARTNER, CUSTOMER, or ADMIN role
 * - **Search**: Prefix search on order ID
 * - **Status filtering**: Filter by exact status or status type groups
 * - **Pagination**: Cursor-based pagination using DocumentSnapshot
 *
 * Note: Search mode and status filtering are mutually exclusive for query optimization.
 */
@Singleton
class GetPagedOrderServicesUseCase @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {
    /**
     * Retrieves a paginated list of orders with role-based filtering and search.
     *
     * @param limit Maximum number of orders to retrieve per page
     * @param lastSnapshot Document snapshot from the previous page for pagination (null for first page)
     * @param searchQuery Order ID prefix to search for (disables status filtering when active)
     * @param statusTypeFilter List of status types to filter by (e.g., PENDING, COMPLETED)
     * @param exactStatus Specific order status to filter by (overrides statusTypeFilter)
     * @param userRole User's role for access control (PARTNER, CUSTOMER, or ADMIN)
     * @return PagedOrderResult containing orders and the last document for next page
     * @throws UserNotAuthenticatedException if no authenticated user is found
     */
    suspend operator fun invoke(
        limit: Long,
        lastSnapshot: DocumentSnapshot?,
        searchQuery: String,
        statusTypeFilter: List<OrderStatusType>?,
        exactStatus: OrderStatus?,
        userRole: UserRole
    ): PagedOrderResult {
        val userId = getCurrentUserId()
        val collectionRef = firestore.collection(OrderService.COLLECTION)
        var query: Query = collectionRef

        // Role Security
        query = when (userRole) {
            UserRole.PARTNER -> query.whereEqualTo(OrderService.PARTNER_ID, userId)
            UserRole.CUSTOMER -> query.whereEqualTo("userId", userId) // Assuming field is userId
            UserRole.ADMIN -> query
            else -> query.whereEqualTo("userId", userId)
        }

        // Search Logic (Prefix Search on ID)
        if (searchQuery.isNotBlank()) {
            query = query
                .whereGreaterThanOrEqualTo("id", searchQuery)
                .whereLessThanOrEqualTo("id", searchQuery + "")
                .orderBy("id")
        } else {
            // Status Filters (Only apply if NOT searching)
            if (exactStatus != null) {
                query = query.whereEqualTo("status", exactStatus.name)
            } else if (!statusTypeFilter.isNullOrEmpty()) {
                val statuses = OrderStatus.entries
                    .filter { statusTypeFilter.contains(it.type) }
                    .map { it.name }

                if (statuses.isNotEmpty()) {
                    query = query.whereIn("status", statuses)
                }
            }

            // Default Sort
            query = query.orderBy("updatedAt", Query.Direction.DESCENDING)
        }

        // Pagination
        query = query.limit(limit)

        if (lastSnapshot != null) {
            query = query.startAfter(lastSnapshot)
        }

        // Execute
        val snapshot = query.get().await()
        val orders = snapshot.toObjects(OrderService::class.java)
        val lastDoc = if (snapshot.documents.isNotEmpty()) snapshot.documents.last() else null

        return PagedOrderResult(orders, lastDoc)
    }

    /**
     * Gets the current authenticated user ID.
     *
     * @return User ID from Firebase Auth
     * @throws UserNotAuthenticatedException if user is not authenticated
     */
    private fun getCurrentUserId(): String {
        return firebaseAuth.currentUser?.uid
            ?: throw UserNotAuthenticatedException()
    }
}