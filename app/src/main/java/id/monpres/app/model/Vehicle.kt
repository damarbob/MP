package id.monpres.app.model

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

/**
 * Represents a vehicle entity.
 *
 * This data class is used to store information about a vehicle, such as its ID, user ID, type ID,
 * name, model, registration number, license plate number, year, engine capacity, transmission,
 * seat count, power output, wheel drive, power source, and active status.
 *
 * It is annotated with `@Entity` to be used with Room database and `@Parcelize` to be parcelable.
 *
 * @property id The unique ID of the vehicle.
 * @property userId The ID of the user who owns the vehicle.
 * @property typeId The ID of the vehicle type.
 * @property name The name of the vehicle.
 * @property model The model of the vehicle.
 * @property registrationNumber The registration number of the vehicle.
 * @property licensePlateNumber The license plate number of the vehicle.
 * @property year The manufacturing year of the vehicle.
 * @property engineCapacity The engine capacity of the vehicle.
 * @property transmission The transmission type of the vehicle.
 * @property seat The number of seats in the vehicle.
 * @property powerOutput The power output of the vehicle.
 * @property wheelDrive The wheel drive type of the vehicle.
 * @property powerSource The power source of the vehicle (e.g., gasoline, electric).
 * @property active Indicates whether the vehicle is active or not.
 */
@Entity(tableName = "vehicles")
@Parcelize
data class Vehicle(
    @PrimaryKey
    var id: String = "",
    var userId: String? = null,
    var typeId: String? = null,
    var name: String? = null,
    var model: String? = null,
    var registrationNumber: String? = null,
    var licensePlateNumber: String? = null,
    var year: String? = null,
    var engineCapacity: String? = null,
    var transmission: String? = null,
    var seat: String? = null,
    var powerOutput: String? = null,
    var wheelDrive: String? = null,
    var powerSource: String? = null,

    var active: Boolean = true
) : Parcelable {

    companion object {
        const val COLLECTION = "vehicles"
        const val FIELD_ID = "id"
        const val FIELD_USER_ID = "userId"
    }

    // Room will use this constructor, ignore others
    @Ignore
    constructor(
        id: String,
        userId: String,
        typeId: String,
        name: String,
        model: String,
        registrationNumber: String,
        licensePlateNumber: String,
        year: String,
        engineCapacity: String,
        transmission: String,
        seat: String,
        powerOutput: String,
        wheelDrive: String,
        powerSource: String
    ) : this(
        id,
        userId,
        typeId,
        name,
        model,
        registrationNumber,
        licensePlateNumber,
        year,
        engineCapacity,
        transmission,
        seat,
        powerOutput,
        wheelDrive,
        powerSource,
        true,
    )

    // For firestore serialization
    @Ignore
    constructor() : this(
        "",
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        true
    )
}