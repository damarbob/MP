package id.monpres.app.usecase

import com.google.firebase.firestore.FirebaseFirestore
import id.monpres.app.model.Vehicle
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InsertVehicleUseCase @Inject constructor(private val firestore: FirebaseFirestore) {
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