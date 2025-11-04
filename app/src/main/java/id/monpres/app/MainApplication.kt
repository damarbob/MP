package id.monpres.app

import android.app.Application
import android.os.Build
import dagger.hilt.android.HiltAndroidApp
import id.monpres.app.model.Service
import id.monpres.app.model.ServiceType
import id.monpres.app.notification.OrderServiceNotification

@HiltAndroidApp
class MainApplication : Application() {

    companion object {
        private val TAG = MainApplication::class.java.simpleName

        // Services
        var serviceTypes: List<ServiceType>? = null // TODO: Use repository
        var services: List<Service>? = null  // TODO: Use repository
    }

    override fun onCreate() {
        super.onCreate()

        /* Notification */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            OrderServiceNotification.createNotificationChannel(this)
        }

//        DynamicColors.applyToActivitiesIfAvailable(this)

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
                categoryId = "1"
            ),
            Service(
                "2",
                "internal",
                getString(R.string.scheduled_service),
                getString(R.string.plan_comprehensive_maintenance_ahead_of_time_ensuring_thorough_care_at_your_convenience),
                -1.0,
                true,
                true,
                imageUris = null,
                searchTokens = null,
                categoryId = "2"
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
}