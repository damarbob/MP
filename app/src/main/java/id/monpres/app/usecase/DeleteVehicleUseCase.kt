package id.monpres.app.usecase

import com.google.firebase.firestore.FirebaseFirestore
import id.monpres.app.model.Vehicle
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeleteVehicleUseCase @Inject constructor(private val firestore: FirebaseFirestore) {
    suspend operator fun invoke(vehicleId: String) {
        val vehicleCollection = firestore.collection(Vehicle.COLLECTION)
        vehicleCollection.document(vehicleId).delete().await()
    }
}