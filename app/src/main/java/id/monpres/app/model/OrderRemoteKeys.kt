package id.monpres.app.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "order_remote_keys")
data class OrderRemoteKeys(
    @PrimaryKey
    val orderId: String,
    val prevKey: Int?,
    val nextKey: Int?
)
