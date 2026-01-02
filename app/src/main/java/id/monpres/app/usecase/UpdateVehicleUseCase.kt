package id.monpres.app.usecase

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import id.monpres.app.model.Vehicle
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case for updating vehicle documents in Firestore with merge behavior.
 */
@Singleton
class UpdateVehicleUseCase @Inject constructor(private val firestore: FirebaseFirestore) {
    /**
     * Updates a vehicle document in Firestore.
     *
     * @param vehicle Vehicle object with updated data
     */
    suspend operator fun invoke(vehicle: Vehicle) {
        val vehicleCollection = firestore.collection(Vehicle.COLLECTION)
        vehicleCollection.document(vehicle.id).set(vehicle, SetOptions.merge()).await()
    }
}