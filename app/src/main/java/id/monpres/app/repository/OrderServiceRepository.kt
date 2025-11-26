package id.monpres.app.repository

import android.security.keystore.UserNotAuthenticatedException
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import id.monpres.app.enums.OrderStatus
import id.monpres.app.enums.OrderStatusType
import id.monpres.app.enums.UserRole
import id.monpres.app.model.OrderService
import id.monpres.app.usecase.ObserveCollectionByFieldUseCase
import id.monpres.app.usecase.ObserveCollectionByUserIdUseCase
import id.monpres.app.usecase.ObserveCollectionUseCase
import id.monpres.app.usecase.UpdateDataByIdUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

// Data Class to handle paging result
data class PagedOrderResult(
    val data: List<OrderService>,
    val lastSnapshot: DocumentSnapshot?
)

@Singleton
class OrderServiceRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val observeCollectionByUserIdUseCase: ObserveCollectionByUserIdUseCase,
    private val observeCollectionByFieldUseCase: ObserveCollectionByFieldUseCase,
    private val observeCollectionUseCase: ObserveCollectionUseCase,
    private val updateDataByIdUseCase: UpdateDataByIdUseCase
) : Repository<OrderService>() {
    companion object {
        val TAG = OrderServiceRepository::class.simpleName
    }

    /**
     * Fetches paginated data with server-side filtering/searching.
     */
    suspend fun getOrderServicesPaged(
        limit: Long,
        lastSnapshot: DocumentSnapshot?,
        searchQuery: String, // Server-side search
        statusTypeFilter: List<OrderStatusType>?, // Filter by status types (Ongoing/Closed)
        exactStatus: OrderStatus?, // Filter by exact status (Completed/Cancelled)
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
        // Note: Firestore cannot combine range filters (search) on one field with range filters on another.
        // If searching, we prioritize the search and skip status filters to avoid index errors,
        // or you need composite indexes in Firebase Console.
        if (searchQuery.isNotBlank()) {
            // "Server-side LIKE 'query%'"
            query = query
                .whereGreaterThanOrEqualTo("id", searchQuery)
                .whereLessThanOrEqualTo("id", searchQuery + "\uf8ff")
                .orderBy("id")
        } else {
            // 3. Status Filters (Only apply if NOT searching)
            if (exactStatus != null) {
                query = query.whereEqualTo("status", exactStatus.name)
            } else if (!statusTypeFilter.isNullOrEmpty()) {
                // Warning: 'in' queries are limited to 10 items
                val types = statusTypeFilter.map { it.name }
                // Requires a field in Firestore that stores the TYPE string, or use 'in' on status enum
                // For simplicity, assuming 'status' field holds the enum name:
                // We might need to filter client side if Firestore structure is complex,
                // but here is the logic if you have a helper field or filter by exact status list.

                // Simplified: If asking for "Ongoing", we usually want OPEN or IN_PROGRESS
                // Firestore 'in' query:
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

    /**
     * Observes all OrderServices in the collection without filtering by user.
     * Useful for admin views or public feeds.
     */
    fun observeOrderServices(): Flow<List<OrderService>> =
        observeCollectionUseCase(
            OrderService.COLLECTION,
            OrderService::class.java
        )
            .mapNotNull { orderServices ->
                // Clean up any nulls that might come from Firestore and cache locally
                setRecords(orderServices, false)
                orderServices
            }
            .distinctUntilChanged()
            .catch {
                Log.e(TAG, "Error observing all order services", it)
                emit(emptyList())
            }
            .flowOn(Dispatchers.IO)

    // TODO: Use observeOrderServicesByPartnerId for consistency
    fun observeOrderServicesByUserId(): Flow<List<OrderService>> =
        observeCollectionByUserIdUseCase(
            getCurrentUserId(),
            OrderService.COLLECTION, OrderService::class.java
        )
            .mapNotNull { orderServices ->
                // Clean up any nulls that might come from Firestore
                setRecords(orderServices, false)
                orderServices
            }
            .distinctUntilChanged()
            .catch {
                Log.e(TAG, "Error observing order services", it)
                emit(emptyList())
            }
            .flowOn(Dispatchers.IO) // Run the collection and mapping on an IO thread

    fun observeOrderServicesByPartnerId(): Flow<List<OrderService>> =
        observeCollectionByFieldUseCase(
            OrderService.PARTNER_ID,
            getCurrentUserId(),
            OrderService.COLLECTION, OrderService::class.java
        )
            .mapNotNull { orderServices ->
                setRecords(orderServices, false)
                orderServices
            }
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)

    fun getOrderServiceById(id: String): OrderService? = getRecords().find { it.id == id }



    /**
     * Best Practice: This is a one-shot write operation. It should be a simple suspend function.
     * It will throw an exception on failure, which the ViewModel will catch.
     */
    suspend fun updateOrderService(orderService: OrderService): OrderService {
        val id = orderService.id ?: throw IllegalArgumentException("OrderService ID cannot be null")
        updateDataByIdUseCase(id, OrderService.COLLECTION, orderService)
        return orderService
    }

    /**
     * Retrieves the UID of the currently authenticated Firebase user.
     *
     * @return The current user's UID as a String.
     * @throws UserNotAuthenticatedException if no user is currently authenticated.
     */
    private fun getCurrentUserId(): String {
        return firebaseAuth.currentUser?.uid
            ?: throw UserNotAuthenticatedException()
    }

    override fun onStart() {
        TODO("Not yet implemented")
    }

    override fun onDestroy() {
        TODO("Not yet implemented")
    }

    override fun createRecord(record: OrderService) {
        TODO("Not yet implemented")
    }

    override fun onRecordAdded(record: OrderService) {
        TODO("Not yet implemented")
    }

    override fun onRecordDeleted(record: OrderService) {
        TODO("Not yet implemented")
    }

    override fun onRecordCleared() {

    }
}