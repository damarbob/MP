package id.monpres.app.model

import com.google.firebase.firestore.GeoPoint

data class LivePartnerLocation(
    val location: GeoPoint? = null,
    val isArrived: Boolean = false
) {
    companion object {
        const val COLLECTION = "livePartnerLocation"
    }
}
