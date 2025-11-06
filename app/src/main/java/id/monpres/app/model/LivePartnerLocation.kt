package id.monpres.app.model

import com.google.firebase.firestore.GeoPoint

data class LivePartnerLocation(
    val location: GeoPoint? = null
) {
    companion object {
        const val COLLECTION = "livePartnerLocation"
    }
}
