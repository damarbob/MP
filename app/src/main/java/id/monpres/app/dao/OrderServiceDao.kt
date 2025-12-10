package id.monpres.app.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import id.monpres.app.model.OrderService
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderServiceDao {

    /**
     * Inserts a list of order services. If a service with the same ID already exists,
     * it will be replaced. This is key for synchronization.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(orders: List<OrderService>)

    /**
     * Provides a PagingSource for the UI. Room's Paging 3 library handles all the
     * complexity of loading paginated data from the database.
     *
     * The query handles all filtering and searching LOCALLY.
     */
    @Query("""
        SELECT * FROM order_services
        WHERE
            -- Search logic (searches the 'id', 'name', etc.)
            (:searchQuery = '' OR id LIKE '%' || :searchQuery || '%' OR name LIKE '%' || :searchQuery || '%') AND
            
            -- Filter logic for status
            (status IN (:statusFilter)) AND

            -- Role-based filtering
            (CASE
                 WHEN :userRole = 'ADMIN' THEN 1 -- Admin sees all
                 WHEN :userRole = 'PARTNER' AND partnerId = :userId THEN 1
                 WHEN :userRole = 'CUSTOMER' AND userId = :userId THEN 1
                 ELSE 0
             END) = 1
        ORDER BY updatedAt DESC
    """)
    fun getOrderServicesPaged(
        searchQuery: String,
        statusFilter: List<String>, // Pass list of status names
        userId: String,
        userRole: String
    ): PagingSource<Int, OrderService>

    /**
     * Provides a PagingSource for the UI. Room's Paging 3 library handles all the
     * complexity of loading paginated data from the database.
     *
     * The query handles all filtering and searching LOCALLY.
     */
    @Query("""
        SELECT * FROM order_services
        WHERE
            -- Search logic (searches the 'id', 'name', etc.)
            (:searchQuery = '' OR id LIKE '%' || :searchQuery || '%' OR name LIKE '%' || :searchQuery || '%') AND
            
            -- Filter logic for status
            (status IN (:statusFilter)) AND

            -- Role-based filtering
            (CASE
                 WHEN :userRole = 'ADMIN' THEN 1 -- Admin sees all
                 WHEN :userRole = 'PARTNER' AND partnerId = :userId THEN 1
                 WHEN :userRole = 'CUSTOMER' AND userId = :userId THEN 1
                 ELSE 0
             END) = 1
        ORDER BY updatedAt DESC
        LIMIT :limit OFFSET :offset
    """)
    fun getPagedList(
        searchQuery: String,
        statusFilter: List<String>, // Pass list of status names
        userId: String,
        userRole: String,
        limit: Int,
        offset: Int
    ): List<OrderService>

    /**
     * Deletes all data from the table. Useful for a full refresh.
     */
    @Query("DELETE FROM order_services")
    suspend fun clearAll()

    /**
     * Gets the most recent 'updatedAt' timestamp from the local DB for a specific user.
     * This is the key to fetching only what's new from Firestore.
     */
    @Query("""
        SELECT MAX(updatedAt) FROM order_services
        WHERE 
            CASE :userRole
                WHEN 'ADMIN' THEN 1
                WHEN 'PARTNER' THEN partnerId = :userId
                WHEN 'CUSTOMER' THEN userId = :userId
                ELSE 0
            END = 1
    """)
    suspend fun getLatestTimestampForUser(userId: String, userRole: String): Long?

    /**
     * Gets all order IDs stored locally for a specific user.
     * This is used to compare against remote IDs to find what needs to be deleted.
     */
    @Query("""
        SELECT id FROM order_services
        WHERE 
            CASE :userRole
                WHEN 'ADMIN' THEN 1
                WHEN 'PARTNER' THEN partnerId = :userId
                WHEN 'CUSTOMER' THEN userId = :userId
                ELSE 0
            END = 1
    """)
    suspend fun getAllOrderIdsForUser(userId: String, userRole: String): List<String>

    /**
     * Deletes a list of orders by their IDs.
     */
    @Query("DELETE FROM order_services WHERE id IN (:orderIds)")
    suspend fun deleteOrdersByIds(orderIds: List<String>)

    /**
     * NEW: Provides a non-paginated Flow of order services for a user, limited to a certain count.
     * This is perfect for the home screen previews.
     */
    @Query("""
        SELECT * FROM order_services
        WHERE
            (CASE
                 WHEN :userRole = 'ADMIN' THEN 1
                 WHEN :userRole = 'PARTNER' AND partnerId = :userId THEN 1
                 WHEN :userRole = 'CUSTOMER' AND userId = :userId THEN 1
                 ELSE 0
             END) = 1
        ORDER BY updatedAt DESC
    """)
    fun getOrderServicesFlow(userId: String, userRole: String): Flow<List<OrderService>>

    /**
     * NEW: Provides a Flow for a single OrderService by its ID.
     * This is for ServiceProcessFragment.
     */
    @Query("SELECT * FROM order_services WHERE id = :orderId")
    fun getOrderServiceByIdFlow(orderId: String): Flow<OrderService?>

}
