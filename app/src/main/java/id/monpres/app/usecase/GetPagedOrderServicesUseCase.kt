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

@Singleton
class GetPagedOrderServicesUseCase @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {
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

        // 1. Role Security
        query = when (userRole) {
            UserRole.PARTNER -> query.whereEqualTo(OrderService.PARTNER_ID, userId)
            UserRole.CUSTOMER -> query.whereEqualTo("userId", userId) // Assuming field is userId
            UserRole.ADMIN -> query
            else -> query.whereEqualTo("userId", userId)
        }

        // 2. Search Logic (Prefix Search on ID)
        if (searchQuery.isNotBlank()) {
            query = query
                .whereGreaterThanOrEqualTo("id", searchQuery)
                .whereLessThanOrEqualTo("id", searchQuery + "")
                .orderBy("id")
        } else {
            // 3. Status Filters (Only apply if NOT searching)
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

            // 4. Default Sort
            query = query.orderBy("updatedAt", Query.Direction.DESCENDING)
        }

        // 5. Pagination
        query = query.limit(limit)

        if (lastSnapshot != null) {
            query = query.startAfter(lastSnapshot)
        }

        // 6. Execute
        val snapshot = query.get().await()
        val orders = snapshot.toObjects(OrderService::class.java)
        val lastDoc = if (snapshot.documents.isNotEmpty()) snapshot.documents.last() else null

        return PagedOrderResult(orders, lastDoc)
    }

    private fun getCurrentUserId(): String {
        return firebaseAuth.currentUser?.uid
            ?: throw UserNotAuthenticatedException()
    }
}