package id.monpres.app.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import id.monpres.app.model.Vehicle
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVehicle(vehicle: Vehicle)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVehicles(vehicles: List<Vehicle>)

    @Update
    suspend fun updateVehicle(vehicle: Vehicle)

    @Query("SELECT * FROM vehicles WHERE userId = :userId")
    suspend fun getVehiclesByUser(userId: String): List<Vehicle>

    @Query("SELECT * FROM vehicles WHERE userId = :userId")
    fun getVehiclesByUserFlow(userId: String): Flow<List<Vehicle>>

    @Query("SELECT * FROM vehicles WHERE id = :vehicleId")
    suspend fun getVehicleById(vehicleId: String): Vehicle?

    @Query("SELECT * FROM vehicles WHERE id IN (:vehiclesIds)")
    suspend fun getVehiclesByIds(vehiclesIds: List<String>): List<Vehicle>

    @Delete
    suspend fun deleteVehicle(vararg vehicle: Vehicle)

    @Query("DELETE FROM vehicles WHERE userId = :userId")
    suspend fun deleteAllVehiclesByUser(userId: String) // More specific delete

    @Query("DELETE FROM vehicles WHERE id IN (:vehicleIds)")
    suspend fun deleteVehiclesByIds(vehicleIds: List<String>)

    @Query("DELETE FROM vehicles")
    suspend fun deleteAllVehicles()

    @Transaction
    suspend fun withTransaction(block: suspend () -> Unit) { // Helper for transactions
        block()
    }

//    @Query("SELECT * FROM vehicles WHERE synced = 0")
//    suspend fun getUnsyncedVehicles(): List<Vehicle>?
//
//    @Query("UPDATE vehicles SET synced = 1 WHERE id = :vehicleId")
//    suspend fun markAsSynced(vehicleId: String)
}