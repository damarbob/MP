package id.monpres.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mapbox.geojson.Point
import com.mapbox.maps.MapLoaded
import com.mapbox.maps.MapLoadedCallback
import com.mapbox.maps.MapView
import com.mapbox.maps.dsl.cameraOptions
import com.mapbox.maps.plugin.PuckBearing
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotation
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.gestures.addOnMapClickListener
import com.mapbox.maps.plugin.locationcomponent.createDefault2DPuck
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.maps.plugin.viewport.viewport
import dev.androidbroadcast.vbpd.viewBinding
import id.monpres.app.databinding.ActivityMapsBinding
import id.monpres.app.model.MapsActivityExtraData

class MapsActivity : AppCompatActivity(R.layout.activity_maps), MapLoadedCallback {
    companion object {
        private val TAG = MapsActivity::class.java.simpleName
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }

    /* Configs */
    private var pickMode = false
    private var pointJsons: List<String?> = emptyList()

    /* Resources */
    private val redMarkerBitmap: Bitmap by lazy {
        return@lazy BitmapFactory
            .decodeResource(resources, R.drawable.mp_marker)
    }

    /* Variables */
    private lateinit var pointAnnotationManager: PointAnnotationManager
    private var selectedLocationPoint: Point? = null
    private var userLocationPoint: Point? = null
    private var annotation: PointAnnotation? = null

    /* Views */
    private val binding by viewBinding(ActivityMapsBinding::bind)

    // Permission request launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        if (granted) {
            Log.d(TAG, "Location permission granted")
            enableLocationFeatures()
        } else {
            Log.w(TAG, "Location permission denied")
            // Handle permission denial (optional: show message, disable features)
        }
    }

    private lateinit var mapView: MapView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "MapsActivity initialized")

        enableEdgeToEdge()

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        /* Maps */
        mapView = binding.mapsView

        val annotationApi = mapView.annotations
        pointAnnotationManager = annotationApi.createPointAnnotationManager()

        // Initial map setup
        mapView.mapboxMap.apply {
            setCamera(
                cameraOptions {
                    center(
                        Point.fromLngLat(
                            -98.0, 39.5
                        )
                    )
                    zoom(12.0)
                }
            )
            subscribeMapLoaded(this@MapsActivity)
        }

        mapView.mapboxMap.loadStyle("mapbox://styles/mapbox/streets-v12")

        // Check permissions and setup location
        if (hasLocationPermission()) {
            Log.d(TAG, "Location permissions already granted")
            enableLocationFeatures()
        } else {
            Log.d(TAG, "Requesting location permissions")
            requestPermissionLauncher.launch(REQUIRED_PERMISSIONS)
        }

        /* End maps */

        /* Configs */

        // Check if we’re in “pick mode”
        pickMode = intent.getBooleanExtra(MapsActivityExtraData.EXTRA_PICK_MODE, false)
//        pickMode = false
        if (pickMode) {
            enableTapToSelect()
        }

        val points = intent.getStringArrayListExtra("points")
        Log.d(TAG, "Received points: $points")
        if (points != null) {
            pointJsons = points
        }

        /* Setup UI */

        // Hide submit location FAB if not pick mode
        if (!pickMode) binding.fabMapsSubmitLocation.visibility = View.GONE

        // Add points to map
        pointJsons.forEach { pointJson ->
            Log.d(TAG, "Point JSON: $pointJson")
            if (pointJson == null) return@forEach
            val point = Point.fromJson(pointJson)
            val pointAnnotationOptions: PointAnnotationOptions = PointAnnotationOptions()
                .withPoint(point)
                .withIconImage(redMarkerBitmap)
                // Make the annotation draggable.
                .withDraggable(false)

            // Add the draggable pointAnnotation to the map.
            annotation = pointAnnotationManager.create(pointAnnotationOptions)
        }

        /* Listeners */
        Log.d(TAG, "Pick mode: $pickMode")
        binding.fabMapsSubmitLocation.setOnClickListener { _ ->

            Log.d(TAG, "Pick mode: $pickMode")

            // Return if not in pick mode
            if (!pickMode) return@setOnClickListener

            Log.d(TAG, "Submitting location")

            submitLocation()
        }
    }

    private fun hasLocationPermission(): Boolean {
        return REQUIRED_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(
                this,
                permission
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun enableLocationFeatures() {
        with(mapView) {
            // Configure location display
            location.apply {
                locationPuck = createDefault2DPuck(withBearing = true)
                enabled = true
                puckBearing = PuckBearing.COURSE
                puckBearingEnabled = true
            }

            // Set camera to follow user
            viewport.transitionTo(
                targetState = viewport.makeFollowPuckViewportState(),
                transition = viewport.makeImmediateViewportTransition()
            )

            // Update user location point on position changed
            location.addOnIndicatorPositionChangedListener { point ->
                userLocationPoint = point
            }
        }
    }

    override fun run(mapLoaded: MapLoaded) {
//        TODO("Not yet implemented")
    }

    private fun enableTapToSelect() {
        mapView.mapboxMap.addOnMapClickListener { point ->

            this.selectedLocationPoint = point // This replaces the old point if any

            // Delete the previous annotation (if any)
            annotation?.let { pointAnnotationManager.delete(it) }

            // Add the draggable pointAnnotation to the map.
            annotation = createAnnotation(point)

            true
        }
    }

    private fun createAnnotation(point: Point): PointAnnotation {
        val pointAnnotationOptions: PointAnnotationOptions = PointAnnotationOptions()
            .withPoint(Point.fromLngLat(point.longitude(), point.latitude()))
            .withIconImage(redMarkerBitmap)
            // Make the annotation draggable.
            .withDraggable(false)

        return pointAnnotationManager.create(pointAnnotationOptions)
    }

    private fun submitLocation() {

        // If no location selected, prompt to use current location
        if (selectedLocationPoint == null && userLocationPoint != null) {
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.location))
                .setMessage(getString(R.string.you_have_not_selected_a_location_would_you_like_to_use_your_current_location_instead))
                .setPositiveButton(getString(R.string.okay)) { _, _ ->

                    // Set selected location to user location
                    selectedLocationPoint = userLocationPoint

                    // Delete the previous annotation (if any)
                    annotation?.let { pointAnnotationManager.delete(it) }

                    // Add the draggable pointAnnotation to the map.
                    annotation = selectedLocationPoint?.let { createAnnotation(it) }

                    // Package up the user’s selection
                    val data = Intent().apply {
                        putExtra(
                            MapsActivityExtraData.SELECTED_LOCATION,
                            selectedLocationPoint?.toJson()
                        )
                        putExtra(MapsActivityExtraData.USER_LOCATION, userLocationPoint?.toJson())
                    }
                    setResult(RESULT_OK, data)
                    Log.d(TAG, "User's location: ${userLocationPoint.toString()}")
                    Log.d(TAG, "Selected location: ${selectedLocationPoint.toString()}")
                    finish()  // return to previous activity

                }
                .setNegativeButton(getString(R.string.cancel)) { _, _ ->
                    return@setNegativeButton
                }
                .show()
        }
        // If user has selected a location (expected case)
        else if (selectedLocationPoint != null) {
            // Package up the user’s selection
            val data = Intent().apply {
                putExtra(MapsActivityExtraData.SELECTED_LOCATION, selectedLocationPoint?.toJson())
                putExtra(MapsActivityExtraData.USER_LOCATION, userLocationPoint?.toJson())
            }
            setResult(RESULT_OK, data)
            Log.d(TAG, "User's location: ${userLocationPoint.toString()}")
            Log.d(TAG, "Selected location: ${selectedLocationPoint.toString()}")
            finish()  // return to previous activity
        }
        // If no location selected and no user location available, prompt to select a location
        else {
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.location))
                .setMessage(getString(R.string.select_a_location))
                .setPositiveButton(getString(R.string.okay)) { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        }
    }

}