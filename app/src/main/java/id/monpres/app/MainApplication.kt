package id.monpres.app

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.telephony.TelephonyManager
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import id.monpres.app.model.Service
import id.monpres.app.model.ServiceType
import id.monpres.app.notification.OrderServiceNotification
import id.monpres.app.notification.OrderServiceNotification.BASE_CHANNEL_ID
import id.monpres.app.notification.OrderServiceNotification.BASE_CHANNEL_NAME

@HiltAndroidApp
class MainApplication : Application() {

    companion object {
        private val TAG = MainApplication::class.java.simpleName

        // Region
        const val APP_REGION = "ID" // Default region
        var userRegion: String? = null

        // Services
        var serviceTypes: List<ServiceType>? = null // TODO: Use repository
        var services: List<Service>? = null  // TODO: Use repository

        const val adminWANumber = "6285166665655"
    }

    override fun onCreate() {
        super.onCreate()

        // Initialize the userRegion
        userRegion = getSimCountry(this)
        Log.d(TAG, "User region from SIM card: $userRegion")

        if (userRegion.isNullOrEmpty()) {
            userRegion = APP_REGION
            Log.d(TAG, "Falling back to default app region: $userRegion")
        }

        /* Notification */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            OrderServiceNotification.createNotificationChannel(this)
            OrderServiceNotification.createNotificationChannel(
                this,
                BASE_CHANNEL_ID, BASE_CHANNEL_NAME, importance = NotificationManager.IMPORTANCE_LOW
            )
        }

        serviceTypes = listOf(
            ServiceType(
                "internal",
                "Internal"
            ),
            ServiceType(
                "third_party",
                "Third Party"
            )
        )

        services = listOf(
            Service(
                "1",
                "internal",
                getString(R.string.quick_service),
                getString(R.string.fast_on_the_spot_maintenance_and_diagnostics_to_get_your_vehicle_running_smoothly_in_no_time),
                -1.0,
                false,
                true,
                imageUris = null,
                searchTokens = null,
                categoryId = "1",
                iconKey = "next_week_24px",
            ),
            Service(
                "2",
                "internal",
                getString(R.string.scheduled_service),
                getString(R.string.plan_comprehensive_maintenance_ahead_of_time_ensuring_thorough_care_at_your_convenience),
                -1.0,
                true,
                true,
                searchTokens = null,
                categoryId = "2",
                iconKey = "home_repair_service_24px",
            ),
//            Service(
//                "3",
//                "internal",
//                getString(R.string.component_replacement),
//                getString(R.string.swift_and_precise_exchange_of_worn_or_damaged_vehicle_parts_with_high_quality_originals),
//                -1.0,
//                true,
//                true,
//                imageUris = null,
//                searchTokens = null,
//                categoryId = "3"
//            ),
        )
    }

    /**
     * Gets the ISO country code from the SIM card.
     */
    private fun getSimCountry(context: Context): String? {
        val telephonyManager = context.getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        // Returns the ISO country code (e.g., "US", "ID") or null
        return telephonyManager.simCountryIso?.uppercase()
    }
}
