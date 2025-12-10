package id.monpres.app.model

import androidx.room.TypeConverter
import com.google.firebase.Timestamp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import id.monpres.app.enums.OrderStatus

class Converters {
    private val gson = Gson()

    // Converter for OrderStatus enum
    @TypeConverter
    fun fromOrderStatus(status: OrderStatus?): String? {
        return status?.name
    }

    @TypeConverter
    fun toOrderStatus(name: String?): OrderStatus? {
        return name?.let { OrderStatus.valueOf(it) }
    }

    // Converter for Firestore Timestamp
    @TypeConverter
    fun fromTimestamp(timestamp: Timestamp?): Long? {
        return timestamp?.toDate()?.time
    }

    @TypeConverter
    fun toTimestamp(value: Long?): Timestamp? {
        return value?.let { Timestamp(it / 1000, ((it % 1000) * 1000000).toInt()) }
    }

    // --- JSON-based Converters for Lists and Objects ---

    // For List<String>
    @TypeConverter
    fun fromStringList(value: List<String>?): String? {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toStringList(value: String?): List<String>? {
        if (value == null) return null
        val listType = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, listType)
    }

    // For List<OrderItem>
    @TypeConverter
    fun fromOrderItemList(value: List<OrderItem>?): String? {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toOrderItemList(value: String?): List<OrderItem>? {
        if (value == null) return null
        val listType = object : TypeToken<List<OrderItem>>() {}.type
        return gson.fromJson(value, listType)
    }

    // For a single MontirPresisiUser object
    @TypeConverter
    fun fromUser(user: MontirPresisiUser?): String? {
        return gson.toJson(user)
    }

    @TypeConverter
    fun toUser(userString: String?): MontirPresisiUser? {
        if (userString == null) return null
        return gson.fromJson(userString, MontirPresisiUser::class.java)
    }

    // For a single Vehicle object
    @TypeConverter
    fun fromVehicle(vehicle: Vehicle?): String? {
        return gson.toJson(vehicle)
    }

    @TypeConverter
    fun toVehicle(vehicleString: String?): Vehicle? {
        if (vehicleString == null) return null
        return gson.fromJson(vehicleString, Vehicle::class.java)
    }
}