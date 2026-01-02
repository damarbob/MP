package id.monpres.app.usecase

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import id.monpres.app.R

/**
 * Use case for launching Google Maps with a specific location.
 */
class GoogleMapsIntentUseCase {

    /**
     * Opens Google Maps app with the specified location.
     *
     * @param context Android context for launching the intent
     * @param latitude Location latitude
     * @param longitude Location longitude
     * @param label Optional label for the location marker
     */
    operator fun invoke(context: Context, latitude: Double, longitude: Double, label: String? = null) {
        val locationLabel = label ?: context.getString(R.string.order_location)
        
        val gmmIntentUri = "geo:0,0?q=$latitude,$longitude($locationLabel)".toUri()
        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
        mapIntent.setPackage("com.google.android.apps.maps")
        context.startActivity(mapIntent)
    }
}
