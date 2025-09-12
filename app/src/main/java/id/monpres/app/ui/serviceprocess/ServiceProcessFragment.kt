package id.monpres.app.ui.serviceprocess

import android.Manifest
import android.animation.ValueAnimator
import android.app.Activity.RESULT_OK
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.insets.GradientProtection
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.transition.TransitionManager
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.LocationSettingsResponse
import com.google.android.gms.location.Priority
import com.google.android.gms.location.SettingsClient
import com.google.android.gms.tasks.Task
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.transition.MaterialFade
import com.google.android.material.transition.MaterialSharedAxis
import com.google.firebase.Timestamp
import com.ncorti.slidetoact.SlideToActView
import com.ncorti.slidetoact.SlideToActView.OnSlideCompleteListener
import dagger.hilt.android.AndroidEntryPoint
import id.monpres.app.MainViewModel
import id.monpres.app.R
import id.monpres.app.databinding.FragmentServiceProcessBinding
import id.monpres.app.enums.OrderStatus
import id.monpres.app.enums.OrderStatusType
import id.monpres.app.enums.UserRole
import id.monpres.app.model.OrderService
import id.monpres.app.ui.BaseFragment
import id.monpres.app.utils.capitalizeWords
import id.monpres.app.utils.toDateTimeDisplayString
import java.text.DateFormat
import java.util.concurrent.TimeUnit


@AndroidEntryPoint
class ServiceProcessFragment : BaseFragment() {

    companion object {
        fun newInstance() = ServiceProcessFragment()
        const val TAG = "ServiceProcessFragment"
        const val ARG_ORDER_SERVICE_ID = "orderServiceId"
        const val MINIMUM_DISTANCE_TO_START_SERVICE = 20
    }

    private val viewModel: ServiceProcessViewModel by viewModels()
    private val mainViewModel: MainViewModel by activityViewModels()

    private val args: ServiceProcessFragmentArgs by navArgs()

    private lateinit var binding: FragmentServiceProcessBinding

    private lateinit var orderService: OrderService

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private var currentLatitude: Double = 0.0
    private var currentLongitude: Double = 0.0
    private var currentAccuracy: Float = 0f
    private var currentDistance: Float = 0f

    // Permission request launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            // All permissions granted
            checkLocationSettings()
        } else {
            // Permission denied
            handlePermissionDenied()
        }
    }

    // Location settings request launcher
    private val locationSettingsRequestLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // Location services enabled, start location updates
            startLocationUpdates()
        } else {
            // User didn't enable location services
            handleLocationServicesDisabled()
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set the transition for this fragment
        enterTransition = MaterialSharedAxis(MaterialSharedAxis.Y, /* forward= */ true)
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.Y, /* forward= */ false)
        exitTransition = MaterialSharedAxis(MaterialSharedAxis.Y, /* forward= */ true)
        reenterTransition = MaterialSharedAxis(MaterialSharedAxis.Y, /* forward= */ false)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentServiceProcessBinding.inflate(inflater, container, false)

        // Set insets
        ViewCompat.setOnApplyWindowInsetsListener(binding.fragmentServiceProcessNestedScrollView) { v, windowInsets ->
            val insets =
                windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(
                insets.left,
                0,
                insets.right,
                insets.bottom
            )
            windowInsets
        }

        mainViewModel.observeOrderServiceById(args.orderServiceId)

        setupObservers()
        setupListeners()

        // Initialize location services
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        createLocationRequest()
        createLocationCallback()

        return binding.root
    }

    private fun setupObservers() {
        Log.d(TAG, "OrderServiceId: ${args.orderServiceId}")
        when (mainViewModel.getCurrentUser()?.role) {
            UserRole.CUSTOMER ->
                observeUiState(mainViewModel.userOrderServiceState) { data ->
                    orderService = data
                    Log.d(TAG, "OrderService: $orderService")
                    setupView()
                    showCancelButton(orderService.status == OrderStatus.ORDER_PLACED)
                    showActionButton(false)
                    showCompleteStatus(orderService.status in OrderStatus.entries.filter { it.type == OrderStatusType.CLOSED })
                }


            UserRole.PARTNER ->
                observeUiState(mainViewModel.partnerOrderServiceState) { data ->
                    orderService = data
                    Log.d(TAG, "OrderService: $orderService")
                    setupView()
                    showCancelButton(orderService.status == OrderStatus.ORDER_PLACED)
                    showActionButton(orderService.status in OrderStatus.entries.filter { it.type != OrderStatusType.CLOSED })
                    showCompleteStatus(orderService.status in OrderStatus.entries.filter { it.type == OrderStatusType.CLOSED })

                    if (orderService.status == OrderStatus.ON_THE_WAY) {
                        // Check and request permissions, then get location
                        checkLocationPermission()
                    } else {
                        stopLocationUpdates()
                    }
                }


            else -> {}
        }
    }

    private fun setupView() {
        binding.apply {
            root.setProtections(
                listOf(
                    GradientProtection(
                        WindowInsetsCompat.Side.TOP,
                        MaterialColors.getColor(
                            requireContext(),
                            com.google.android.material.R.attr.colorSurfaceContainer,
                            resources.getColor(
                                R.color.md_theme_surfaceContainer,
                                requireContext().theme
                            )
                        )
                    )
                )
            )

            val materialFade = MaterialFade().apply {
                duration = 150L
            }
            TransitionManager.beginDelayedTransition(binding.root, materialFade)
            fragmentServiceProcessTextViewTitle.text =
                orderService.status?.getLabel(requireContext())?.capitalizeWords() ?: "-"
            fragmentServiceProcessTextViewSubtitle.text =
                orderService.updatedAt.toDateTimeDisplayString(
                    dateStyle = DateFormat.FULL,
                    timeStyle = DateFormat.LONG
                )
            fragmentServiceProcessOrderId.text = orderService.id ?: "-"
            fragmentServiceProcessLocation.text =
                "${orderService.selectedLocationLat}, ${orderService.selectedLocationLng}"
            fragmentServiceProcessAddress.text =
                if (orderService.userAddress?.isNotBlank() == true) orderService.userAddress else "-"
            fragmentServiceProcessPartner.text = orderService.partnerId ?: "-"
            fragmentServiceProcessVehicle.text = orderService.vehicle?.name ?: "-"
            fragmentServiceProcessIssue.text = orderService.issue ?: "-"
            fragmentServiceProcessIssueDescription.text =
                if (orderService.issueDescription?.isNotBlank() == true) orderService.issueDescription else "-"

        }
    }

    private fun setupListeners() {
        binding.fragmentServiceProcessButtonCancel.setOnClickListener {
            // TODO: Cancel service
            findNavController().popBackStack()
        }

        binding.fragmentServiceProcessButtonComplete.setOnClickListener {
            findNavController().navigate(
                ServiceProcessFragmentDirections.actionServiceProcessFragmentToOrderServiceDetailFragment(
                    orderService
                )
            )
        }

        binding.fragmentServiceProcessActButton.onSlideCompleteListener =
            object : OnSlideCompleteListener {
                override fun onSlideComplete(view: SlideToActView) {

                    if (orderService.status?.serviceNextProcess() == OrderStatus.IN_PROGRESS && currentDistance > MINIMUM_DISTANCE_TO_START_SERVICE) {
                        Toast.makeText(requireContext(), "You haven't arrived at the location yet, still about $currentDistance m to go.", Toast.LENGTH_LONG).show()
                        view.setCompleted(completed = false, withAnimation = true)
                        return
                    }

                    observeUiStateOneShot(
                        viewModel.updateOrderService(
                            orderService.copy(
                                updatedAt = Timestamp.now(),
                                status = orderService.status?.serviceNextProcess()
                            )
                        )
                    ) {
                        view.setCompleted(completed = false, withAnimation = true)
                    }
                }
            }
    }

    private fun showCancelButton(show: Boolean) {
        val materialFade = MaterialFade().apply {
            duration = 150L
        }
        TransitionManager.beginDelayedTransition(binding.root, materialFade)
        binding.fragmentServiceProcessButtonCancel.visibility =
            if (show) View.VISIBLE else View.GONE
    }

    private fun showActionButton(show: Boolean) {
        val materialFade = MaterialFade().apply {
            duration = 150L
        }
        TransitionManager.beginDelayedTransition(binding.root, materialFade)
        val isButtonLocked = orderService.status?.serviceNextProcess() == OrderStatus.IN_PROGRESS && currentDistance > MINIMUM_DISTANCE_TO_START_SERVICE
        Log.d(TAG, "Is button locked: $isButtonLocked")
        binding.fragmentServiceProcessActButton.apply {
            visibility =
                if (show) View.VISIBLE else View.GONE
            text =
                orderService.status?.serviceNextProcess()?.getServiceActionLabel(requireContext())?.capitalizeWords()
                    ?: "Next"

            // Lock button if next status is in progress (repairing) and distance is greater than MINIMUM_DISTANCE_TO_START_SERVICE
//            isLocked = if (orderService.status?.serviceNextProcess() == OrderStatus.IN_PROGRESS) currentDistance > MINIMUM_DISTANCE_TO_START_SERVICE else false
//            isLocked = isButtonLocked
        }
    }

    private fun showCompleteStatus(show: Boolean) {
        val materialFade = MaterialFade().apply {
            duration = 150L
        }
        TransitionManager.beginDelayedTransition(binding.root, materialFade)
        binding.fragmentServiceProcessButtonComplete.visibility =
            if (show) View.VISIBLE else View.GONE
        if (show) {
            binding.fragmentServiceProcessProgressIndicator.isIndeterminate = false
            ValueAnimator.ofInt(0, 100).apply {
                duration = 1000
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener {
                    val progress = it.animatedValue as Int
                    binding.fragmentServiceProcessProgressIndicator.progress = progress
                }
                start()
            }
        } else binding.fragmentServiceProcessProgressIndicator.isIndeterminate = true
    }

    override fun onDestroy() {
        super.onDestroy()
        mainViewModel.stopObserve()
        stopLocationUpdates()
    }

    override fun showLoading(isLoading: Boolean) {
        // Already handled by showCompleteStatus()
    }

    override fun onResume() {
        super.onResume()
        if (hasLocationPermission()) {
            startLocationUpdates()
        }
    }

    override fun onPause() {
        super.onPause()
        stopLocationUpdates()
    }

    private fun checkLocationPermission() {
        when {
            hasLocationPermission() -> {
                // Already have permission, check location settings
                checkLocationSettings()
            }

            shouldShowRequestPermissionRationale() -> {
                // Explain why we need permission
                showPermissionRationale()
            }

            else -> {
                // Request the permission
                requestLocationPermission()
            }
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun shouldShowRequestPermissionRationale(): Boolean {
        return shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) ||
                shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    private fun requestLocationPermission() {
        requestPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun showPermissionRationale() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.location_permission_needed))
            .setMessage(getString(R.string.grant_location_permission))
            .setPositiveButton(getString(R.string.okay)) { _, _ ->
                requestLocationPermission()
            }
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                dialog.dismiss()
                handlePermissionDenied()
            }
            .create()
            .show()
    }

    private fun handlePermissionDenied() {
        if (!hasLocationPermission() && !shouldShowRequestPermissionRationale()) {
            // Permission permanently denied, guide user to app settings
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.x_permission_is_not_granted, getString(R.string.location)))
                .setMessage(getString(R.string.this_permission_is_disabled))
                .setPositiveButton(getString(R.string.go_to_settings)) { _, _ ->
                    openAppSettings()
                }
                .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                    dialog.dismiss()
                }
                .create()
                .show()
        } else {
            // Temporary denial, we can ask again later
            Log.d("Location", "Location permission denied")
        }
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", requireActivity().packageName, null)
        }
        startActivity(intent)
    }

    private fun createLocationRequest() {
        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, TimeUnit.SECONDS.toMillis(10)).apply {
            setMinUpdateDistanceMeters(100f)
            setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
            setWaitForAccurateLocation(true)
        }.build()
    }

    private fun createLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                locationResult.lastLocation?.let {
                    handleNewLocation(it)
                }
            }
        }
    }

    private fun checkLocationSettings() {
        val builder = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)

        val client: SettingsClient = LocationServices.getSettingsClient(requireActivity())
        val task: Task<LocationSettingsResponse> = client.checkLocationSettings(builder.build())

        task.addOnSuccessListener {
            // Location settings are satisfied, start location updates
            startLocationUpdates()
        }

        task.addOnFailureListener { exception ->
            if (exception is ResolvableApiException) {
                // Location settings are not satisfied, show dialog to enable
                try {
                    locationSettingsRequestLauncher.launch(
                        IntentSenderRequest.Builder(exception.resolution).build()
                    )
                } catch (sendEx: Exception) {
                    // Ignore the error
                    Log.e("Location", "Error launching location settings", sendEx)
                }
            } else {
                handleLocationServicesDisabled()
            }
        }
    }

    private fun startLocationUpdates() {
        if (!hasLocationPermission()) {
            return
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            Log.e("Location", "SecurityException: ${e.message}")
        }
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun handleNewLocation(location: Location) {
        currentLatitude = location.latitude
        currentLongitude = location.longitude
        currentAccuracy = location.accuracy
        currentDistance = calculateAerialDistance(currentLatitude, currentLongitude, orderService.selectedLocationLat!!, orderService.selectedLocationLng!!)

        Log.d(
            "Location",
            "Latitude: $currentLatitude, Longitude: $currentLongitude, Accuracy: $currentAccuracy, Distance: $currentDistance"
        )


        // Update your UI with the new location here
        // For example: updateTextView("Lat: $latitude, Long: $longitude")
    }

    private fun handleLocationServicesDisabled() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.location_services_disabled))
            .setMessage(getString(R.string.enable_location_services))
            .setPositiveButton(getString(R.string.enable)) { _, _ ->
                openLocationSettings()
            }
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }

    private fun openLocationSettings() {
        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        startActivity(intent)
    }

    private fun calculateAerialDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }
}