package id.monpres.app.repository

import android.security.keystore.UserNotAuthenticatedException
import android.util.Log
import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.room.withTransaction
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import id.monpres.app.dao.OrderServiceRemoteMediator
import id.monpres.app.database.AppDatabase
import id.monpres.app.enums.OrderStatus
import id.monpres.app.enums.OrderStatusType
import id.monpres.app.enums.UserRole
import id.monpres.app.model.OrderService
import id.monpres.app.usecase.ObserveCollectionByFieldUseCase
import id.monpres.app.usecase.ObserveCollectionByUserIdUseCase
import id.monpres.app.usecase.ObserveCollectionUseCase
import id.monpres.app.usecase.UpdateDataByIdUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
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
    private val updateDataByIdUseCase: UpdateDataByIdUseCase,
    private val appDatabase: AppDatabase
) : Repository<OrderService>() {
    companion object {
        val TAG = OrderServiceRepository::class.simpleName
    }

    // This reference needs to be held to be removed later
    private var realTimeListener: ListenerRegistration? = null

    /**
     * STARTS a real-time listener for changes. This should be called from the ViewModel's init block.
     * It handles additions, modifications, and DELETIONS.
     */
    fun startRealTimeOrderUpdates(userId: String, userRole: UserRole) {
        // Prevent multiple listeners
        if (realTimeListener != null) return

        var query: Query = firestore.collection(OrderService.COLLECTION)
        query = when (userRole) {
            UserRole.PARTNER -> query.whereEqualTo(OrderService.PARTNER_ID, userId)
            UserRole.CUSTOMER -> query.whereEqualTo("userId", userId)
            UserRole.ADMIN -> query
        }

        realTimeListener = query.addSnapshotListener { snapshots, error ->
            if (error != null) {
                Log.w(TAG, "Listen failed.", error)
                return@addSnapshotListener
            }

            if (snapshots == null) return@addSnapshotListener

            val source = if (snapshots.metadata.isFromCache) "CACHE" else "SERVER"
            Log.d(TAG, "Data fetched from $source, size: ${snapshots.documentChanges.size}")

            val ordersToUpdate = mutableListOf<OrderService>()
            val ordersToDelete = mutableListOf<String>()

            for (dc in snapshots.documentChanges) {
                when (dc.type) {
                    DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                        val order = dc.document.toObject(OrderService::class.java)
                        ordersToUpdate.add(order)
                        Log.d(TAG, "Real-time update: ADDED/MODIFIED ${order.id}")
                    }

                    DocumentChange.Type.REMOVED -> {
                        ordersToDelete.add(dc.document.id)
                        Log.d(TAG, "Real-time update: REMOVED ${dc.document.id}")
                    }
                }
            }

            // Perform DB operations in a background coroutine
            CoroutineScope(Dispatchers.IO).launch {
                appDatabase.withTransaction {
                    if (ordersToUpdate.isNotEmpty()) {
                        appDatabase.orderServiceDao().insertOrUpdate(ordersToUpdate)
                    }
                    if (ordersToDelete.isNotEmpty()) {
                        appDatabase.orderServiceDao().deleteOrdersByIds(ordersToDelete)
                    }
                }
            }
        }
    }

    /**
     * STOPS the real-time listener. Call this from ViewModel's onCleared().
     */
    fun stopRealTimeOrderUpdates() {
        realTimeListener?.remove()
        realTimeListener = null
    }

    @OptIn(ExperimentalPagingApi::class)
    fun getOrderServiceStream(
        searchQuery: String,
        statusFilter: List<String>?, // Now accepts String list
        userRole: UserRole,
        userId: String
    ): Flow<PagingData<OrderService>> {

        val pagingSourceFactory = {
            appDatabase.orderServiceDao().getOrderServicesPaged(
                searchQuery = searchQuery,
                statusFilter = statusFilter ?: emptyList(), // Pass empty list if null
                userId = userId,
                userRole = userRole.name
            )
        }

        return Pager(
            config = PagingConfig(
                pageSize = 8, // Your desired page size
                enablePlaceholders = false
            ),
            remoteMediator = OrderServiceRemoteMediator(
                firestore = firestore,
                database = appDatabase,
                userId = userId,
                userRole = userRole,
                statusFilterNames = statusFilter // Pass filters to mediator
            ),
            pagingSourceFactory = pagingSourceFactory
        ).flow
    }

    /**
     * NEW: Observes a limited list of recent orders directly from Room.
     * For HomeFragment and PartnerHomeFragment.
     */
    fun observeRecentOrderServices(userId: String, userRole: UserRole): Flow<List<OrderService>> {
        // The data comes directly from Room's DAO.
        return appDatabase.orderServiceDao().getOrderServicesFlow(userId, userRole.name)
    }

    /**
     * NEW: Observes a single order by its ID directly from Room.
     * For ServiceProcessFragment.
     */
    fun observeOrderServiceById(orderId: String): Flow<OrderService?> {
        return appDatabase.orderServiceDao().getOrderServiceByIdFlow(orderId)
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