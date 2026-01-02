package id.monpres.app.usecase

import com.google.firebase.firestore.FirebaseFirestore
import id.monpres.app.model.Vehicle
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case for retrieving a single vehicle by its ID from Firestore.
 */
@Singleton
class GetVehicleByIdUseCase @Inject constructor(private val firestore: FirebaseFirestore) {
    /**
     * Retrieves a vehicle by its ID.
     *
     * @param vehicleId The unique identifier of the vehicle
     * @return Vehicle object if found, null otherwise
     */
    suspend operator fun invoke(vehicleId: String): Vehicle? {
        val vehiclesCollection = firestore.collection(Vehicle.COLLECTION)
        return vehiclesCollection.document(vehicleId).get().await().toObject(Vehicle::class.java)
    }
}