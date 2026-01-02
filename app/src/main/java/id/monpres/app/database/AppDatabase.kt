package id.monpres.app.database

import androidx.room.Database
import androidx.room.RoomDatabase
import id.monpres.app.dao.VehicleDao
import id.monpres.app.model.Vehicle

@Database(entities = [Vehicle::class], version = 3, exportSchema = false)
abstract class AppDatabase: RoomDatabase() {
    abstract fun vehicleDao(): VehicleDao
}
