package id.monpres.app.usecase

import com.google.firebase.firestore.FirebaseFirestore
import id.monpres.app.model.Vehicle
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case for creating or updating vehicle records in Firestore.
 *
 * Handles ID generation if not provided.
 */
@Singleton
class InsertVehicleUseCase @Inject constructor(private val firestore: FirebaseFirestore) {
    /**
     * Inserts or updates a vehicle in Firestore.
     *
     * @param vehicle Vehicle object to insert (generates ID if empty)
     * @return The inserted vehicle with its final ID
     */
    suspend operator fun invoke(vehicle: Vehicle): Vehicle {

        val vehicleCollection = firestore.collection(Vehicle.COLLECTION)
        val vehicleToInsert: Vehicle
        val documentReference = if (vehicle.id.isNotEmpty()) {
            // Use provided ID
            vehicleToInsert = vehicle
            vehicleCollection.document(vehicle.id)
        } else {
            // Firestore generates ID, then we update our object if needed
            val newDocRef = vehicleCollection.document() // Auto-generates ID path
            vehicleToInsert = vehicle.copy(id = newDocRef.id) // Update vehicle with generated ID
            newDocRef
        }
        documentReference.set(vehicleToInsert).await()
        return vehicleToInsert
    }
}
