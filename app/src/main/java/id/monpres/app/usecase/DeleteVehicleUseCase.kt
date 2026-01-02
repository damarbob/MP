package id.monpres.app.usecase

import com.google.firebase.firestore.FirebaseFirestore
import id.monpres.app.model.Vehicle
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case for deleting a vehicle from Firestore.
 */
@Singleton
class DeleteVehicleUseCase @Inject constructor(private val firestore: FirebaseFirestore) {
    /**
     * Deletes a vehicle document from the Firestore collection.
     *
     * @param vehicleId The unique identifier of the vehicle to delete
     */
    suspend operator fun invoke(vehicleId: String) {
        val vehicleCollection = firestore.collection(Vehicle.COLLECTION)
        vehicleCollection.document(vehicleId).delete().await()
    }
}
