package id.monpres.app.service

import android.Manifest
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.Timestamp
import com.google.firebase.firestore.GeoPoint
import dagger.hilt.android.AndroidEntryPoint
import id.monpres.app.R
import id.monpres.app.enums.OrderStatus
import id.monpres.app.enums.UserRole
import id.monpres.app.model.OrderService
import id.monpres.app.notification.OrderServiceNotification
import id.monpres.app.repository.LivePartnerLocationRepository
import id.monpres.app.repository.OrderServiceRepository
import id.monpres.app.repository.UserRepository
import id.monpres.app.usecase.NumberFormatterUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.absoluteValue

@AndroidEntryPoint
class OrderServiceLocationTrackingService : Service() {
    companion object {
        private val TAG = OrderServiceLocationTrackingService::class.simpleName
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_STOP_ONE = "ACTION_STOP_ONE"

        const val EXTRA_ORDER_ID = "EXTRA_ORDER_ID"
        const val EXTRA_MODE = "EXTRA_MODE"
        const val EXTRA_ORDER_SERVICE = "EXTRA_ORDER_SERVICE"

        const val MODE_PARTNER = "MODE_PARTNER"
        const val MODE_CUSTOMER = "MODE_CUSTOMER"


        private const val UPDATE_INTERVAL_SECOND = 60L
        private const val MIN_UPDATE_DISTANCE_METER = 20f
        private const val FOREGROUND_SERVICE_ID: Int = 12341234
    }


    @Inject
    lateinit var livePartnerLocationRepository: LivePartnerLocationRepository

    @Inject
    lateinit var userRepository: UserRepository
    @Inject
    lateinit var orderServiceRepository: OrderServiceRepository

    private val numberFormatterUseCase = NumberFormatterUseCase()

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Use a ConcurrentHashMap for thread-safe access to the jobs map.
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val activeLocationCallbacks = ConcurrentHashMap<String, LocationCallback>()

    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient


    override fun onCreate() {
        super.onCreate()
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val orderId = intent?.getStringExtra(EXTRA_ORDER_ID)

        when (intent?.action) {
            ACTION_START -> {
                if (orderId == null) return START_NOT_STICKY

                // Check for location permissions before proceeding.
                val hasPermission = (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) &&
                        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                if (!hasPermission) {
                    Log.e(TAG, "Cannot start service: Location permission not granted.")
                    stopSelf()
                    return START_NOT_STICKY
                }

                // If already tracking this specific order, do nothing.
                if (activeJobs.containsKey(orderId)) {
                    Log.d(TAG, "Already tracking order $orderId. Ignoring redundant start command.")
                    return START_NOT_STICKY
                }

                val mode = intent.getStringExtra(EXTRA_MODE)
                val orderService = getOrderServiceFromIntent(intent)

                if (orderService == null || mode == null) {
                    Log.e(TAG, "Cannot start tracking for $orderId without OrderService or mode.")
                    return START_NOT_STICKY
                }

                // Start the service in the foreground if it isn't already.
                // This uses a generic notification. Each job will post its own specific one.
                if (activeJobs.isEmpty()) {
                    Log.d(TAG, "No active jobs. Starting service in foreground.")
                    val baseNotification = OrderServiceNotification.createBaseNotification(this)
                    startForeground(FOREGROUND_SERVICE_ID, baseNotification)
                }

                // --- CREATE AND STORE A NEW JOB FOR THIS ORDER ---
                val newJob = when (mode) {
                    MODE_PARTNER -> startPartnerLocationUpdates(orderService)
                    MODE_CUSTOMER -> startCustomerLocationObserver(orderService)
                    else -> null
                }
                newJob?.let { activeJobs[orderId] = it }
            }

            ACTION_STOP_ONE -> {
                if (orderId != null) {
                    Log.d(TAG, "Received request to stop tracking for order: $orderId")
                    stopTrackingForOrder(orderId)
                }
            }

            ACTION_STOP -> {
                Log.d(TAG, "ACTION_STOP received. Stopping all tracking and service.")
                // Stop all individual jobs first.
                activeJobs.keys.forEach { id -> stopTrackingForOrder(id) }
                // Then stop the service itself.
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun getOrderServiceFromIntent(intent: Intent): OrderService? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_ORDER_SERVICE, OrderService::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_ORDER_SERVICE)
        }
    }

    // --- This function now returns a Job ---
    private fun startPartnerLocationUpdates(orderService: OrderService): Job? {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Partner mode: Location permission not granted.")
            return null // Cannot start job
        }

        val orderId = orderService.id!!
        val locationRequest = buildLocationRequest()

        // Create a new LocationCallback for this specific order
        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                // This callback continues to run, but its parent job can be cancelled.
                val location = locationResult.lastLocation ?: return
                // Launch a fire-and-forget coroutine within the service scope for the update.
                serviceScope.launch {
                    updatePartnerState(orderId, location, orderService)
                }
            }
        }
        activeLocationCallbacks[orderId] = locationCallback

        fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        Log.d(TAG, "Started location updates for partner, order: $orderId")

        // Return a job that does nothing but can be used as a handle in our map.
        // The real cleanup happens when we remove the LocationCallback.
        return Job().also { it.invokeOnCompletion {
            Log.d(TAG, "Partner job for $orderId completed/cancelled.")
        }}
    }

    // This function now returns a Job
    private fun startCustomerLocationObserver(orderService: OrderService): Job {
        val orderId = orderService.id!!
        Log.d(TAG, "Starting Firestore observer for customer, order: $orderId")

        // Launch a new coroutine for this specific order's observer
        return serviceScope.launch {
            val initialDistance = getInitialDistance(orderService)
            val currentUserRole = userRepository.userRecord.filterNotNull().first().role ?: UserRole.CUSTOMER

            livePartnerLocationRepository.observeLiveLocation(orderId)
                .onEach { livePartnerLocation ->
                    Log.d(TAG, "Received live location for $orderId: $livePartnerLocation")
                    val partnerGeoPoint = livePartnerLocation.location ?: return@onEach
                    val currentDistance = getCurrentDistance(partnerGeoPoint, orderService)
                    val progress = calculateProgress(initialDistance, currentDistance)
                    Log.d(TAG, "Current distance for $orderId: $currentDistance")
                    Log.d(TAG, "Initial distance for $orderId: $initialDistance")
                    Log.d(TAG, "Progress for $orderId: $progress")
                    OrderServiceNotification.showOrUpdateNotification(
                        this@OrderServiceLocationTrackingService, orderId, OrderStatus.ON_THE_WAY,
                        Timestamp.now(), currentUserRole, progress,
                        shortCriticalText = getString(R.string.x_distance_m, numberFormatterUseCase(currentDistance))
                    )
                }
                .catch { e -> Log.e(TAG, "Error observing partner location for $orderId", e) }
                .collect()
        }
    }

    private suspend fun updatePartnerState(orderId: String, location: Location, orderService: OrderService) {

        try {
            livePartnerLocationRepository.updateLiveLocation(orderId, location.latitude, location.longitude)
            updatePartnerNotification(orderService,location)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update live location for $orderId", e)
        }
    }

    private suspend fun updatePartnerNotification(orderService: OrderService, currentLocation: Location) {
        val targetLocation = Location("target").apply {
            latitude = orderService.selectedLocationLat ?: 0.0
            longitude = orderService.selectedLocationLng ?: 0.0
        }
        val currentDistance = currentLocation.distanceTo(targetLocation)
        val initialDistance = getInitialDistance(orderService)
        val progress = calculateProgress(initialDistance, currentDistance)
        val currentUserRole = userRepository.userRecord.filterNotNull().first().role ?: UserRole.CUSTOMER
        val notification = OrderServiceNotification.showOrUpdateNotification(
            this@OrderServiceLocationTrackingService, orderService.id!!, OrderStatus.ON_THE_WAY,
            Timestamp.now(), currentUserRole, progress,
            shortCriticalText = getString(R.string.x_distance_m, numberFormatterUseCase(currentDistance))
        )
//        startForeground(getNotificationId(orderService.id!!), notification)
    }

    private fun stopTrackingForOrder(orderId: String) {
        // 1. Cancel and remove the coroutine job
        activeJobs[orderId]?.cancel()
        activeJobs.remove(orderId)

        // 2. Remove and stop the location callback for partners
        activeLocationCallbacks[orderId]?.let { callback ->
            fusedLocationProviderClient.removeLocationUpdates(callback)
            activeLocationCallbacks.remove(orderId)
            Log.d(TAG, "Removed location callback for order: $orderId")
        }

        // 3. Dismiss the specific notification for this order
        OrderServiceNotification.cancelNotification(this, orderId)
        Log.d(TAG, "Stopped tracking and cleaned up for order: $orderId")
        stopForeground(STOP_FOREGROUND_REMOVE)

        // 4. If this was the last active job, stop the service itself.
        if (activeJobs.isEmpty()) {
            Log.d(TAG, "No more active jobs. Stopping foreground service.")
            stopSelf()
        }
    }

    private fun buildLocationRequest(): com.google.android.gms.location.LocationRequest {
        val priority = if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
            Priority.PRIORITY_HIGH_ACCURACY
        else
            Priority.PRIORITY_BALANCED_POWER_ACCURACY

        return com.google.android.gms.location.LocationRequest.Builder(priority, TimeUnit.SECONDS.toMillis(UPDATE_INTERVAL_SECOND))
            .apply {
                setMinUpdateDistanceMeters(MIN_UPDATE_DISTANCE_METER)
                setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
                setWaitForAccurateLocation(true)
            }.build()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy: Service is being destroyed. Final cleanup.")
        // Fallback cleanup in case anything was missed
        activeJobs.keys.forEach { orderId -> stopTrackingForOrder(orderId) }
        serviceScope.cancel()
    }

    // --- Calculation Helper Functions ---

    private fun getInitialDistance(orderService: OrderService): Float {
        // Use the partner's location from the OrderService document as the starting point.
        val partner = orderService.partner ?: return 0f
        val initialPartnerLoc = Location("initialPartner").apply {
            latitude = partner.locationLat?.toDoubleOrNull() ?: 0.0
            longitude = partner.locationLng?.toDoubleOrNull() ?: 0.0
        }
        val targetLoc = Location("target").apply {
            latitude = orderService.selectedLocationLat ?: 0.0
            longitude = orderService.selectedLocationLng ?: 0.0
        }
        return initialPartnerLoc.distanceTo(targetLoc)
    }

    private fun getCurrentDistance(partnerGeoPoint: GeoPoint, orderService: OrderService): Float {
        val partnerLoc = Location("partner").apply {
            latitude = partnerGeoPoint.latitude
            longitude = partnerGeoPoint.longitude
        }
        val targetLoc = Location("target").apply {
            latitude = orderService.selectedLocationLat ?: 0.0
            longitude = orderService.selectedLocationLng ?: 0.0
        }
        return partnerLoc.distanceTo(targetLoc)
    }

    private fun calculateProgress(initialDistance: Float, currentDistance: Float): Int {
        return if (initialDistance > 0) {
            ((initialDistance - currentDistance) / initialDistance * 100).toInt().coerceIn(0, 100)
        } else {
            0
        }
    }

    private fun getNotificationId(orderId: String): Int {
        return orderId.hashCode().absoluteValue
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}