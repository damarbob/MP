package id.monpres.app.usecase

import com.google.firebase.firestore.FirebaseFirestore
import id.monpres.app.model.Vehicle
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetVehiclesByUserIdUseCase @Inject constructor(private val firestore: FirebaseFirestore) {
    suspend operator fun invoke(userId: String): List<Vehicle>? {

        val vehicleCollection = firestore.collection(Vehicle.COLLECTION)
        return try {
            vehicleCollection.whereEqualTo(Vehicle.FIELD_USER_ID, userId).get().await().toObjects(Vehicle::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}