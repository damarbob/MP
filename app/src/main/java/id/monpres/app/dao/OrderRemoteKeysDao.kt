package id.monpres.app.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import id.monpres.app.model.OrderRemoteKeys

@Dao
interface OrderRemoteKeysDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(remoteKey: List<OrderRemoteKeys>)

    @Query("SELECT * FROM order_remote_keys WHERE orderId = :orderId")
    suspend fun remoteKeysByOrderId(orderId: String): OrderRemoteKeys?

    @Query("DELETE FROM order_remote_keys")
    suspend fun clearRemoteKeys()
}