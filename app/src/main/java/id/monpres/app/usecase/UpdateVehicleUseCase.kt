package id.monpres.app.usecase

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import id.monpres.app.model.Vehicle
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateVehicleUseCase @Inject constructor(private val firestore: FirebaseFirestore) {
    suspend operator fun invoke(vehicle: Vehicle) {
        val vehicleCollection = firestore.collection(Vehicle.COLLECTION)
        vehicleCollection.document(vehicle.id).set(vehicle, SetOptions.merge()).await()
    }

}