package id.monpres.app.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import id.monpres.app.model.LivePartnerLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LivePartnerLocationRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    /**
     * Updates the real-time location for a specific order.
     * This is a suspend function for a single write.
     */
    suspend fun updateLiveLocation(orderId: String, lat: Double, lng: Double, isFinalUpdate: Boolean = false) {
        val geoPoint = GeoPoint(lat, lng)
        val data = mutableMapOf<String, Any>("location" to geoPoint)
        if (isFinalUpdate) {
            data["isArrived"] = true
        }

        firestore.collection(LivePartnerLocation.COLLECTION).document(orderId)
            .set(data)
            .await() // Use kotlinx-coroutines-play-services
    }

    /**
     * Observes the real-time location for a specific order.
     * This returns a Flow that the customer will collect.
     */
    fun observeLiveLocation(orderId: String): Flow<LivePartnerLocation> =
        callbackFlow {
            val docRef = firestore.collection(LivePartnerLocation.COLLECTION).document(orderId)

            val listener = docRef.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    cancel("Error fetching live location", error)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val location = snapshot.toObject(LivePartnerLocation::class.java)
                    if (location != null) {
                        trySend(location).isSuccess
                    }
                }
            }
            // This is crucial: removes the listener when the Flow is cancelled.
            awaitClose { listener.remove() }
        }.flowOn(Dispatchers.IO)
}
