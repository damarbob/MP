package id.monpres.app.dao

import android.util.Log
import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.withTransaction
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import id.monpres.app.database.AppDatabase
import id.monpres.app.enums.UserRole
import id.monpres.app.model.OrderRemoteKeys
import id.monpres.app.model.OrderService
import kotlinx.coroutines.tasks.await

private const val STARTING_PAGE_INDEX = 1

@OptIn(ExperimentalPagingApi::class)
class OrderServiceRemoteMediator(
    private val firestore: FirebaseFirestore,
    private val database: AppDatabase,
    private val userId: String,
    private val userRole: UserRole,
    private val statusFilterNames: List<String>? // Pass filters here
) : RemoteMediator<Int, OrderService>() {

    private val orderServiceDao: OrderServiceDao = database.orderServiceDao()
    private val remoteKeysDao: OrderRemoteKeysDao = database.orderRemoteKeysDao()

    companion object {
        private val TAG = OrderServiceRemoteMediator::class.simpleName
    }

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, OrderService>
    ): MediatorResult {
        return try {
            val page = when (loadType) {
                LoadType.REFRESH -> {
                    // On refresh, we don't fetch from network. We assume the snapshot listener will handle updates.
                    // Returning success with endOfPaginationReached=false tells Paging to just load from DB.
                    Log.d(TAG, "REFRESH triggered. Letting local data source handle it.")
                    return MediatorResult.Success(endOfPaginationReached = false)
                }
                LoadType.PREPEND -> {
                    // We only page forward, so no need to prepend.
                    return MediatorResult.Success(endOfPaginationReached = true)
                }
                LoadType.APPEND -> {
                    // We are scrolling down, find the next page number to load.
                    val remoteKeys = getLastRemoteKey(state)
                    val nextPage = remoteKeys?.nextKey
                        ?: return MediatorResult.Success(endOfPaginationReached = remoteKeys != null)
                    Log.d(TAG, "APPEND triggered. Loading page: $nextPage")
                    nextPage
                }
            }

            // You must now build your query with pagination and filters
            var query: Query = firestore.collection(OrderService.COLLECTION)
            query = when (userRole) {
                UserRole.PARTNER -> query.whereEqualTo(OrderService.PARTNER_ID, userId)
                UserRole.CUSTOMER -> query.whereEqualTo("userId", userId)
                UserRole.ADMIN -> query
            }

            if (!statusFilterNames.isNullOrEmpty()) {
                query = query.whereIn("status", statusFilterNames)
            }

            // Order by and then paginate
            query = query.orderBy("updatedAt", Query.Direction.DESCENDING)
                .limit(state.config.pageSize.toLong())
                .startAt(page * state.config.pageSize) // Page-based offset

            val snapshot = query.get().await()
            val orders = snapshot.toObjects(OrderService::class.java)
            val endOfPaginationReached = orders.isEmpty()
            Log.i(TAG, "Fetched ${orders.size} orders from Firestore for page $page.")

            database.withTransaction {
                val prevKey = if (page == STARTING_PAGE_INDEX) null else page - 1
                val nextKey = if (endOfPaginationReached) null else page + 1
                val keys = orders.map {
                    OrderRemoteKeys(orderId = it.id, prevKey = prevKey, nextKey = nextKey)
                }
                remoteKeysDao.insertAll(keys)
                orderServiceDao.insertOrUpdate(orders)
            }

            MediatorResult.Success(endOfPaginationReached = endOfPaginationReached)
        } catch (e: Exception) {
            Log.e(TAG, "Error during remote mediation", e)
            MediatorResult.Error(e)
        }
    }

    private suspend fun getLastRemoteKey(state: PagingState<Int, OrderService>): OrderRemoteKeys? {
        return state.pages.lastOrNull { it.data.isNotEmpty() }?.data?.lastOrNull()
            ?.let { order ->
                remoteKeysDao.remoteKeysByOrderId(order.id)
            }
    }
}