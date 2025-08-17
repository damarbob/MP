package id.monpres.app.usecase

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import id.monpres.app.model.Vehicle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetVehiclesByUserIdFlowUseCase @Inject constructor(private val firestore: FirebaseFirestore) {
    companion object {
        val TAG = GetVehiclesByUserIdFlowUseCase::class.simpleName
    }
    operator fun invoke(userId: String): Flow<List<Vehicle>> = callbackFlow {
        val listenerRegistration = firestore
            .collection(Vehicle.COLLECTION)
            .whereEqualTo(Vehicle.FIELD_USER_ID, userId)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    cancel("Error fetching vehicles", e)
                    Log.e(TAG, "Error fetching vehicles", e)
                    return@addSnapshotListener
                }
                val vehicles = snapshots?.documents?.mapNotNull { it.toObject(Vehicle::class.java) } ?: emptyList()
                trySend(vehicles).isSuccess // Or offer(vehicles)
            }
        awaitClose { listenerRegistration.remove() }
    }.flowOn(Dispatchers.IO)
}