package id.monpres.app.model
import android.os.Parcelable
import com.google.firebase.firestore.DocumentSnapshot
import kotlinx.parcelize.Parcelize

@Parcelize
data class Vehicle(
    var id: String? = null,
    var userId: String? = null,
    var name: String? = null,
    var registrationNumber: String? = null,
    var year: String? = null,
    var engineCapacity: String? = null,
    var transmission: String? = null,
    var seat: String? = null,
    var powerOutput: String? = null,
    var wheelDrive: String? = null,
    var powerSource: String? = null,
    var type: String? = null
) : Parcelable {

    companion object {
        // Convert Firestore DocumentSnapshot to Vehicle object
        fun fromDocumentSnapshot(document: DocumentSnapshot): Vehicle? {
            return document.toObject(Vehicle::class.java)?.apply {
                id = document.id
            }
        }

        // Generate sample list of vehicles
        fun getSampleList(): List<Vehicle> {
            return listOf(
                Vehicle(name = "Toyota Camry", year = "2020", transmission = "Automatic"),
                Vehicle(name = "Honda Civic", year = "2019", transmission = "Manual"),
                Vehicle(name = "Ford F-150", year = "2021", powerSource = "Gasoline"),
                Vehicle(name = "Tesla Model 3", powerSource = "Electric", seat = "5"),
                Vehicle(name = "BMW X5", engineCapacity = "3.0L", wheelDrive = "AWD"),
                Vehicle(name = "Hyundai Tucson", type = "SUV", registrationNumber = "ABC-123"),
                Vehicle(name = "Mercedes C-Class", powerOutput = "255 hp", year = "2022"),
                Vehicle(name = "Audi Q7", seat = "7", transmission = "Automatic"),
                Vehicle(name = "Nissan Rogue", wheelDrive = "FWD", year = "2020"),
                Vehicle(name = "Subaru Outback", type = "Wagon", powerSource = "Hybrid"),
                Vehicle(name = "Subaru Outback", type = "Wagon", powerSource = "Hybrid"),
                Vehicle(name = "Subaru Outback", type = "Wagon", powerSource = "Hybrid"),
                Vehicle(name = "Subaru Outback", type = "Wagon", powerSource = "Hybrid"),
                Vehicle(name = "Subaru Outback", type = "Wagon", powerSource = "Hybrid"),
                Vehicle(name = "Subaru Outback", type = "Wagon", powerSource = "Hybrid"),
                Vehicle(name = "Subaru Outback", type = "Wagon", powerSource = "Hybrid"),
                Vehicle(name = "Subaru Outback", type = "Wagon", powerSource = "Hybrid"),
                Vehicle(name = "Subaru Outback", type = "Wagon", powerSource = "Hybrid"),
                Vehicle(name = "Subaru Outback", type = "Wagon", powerSource = "Hybrid"),
                Vehicle(name = "Subaru Outback", type = "Wagon", powerSource = "Hybrid"),
                Vehicle(name = "Subaru Outback", type = "Wagon", powerSource = "Hybrid"),
                Vehicle(name = "Subaru Outback", type = "Wagon", powerSource = "Hybrid"),
                Vehicle(name = "Subaru Outback", type = "Wagon", powerSource = "Hybrid"),
                Vehicle(name = "Subaru Outback", type = "Wagon", powerSource = "Hybrid"),
                Vehicle(name = "Subaru Outback", type = "Wagon", powerSource = "Hybrid"),
                Vehicle(name = "Subaru Outback", type = "Wagon", powerSource = "Hybrid"),
                Vehicle(name = "Subaru Outback", type = "Wagon", powerSource = "Hybrid"),
                Vehicle(name = "Subaru Outback", type = "Wagon", powerSource = "Hybrid"),
                Vehicle(name = "Subaru Outback", type = "Wagon", powerSource = "Hybrid"),
                Vehicle(name = "Subaru Outback", type = "Wagon", powerSource = "Hybrid"),
                Vehicle(name = "Subaru Outback", type = "Wagon", powerSource = "Hybrid"),
                Vehicle(name = "lala Outback", type = "Wagon", powerSource = "Hybrid"),
            )
        }
    }

    // Convert Vehicle to Firestore compatible map
    fun toFirestoreMap(): Map<String, Any?> {
        return mapOf(
            "userId" to userId,
            "name" to name,
            "registrationNumber" to registrationNumber,
            "year" to year,
            "engineCapacity" to engineCapacity,
            "transmission" to transmission,
            "seat" to seat,
            "powerOutput" to powerOutput,
            "wheelDrive" to wheelDrive,
            "powerSource" to powerSource,
            "type" to type
        )
    }
}
//
//import android.os.Parcel
//import android.os.Parcelable
//import com.google.firebase.firestore.DocumentSnapshot
//import kotlinx.parcelize.Parcelize
//
//@Parcelize
//data class Vehicle(
//    var id: String? = null,
//    var userId: String? = null,
//    var name: String? = null,
//    var registrationNumber: String? = null,
//    var year: String? = null,
//    var engineCapacity: String? = null,
//    var transmission: String? = null,
//    var seat: String? = null,
//    var powerOutput: String? = null,
//    var wheelDrive: String? = null,
//    var powerSource: String? = null,
//    var type: String? = null,
//): Parcelable {
//    constructor(parcel: Parcel) : this(
//        parcel.readString() ?: "",
//        parcel.readString() ?: "",
//        parcel.readString() ?: "",
//        parcel.readString() ?: "",
//        parcel.readString() ?: "",
//        parcel.readString() ?: "",
//        parcel.readString() ?: "",
//        parcel.readString() ?: "",
//        parcel.readString() ?: "",
//        parcel.readString() ?: "",
//        parcel.readString() ?: "",
//        parcel.readString() ?: ""
//    ) {
//    }
//
//    fun toMap(): HashMap<String, Any?> {
//        return hashMapOf(
//            "id" to id,
//            "userId" to userId,
//            "name" to name,
//            "registrationNumber" to registrationNumber,
//            "year" to year,
//            "engineCapacity" to engineCapacity,
//            "transmission" to transmission,
//            "seat" to seat,
//            "powerOutput" to powerOutput,
//            "wheelDrive" to wheelDrive,
//            "powerSource" to powerSource,
//            "type" to type,
//        )
//    }
//
//    fun empty(): Vehicle {
//        return Vehicle()
//    }
//
//    fun Vehicle.fromSnapshot(snapshot: DocumentSnapshot): Vehicle {
//        val data = snapshot.data
//        if (data.isNullOrEmpty()) return empty()
//        return Vehicle(
//            id = snapshot.id,
//            userId = data["userId"] as? String ?: "",
//            name = data["name"] as? String ?: "",
//            registrationNumber = data["registrationNumber"] as? String ?: "",
//            year = data["year"] as? String ?: "",
//            engineCapacity = data["engineCapacity"] as? String ?: "",
//            transmission = data["transmission"] as? String ?: "",
//            seat = data["seat"] as? String ?: "",
//            powerOutput = data["powerOutput"] as? String ?: "",
//            wheelDrive = data["wheelDrive"] as? String ?: "",
//            powerSource = data["powerSource"] as? String ?: "",
//            type = data["type"] as? String ?: "",
//        )
//    }
//
//    fun exampleData(): List<Vehicle> {
//        return listOf(
//            Vehicle(
//                "asdf",
//                "asdf",
//                "AAA",
//                "asdf",
//                "asdf",
//                "asdf",
//                "asdf",
//                "asdf",
//                "asdf",
//                "asdf",
//                "asdf",
//                "asdf"
//            ),
//            Vehicle("Tuatara", "Tuatara", "Tuatara", "AB 1 SSR"),
//            Vehicle("Tuatara", "Tuatara", "Tuatara", "AB 1 SSR"),
//            Vehicle("Tuatara", "Tuatara", "Tuatara", "AB 1 SSR"),
//            Vehicle("Tuatara", "Tuatara", "Tuatara", "AB 1 SSR"),
//            Vehicle("Tuatara", "Tuatara", "Tuatara", "AB 1 SSR"),
//            Vehicle("Tuatara", "Tuatara", "Tuatara", "AB 1 SSR"),
//            Vehicle("Tuatara", "Tuatara", "Tuatara", "AB 1 SSR"),
//            Vehicle("Tuatara", "Tuatara", "Tuatara", "AB 1 SSR"),
//            Vehicle("Tuatara", "Tuatara", "Tuatara", "AB 1 SSR"),
//            Vehicle("Tuatara", "Tuatara", "Tuatara", "AB 1 SSR"),
//            Vehicle("Tuatara", "Tuatara", "Tuatara", "AB 1 SSR"),
//            Vehicle("Tuatara", "Tuatara", "Tuatara", "AB 1 SSR"),
//            Vehicle("Tuatara", "Tuatara", "Tuatara", "AB 1 SSR"),
//            Vehicle("Tuatara", "Tuatara", "Tuatara", "AB 1 SSR"),
//            Vehicle("Tuatara", "Tuatara", "Tuatara", "AB 1 SSR"),
//            Vehicle("Tuatara", "Tuatara", "Tuatara", "AB 1 SSR"),
//            Vehicle("Tuatara", "Tuatara", "Tuatara", "AB 1 SSR"),
//            Vehicle("Tuatara", "Tuatara", "Tuatara", "AB 1 SSR"),
//            Vehicle("Tuatara", "Tuatara", "Tuatara", "AB 1 SSR"),
//            Vehicle("Tuatara", "Tuatara", "Tuatara", "AB 1 SSR"),
//            Vehicle("Tuatara", "Tuatara", "Tuatara", "AB 1 SSR"),
//        )
//    }
//
//    override fun describeContents(): Int {
//        return 0
//    }
//
//    override fun writeToParcel(parcel: Parcel, flags: Int) {
//        parcel.writeString(id)
//        parcel.writeString(userId)
//        parcel.writeString(name)
//        parcel.writeString(registrationNumber)
//        parcel.writeString(year)
//        parcel.writeString(engineCapacity)
//        parcel.writeString(transmission)
//        parcel.writeString(seat)
//        parcel.writeString(powerOutput)
//        parcel.writeString(wheelDrive)
//        parcel.writeString(powerSource)
//        parcel.writeString(type)
//    }
//
//    companion object CREATOR : Parcelable.Creator<Vehicle> {
//        override fun createFromParcel(parcel: Parcel): Vehicle {
//            return Vehicle(parcel)
//        }
//
//        override fun newArray(size: Int): Array<Vehicle?> {
//            return arrayOfNulls(size)
//        }
//    }
//}