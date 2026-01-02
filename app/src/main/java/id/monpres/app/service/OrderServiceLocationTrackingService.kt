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
        private const val ARRIVAL_THRESHOLD_METERS = 20f
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

    // This map will now hold the full OrderService object for partners
    private val activePartnerJobs = ConcurrentHashMap<String, OrderService>()
    // Use a ConcurrentHashMap for thread-safe access to the jobs map.
    private val activeCustomerJobs = ConcurrentHashMap<String, Job>()

    private var locationCallback: LocationCallback? = null

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
                val hasPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                if (!hasPermission) {
                    Log.e(TAG, "Cannot start service: Location permission not granted.")
                    stopSelf()
                    return START_NOT_STICKY
                }

                // If already tracking this specific order, do nothing.
                if (activePartnerJobs.containsKey(orderId) || activeCustomerJobs.containsKey(orderId)) {
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
                if (activePartnerJobs.isEmpty() && activeCustomerJobs.isEmpty()) {
                    Log.d(TAG, "No active jobs. Starting service in foreground.")
                    val baseNotification = OrderServiceNotification.createBaseNotification(this)
                    startForeground(FOREGROUND_SERVICE_ID, baseNotification)
                }

                when (mode) {
                    MODE_PARTNER -> {
                        // Store the order and start the single location provider if needed
                        activePartnerJobs[orderId] = orderService
                        startPartnerLocationUpdates()
                    }
                    MODE_CUSTOMER -> {
                        // Customer mode still uses an individual job for Firestore observation
                        val customerJob = startCustomerLocationObserver(orderService)
                        activeCustomerJobs[orderId] = customerJob
                    }
                }
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
                activePartnerJobs.keys.forEach { id -> stopTrackingForOrder(id) }
                activeCustomerJobs.keys.forEach { id -> activeCustomerJobs[id]?.cancel() }
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
    private fun startPartnerLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Partner mode: Location permission not granted.")
            return // Cannot start job
        }

        if (locationCallback != null) {
            Log.e(TAG, "Partner mode: Location callback already exists.")
            return // Cannot start job
        }

        val locationRequest = buildLocationRequest()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                // This callback continues to run, but its parent job can be cancelled.
                val location = locationResult.lastLocation ?: return
                // When location is received, process it for ALL active partner orders.
                processLocationUpdateForPartners(location)
            }
        }

        fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback!!, Looper.getMainLooper())
    }

    // Processes a location update for all active partners
    private fun processLocationUpdateForPartners(location: Location) {
        if (activePartnerJobs.isEmpty()) return

        Log.d(TAG, "Processing location update for ${activePartnerJobs.size} partner jobs.")
        // Create a thread-safe copy of the keys to iterate over, to avoid ConcurrentModificationException
        val orderIds = activePartnerJobs.keys.toList()

        for (orderId in orderIds) {
            val orderService = activePartnerJobs[orderId] ?: continue

            // Launch a separate coroutine for each update to avoid blocking the loop
            serviceScope.launch {
                // Check distance and decide if the partner has arrived
                val destination = Location("destination").apply {
                    latitude = orderService.selectedLocationLat ?: 0.0
                    longitude = orderService.selectedLocationLng ?: 0.0
                }
                val distanceToDestination = location.distanceTo(destination)

                // Update Firestore and notification
                updatePartnerState(orderId, location, orderService, distanceToDestination)

                // --- SMART ARRIVAL LOGIC ---
                if (distanceToDestination < ARRIVAL_THRESHOLD_METERS) {
                    Log.i(TAG, "Partner has arrived for order $orderId (distance: $distanceToDestination m). Performing final update and stopping tracking for this order.")
                    // Perform one last guaranteed update
                    updatePartnerState(orderId, location, orderService, distanceToDestination, isFinalUpdate = true)
                    // Stop tracking for this specific order
                    stopTrackingForOrder(orderId)
                }
            }
        }
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

                    if (livePartnerLocation.isArrived) {
                        Log.i(TAG, "Customer mode: Partner has arrived signal received. Stopping updates for $orderId.")
                        // Show a final "Arrived" notification
                        OrderServiceNotification.showOrUpdateNotification(
                            this@OrderServiceLocationTrackingService, orderId, OrderStatus.ON_THE_WAY,
                            Timestamp.now(), currentUserRole, 100, // 100% progress
                            shortCriticalText = getString(R.string.partner_has_arrived)
                        )
                        // Stop this specific customer observer job
                        this.cancel() // This cancels the coroutine started by serviceScope.launch
                        return@onEach
                    }

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

    private suspend fun updatePartnerState(orderId: String, location: Location, orderService: OrderService, distance: Float, isFinalUpdate: Boolean = false) {
        try {
            // Update firestore
            livePartnerLocationRepository.updateLiveLocation(orderId, location.latitude, location.longitude, isFinalUpdate)
            // Update local notification
            updatePartnerNotification(orderService, distance)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update live location for $orderId", e)
        }
    }

    // --- MODIFIED: Simplified to just take distance ---
    private suspend fun updatePartnerNotification(orderService: OrderService, currentDistance: Float) {
        val initialDistance = getInitialDistance(orderService)
        val progress = calculateProgress(initialDistance, currentDistance)
        val currentUserRole = userRepository.userRecord.filterNotNull().first().role ?: UserRole.CUSTOMER

        OrderServiceNotification.showOrUpdateNotification(
            this, orderService.id!!, OrderStatus.ON_THE_WAY,
            Timestamp.now(), currentUserRole, progress,
            shortCriticalText = getString(R.string.x_distance_m, numberFormatterUseCase(currentDistance.toDouble()))
        )
    }

    private fun stopTrackingForOrder(orderId: String) {
        // Remove from the correct map
        activePartnerJobs.remove(orderId)
        activeCustomerJobs[orderId]?.cancel()
        activeCustomerJobs.remove(orderId)

        // Dismiss the specific notification for this order
        OrderServiceNotification.cancelNotification(this, orderId)
        Log.d(TAG, "Stopped tracking and cleaned up for order: $orderId")

        // If this was the last active job of ANY kind, stop the location provider and the service
        if (activePartnerJobs.isEmpty() && activeCustomerJobs.isEmpty()) {
            Log.i(TAG, "No more active jobs. Stopping foreground service and all location updates.")
            locationCallback?.let {
                fusedLocationProviderClient.removeLocationUpdates(it)
                locationCallback = null
                Log.d(TAG, "Removed single location callback.")
            }
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
        activePartnerJobs.keys.forEach { orderId -> stopTrackingForOrder(orderId) }
        activeCustomerJobs.keys.forEach { orderId -> stopTrackingForOrder(orderId) }
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
