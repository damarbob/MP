package id.monpres.app.repository

import com.google.firebase.auth.FirebaseAuth
import com.mapbox.geojson.Point
import com.mapbox.turf.TurfConstants
import com.mapbox.turf.TurfMeasurement
import id.monpres.app.enums.PartnerCategory
import id.monpres.app.model.MontirPresisiUser
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PartnerRepository @Inject constructor(
    private val auth: FirebaseAuth
) : Repository<MontirPresisiUser>() {
    override fun onStart() {
        // TODO("Not yet implemented")
    }

    override fun onDestroy() {
        // TODO("Not yet implemented")
    }

    override fun createRecord(record: MontirPresisiUser) {
        // TODO("Not yet implemented")
    }

    // Clear cache when records change
    override fun onRecordAdded(record: MontirPresisiUser) {
        record.userId?.let { distanceCache.remove(it) }
    }

    override fun onRecordDeleted(record: MontirPresisiUser) {
        record.userId?.let { distanceCache.remove(it) }
    }

    override fun onRecordCleared() {
        distanceCache.clear()
    }

    fun getCurrentUserRecord(): MontirPresisiUser? {
        return getRecords().find { it.userId == auth.currentUser?.uid }
    }

    fun getRecordByUserId(userId: String): MontirPresisiUser? {
        return getRecords().find { it.userId == userId }
    }

    // Cache for distances to avoid recalculating
    private val distanceCache = mutableMapOf<String, Double>()
    private var currentUserLat: Double = 0.0
    private var currentUserLng: Double = 0.0

    fun setCurrentUserLocation(lat: Double, lng: Double) {
        currentUserLat = lat
        currentUserLng = lng
        distanceCache.clear() // Clear cache when location changes
    }

    fun getPartnersByDistance(): List<MontirPresisiUser> {
        return getRecords().sortedBy { calculateDistance(it) }
    }

    fun getPartnersWithDistance(): List<Pair<MontirPresisiUser, Double>> {
        return getRecords().map { partner ->
            partner to calculateDistance(partner)
        }.sortedBy { it.second }
    }

    fun getPartnersWithDistanceAndCategories(categories: List<PartnerCategory>): List<Pair<MontirPresisiUser, Double>> {
        return getRecords().filter { partner ->
            partner.partnerCategories?.any { it in categories.toSet() } ?: false
        }.map { partner ->
            partner to calculateDistance(partner)
        }.sortedBy { it.second }
    }


    fun getDistanceToPartner(partner: MontirPresisiUser): Double {
        return calculateDistance(partner)
    }

    private fun calculateDistance(partner: MontirPresisiUser): Double {
        val partnerId = partner.userId ?: return Double.MAX_VALUE

        // Return cached distance if available
        distanceCache[partnerId]?.let { return it }

        val partnerLat = partner.locationLat?.toDoubleOrNull() ?: return 41000.0
        val partnerLng = partner.locationLng?.toDoubleOrNull() ?: return 41000.0

        val distance = TurfMeasurement.distance(
            Point.fromLngLat(currentUserLat, currentUserLng),
            Point.fromLngLat(partnerLat, partnerLng),
            TurfConstants.UNIT_KILOMETERS
        )

//        val results = FloatArray(1)
//        Location.distanceBetween(currentUserLat, currentUserLng, partnerLat, partnerLng, results)
//        return results[0]

        // Cache the result
        distanceCache[partnerId] = distance
        return distance
//        distanceCache[partnerId] = results[0].toDouble() / 1000
//        return results[0].toDouble() / 1000
    }
}