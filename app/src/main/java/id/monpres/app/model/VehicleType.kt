package id.monpres.app.model

import android.os.Parcelable
import com.google.firebase.firestore.DocumentSnapshot
import kotlinx.parcelize.Parcelize

@Parcelize
data class VehicleType(
    var id: String? = null,
    var name: String? = null,
) : Parcelable {
    companion object {
        // Convert Firestore DocumentSnapshot to VehicleType object
        fun fromDocumentSnapshot(document: DocumentSnapshot): VehicleType? {
            return document.toObject(VehicleType::class.java)?.apply {
                id = document.id
            }
        }

        // Generate sample list of vehicles
        fun getSampleList(): List<VehicleType> {
            return listOf(
                VehicleType("CAR", "Car"),
                VehicleType("MOTORCYCLE", "Motorcycle"),
                VehicleType("HELICOPTER", "Helicopter"),
            )
        }
    }

    // Convert VehicleType to Firestore compatible map
    fun toFirestoreMap(): Map<String, Any?> {
        return mapOf(
            "name" to name,
        )
    }
}

