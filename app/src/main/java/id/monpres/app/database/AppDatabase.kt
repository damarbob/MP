package id.monpres.app.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import id.monpres.app.dao.OrderRemoteKeysDao
import id.monpres.app.dao.OrderServiceDao
import id.monpres.app.dao.VehicleDao
import id.monpres.app.model.Converters
import id.monpres.app.model.OrderRemoteKeys
import id.monpres.app.model.OrderService
import id.monpres.app.model.Vehicle

@Database(entities = [Vehicle::class, OrderService::class, OrderRemoteKeys::class], version = 6, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase: RoomDatabase() {
    abstract fun vehicleDao(): VehicleDao
    abstract fun orderServiceDao(): OrderServiceDao
    abstract fun orderRemoteKeysDao(): OrderRemoteKeysDao
}