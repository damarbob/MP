package id.monpres.app.ui.serviceprocess

import android.Manifest
import android.animation.ValueAnimator
import android.app.Activity.RESULT_OK
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
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
import androidx.core.graphics.scale
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.insets.GradientProtection
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
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
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.transition.MaterialFade
import com.google.android.material.transition.MaterialSharedAxis
import com.google.firebase.Timestamp
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.ncorti.slidetoact.SlideToActView
import com.ncorti.slidetoact.SlideToActView.OnSlideCompleteListener
import dagger.hilt.android.AndroidEntryPoint
import id.monpres.app.MainGraphViewModel
import id.monpres.app.R
import id.monpres.app.databinding.FragmentServiceProcessBinding
import id.monpres.app.enums.OrderStatus
import id.monpres.app.enums.OrderStatusType
import id.monpres.app.enums.UserRole
import id.monpres.app.model.MontirPresisiUser
import id.monpres.app.model.OrderItem
import id.monpres.app.model.OrderService
import id.monpres.app.ui.BaseFragment
import id.monpres.app.ui.adapter.OrderItemAdapter
import id.monpres.app.ui.itemdecoration.SpacingItemDecoration
import id.monpres.app.ui.orderitemeditor.OrderItemEditorFragment
import id.monpres.app.usecase.CalculateAerialDistanceUseCase
import id.monpres.app.usecase.GoogleMapsIntentUseCase
import id.monpres.app.usecase.IndonesianCurrencyFormatter
import id.monpres.app.utils.capitalizeWords
import id.monpres.app.utils.toDateTimeDisplayString
import java.text.DateFormat
import java.text.NumberFormat
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
    private val mainGraphViewModel: MainGraphViewModel by activityViewModels()

    private val args: ServiceProcessFragmentArgs by navArgs()

    private lateinit var binding: FragmentServiceProcessBinding

    private lateinit var orderService: OrderService
    private var orderItems: ArrayList<OrderItem>? = null
    private var price: Double? = null
    private lateinit var orderItemAdapter: OrderItemAdapter

    private val calculateAerialDistance = CalculateAerialDistanceUseCase()
    private val indonesianCurrencyFormatter = IndonesianCurrencyFormatter()
    private val googleMapsIntentUseCase = GoogleMapsIntentUseCase()

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private var currentLatitude: Double = 0.0
    private var currentLongitude: Double = 0.0
    private var currentAccuracy: Float = 0f
    private var aerialDistanceToTargetInMeters: Float = 40000000f

    private var currentUser: MontirPresisiUser? = null

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
        enterTransition = MaterialSharedAxis(MaterialSharedAxis.X, /* forward= */ true)
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.X, /* forward= */ false)
        exitTransition = MaterialSharedAxis(MaterialSharedAxis.X, /* forward= */ true)
        reenterTransition = MaterialSharedAxis(MaterialSharedAxis.X, /* forward= */ false)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentServiceProcessBinding.inflate(inflater, container, false)

        // Set insets
        ViewCompat.setOnApplyWindowInsetsListener(binding.fragmentServiceProcessNestedScrollView) { v, windowInsets ->
            val insets =
                windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                insets.left,
                0,
                insets.right,
                insets.bottom
            )
            windowInsets
        }

        parentFragmentManager.setFragmentResultListener(
            OrderItemEditorFragment.REQUEST_KEY_ORDER_ITEM_EDITOR,
            viewLifecycleOwner
        ) { _, bundle ->
            orderItems =
                bundle.getParcelableArrayList(OrderItemEditorFragment.KEY_ORDER_ITEMS)
            price = OrderService.getPriceFromOrderItems(orderItems)
            Log.d(TAG, "OrderItems: $orderItems")
            observeUiStateOneShot(
                viewModel.updateOrderService(
                    orderService.copy(
                        status = if (orderService.status?.serviceNextProcess() == OrderStatus.WAITING_FOR_PAYMENT) OrderStatus.WAITING_FOR_PAYMENT else orderService.status,
                        updatedAt = Timestamp.now(),
                        orderItems = orderItems,
                        price = price
                    )
                )
            ) {
            }
        }

        mainGraphViewModel.observeOrderServiceById(args.orderServiceId)
        currentUser = mainGraphViewModel.getCurrentUser()

        setupRecyclerView()
        setupObservers()
        setupListeners()

        // Initialize location services
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        createLocationRequest()
        createLocationCallback()

        return binding.root
    }

    private fun setupRecyclerView() {
        orderItemAdapter = OrderItemAdapter()
        binding.fragmentServiceProcessRecyclerViewOrderItem.apply {
            adapter = orderItemAdapter
            layoutManager = LinearLayoutManager(requireContext())
            addItemDecoration(SpacingItemDecoration(8))
        }
    }

    private fun setupObservers() {
        Log.d(TAG, "OrderServiceId: ${args.orderServiceId}")
        when (currentUser?.role) {
            UserRole.CUSTOMER ->
                observeUiState(mainGraphViewModel.userOrderServiceState) { data ->
                    orderService = data
                    orderItems = orderService.orderItems?.toMutableList() as ArrayList<OrderItem>?
                    Log.d(TAG, "OrderService: $orderService")
                    setupView()
                    showCancelButton(orderService.status == OrderStatus.ORDER_PLACED)
                    showActionButton(false)
                    showCompleteStatus(orderService.status in OrderStatus.entries.filter { it.type == OrderStatusType.CLOSED })
                }


            UserRole.PARTNER ->
                observeUiState(mainGraphViewModel.partnerOrderServiceState) { data ->
                    orderService = data
                    orderItems = orderService.orderItems?.toMutableList() as ArrayList<OrderItem>?
                    Log.d(TAG, "OrderService: $orderService")
                    setupView()
                    showCancelButton(orderService.status == OrderStatus.ORDER_PLACED)
                    showActionButton(orderService.status in OrderStatus.entries.filter { it.type != OrderStatusType.CLOSED })
                    showCompleteStatus(orderService.status in OrderStatus.entries.filter { it.type == OrderStatusType.CLOSED })

                    if (orderService.status == OrderStatus.ON_THE_WAY || orderService.status == OrderStatus.ORDER_PLACED) {
                        // Check and request permissions, then get location
                        checkLocationPermission()
                    } else {
                        stopLocationUpdates()
                    }

                    when (orderService.status) {
                        OrderStatus.WAITING_FOR_PAYMENT, OrderStatus.COMPLETED -> {
                            binding.fragmentServiceProcessLinearLayoutOrderItemContainer.visibility =
                                View.VISIBLE
                        }

                        else -> {}
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
            TransitionManager.beginDelayedTransition(root, materialFade)

            // Order Item
            fragmentServiceProcessLinearLayoutOrderItemContainer.visibility =
                if (orderService.orderItems.isNullOrEmpty()) View.GONE else View.VISIBLE
            fragmentServiceProcessButtonEditOrderItem.visibility =
                if (currentUser?.role == UserRole.PARTNER && orderService.status?.type != OrderStatusType.CLOSED) View.VISIBLE else View.GONE

            // Title and contents
            fragmentServiceProcessTextViewTitle.text =
                orderService.status?.getLabel(requireContext())?.capitalizeWords() ?: "-"
            fragmentServiceProcessTextViewSubtitle.text =
                orderService.updatedAt.toDateTimeDisplayString(
                    dateStyle = DateFormat.FULL,
                    timeStyle = DateFormat.LONG
                )

            fragmentServiceProcessTextViewUserName.text =
                if (currentUser?.role == UserRole.CUSTOMER) orderService.partner?.displayName
                    ?: "-" else if (currentUser?.role == UserRole.PARTNER) orderService.user?.displayName
                    ?: "-" else "-"
            fragmentServiceProcessTextViewUserDetail.text =
                if (currentUser?.role == UserRole.CUSTOMER) getString(R.string.partner) else if (currentUser?.role == UserRole.PARTNER) getString(
                    R.string.customer
                ) else ""

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

            // Show total price with currency format
            price = orderService.price ?: OrderService.getPriceFromOrderItems(orderItems)
            fragmentServiceProcessOrderItemsTotalPrice.text = indonesianCurrencyFormatter(price!!)

            // Set order items list
            orderItemAdapter.submitList(orderItems?.toList())

            // Current distance
            fragmentServiceProcessTextViewCurrentDistance.visibility =
                if (currentUser?.role == UserRole.PARTNER && (orderService.status == OrderStatus.ON_THE_WAY || orderService.status == OrderStatus.ORDER_PLACED)) View.VISIBLE else View.GONE

            // Mapbox
            fragmentServiceProcessMapView.mapboxMap.setCamera(
                CameraOptions.Builder()
                    .center(
                        Point.fromLngLat(
                            orderService.selectedLocationLng ?: 0.0,
                            orderService.selectedLocationLat ?: 0.0
                        )
                    )
                    .pitch(0.0)
                    .zoom(12.0)
                    .bearing(0.0)
                    .build()
            )
            val originalBitmap = BitmapFactory.decodeResource(resources, R.drawable.mp_marker)
            val desiredWidth = 60 // in pixels
            val desiredHeight = 96 // in pixels

            val resizedBitmap = originalBitmap.scale(desiredWidth, desiredHeight, false)

            // Create an instance of the Annotation API and get the PointAnnotationManager.
            val annotationApi = fragmentServiceProcessMapView.annotations
            val pointAnnotationManager = annotationApi.createPointAnnotationManager()

            // Set options for the resulting symbol layer.
            val pointAnnotationOptions: PointAnnotationOptions = PointAnnotationOptions()
                // Define a geographic coordinate.
                .withPoint(
                    Point.fromLngLat(
                        orderService.selectedLocationLng ?: 0.0,
                        orderService.selectedLocationLat ?: 0.0
                    )
                )
                // Specify the bitmap you assigned to the point annotation
                // The bitmap will be added to map style automatically.
                .withIconImage(resizedBitmap)
            // Add the resulting pointAnnotation to the map.
            pointAnnotationManager.create(pointAnnotationOptions)

            fragmentServiceProcessMapView.setOnTouchListener { view, event ->
                view.performClick()
                if (event.pointerCount >= 2) {
                    // Two-finger touch - let MapView handle it
                    fragmentServiceProcessMapView.mapboxMap.gesturesPlugin {
                        scrollEnabled = true
                    }
                    fragmentServiceProcessNestedScrollView.requestDisallowInterceptTouchEvent(true)
                } else {
                    // One-finger touch - let NestedScrollView handle it
                    fragmentServiceProcessMapView.mapboxMap.gesturesPlugin {
                        scrollEnabled = false
                    }
                    fragmentServiceProcessNestedScrollView.requestDisallowInterceptTouchEvent(false)
                }
                false // Return false to allow MapView to handle the touch
            }
        }
    }

    private fun setupListeners() {
        binding.fragmentServiceProcessButtonCancel.setOnClickListener {
            observeUiStateOneShot(
                viewModel.updateOrderService(
                    orderService.copy(
                        updatedAt = Timestamp.now(),
                        status = OrderStatus.CANCELLED
                    )
                )
            ) {
            }
        }

        binding.fragmentServiceProcessButtonComplete.setOnClickListener {
            findNavController().navigate(
                ServiceProcessFragmentDirections.actionServiceProcessFragmentToOrderServiceDetailFragment(
                    orderService
                )
            )
        }

        binding.fragmentServiceProcessButtonEditOrderItem.setOnClickListener {
            findNavController().navigate(
                ServiceProcessFragmentDirections.actionServiceProcessFragmentToOrderItemEditorFragment(
                    orderItems = orderItems?.toTypedArray(), orderService = orderService
                )
            )
        }

        binding.fragmentServiceProcessActButton.onSlideCompleteListener =
            object : OnSlideCompleteListener {
                override fun onSlideComplete(view: SlideToActView) {

                    if (orderService.status?.serviceNextProcess() == OrderStatus.IN_PROGRESS && aerialDistanceToTargetInMeters > MINIMUM_DISTANCE_TO_START_SERVICE) {
                        if (hasLocationPermission()) {
                            val currentDistance =
                                NumberFormat.getInstance().format(aerialDistanceToTargetInMeters)
                            Toast.makeText(
                                requireContext(),
                                getString(
                                    R.string.haven_t_arrived_at_the_location_yet,
                                    currentDistance
                                ),
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            requestLocationPermission()
                        }
                        view.setCompleted(completed = false, withAnimation = true)
                        return
                    }

                    if (orderService.status?.serviceNextProcess() == OrderStatus.WAITING_FOR_PAYMENT) {
                        findNavController().navigate(
                            ServiceProcessFragmentDirections.actionServiceProcessFragmentToOrderItemEditorFragment(
                                orderItems = orderItems?.toTypedArray(), orderService = orderService
                            )
                        )
                        view.setCompleted(completed = false, withAnimation = true)
                        return
                    }

                    if (orderService.status?.serviceNextProcess() == OrderStatus.COMPLETED && orderItems.isNullOrEmpty()) {
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle(getString(R.string.warning))
                            .setMessage(getString(R.string.you_didn_t_add_any_items_fees_to_the_order))
                            .setPositiveButton(getString(R.string.okay)) { dialog, which ->
                                findNavController().navigate(
                                    ServiceProcessFragmentDirections.actionServiceProcessFragmentToOrderItemEditorFragment(
                                        orderItems = orderItems?.toTypedArray(),
                                        orderService = orderService
                                    )
                                )
                                view.setCompleted(completed = false, withAnimation = true)
                            }
                            .setNegativeButton(getString(R.string.complete_anyway)) { dialog, which ->
                                updateOrderStatus(view)
                            }
                            .setNeutralButton(getString(R.string.cancel)) { dialog, which ->
                                dialog.dismiss()
                                view.setCompleted(completed = false, withAnimation = true)
                            }
                            .setCancelable(false)
                            .show()

                        // IMPORTANT: Since the logic is now handled inside the listeners, we return here
                        // to prevent the code below from running immediately.
                        return
                    }

                    // This is the default action if no special conditions are met
                    updateOrderStatus(view)
                }
            }

        binding.fragmentServiceProcessButtonNavigation.setOnClickListener {
            if (orderService.selectedLocationLat != null && orderService.selectedLocationLng != null) {
                googleMapsIntentUseCase(
                    requireContext(),
                    orderService.selectedLocationLat!!, orderService.selectedLocationLng!!
                )
            }
        }
    }

    private fun updateOrderStatus(view: SlideToActView) {
        observeUiStateOneShot(
            viewModel.updateOrderService(
                orderService.copy(
                    updatedAt = Timestamp.now(),
                    status = orderService.status?.serviceNextProcess(),
                    orderItems = orderItems,
                    price = price
                )
            )
        ) {
            // This block runs after the ViewModel update is successful.
            // You might want to navigate away or show a success message.
            // For now, we just reset the slider on success.
            view.setCompleted(completed = false, withAnimation = true)
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
        val isButtonLocked =
            orderService.status?.serviceNextProcess() == OrderStatus.IN_PROGRESS && aerialDistanceToTargetInMeters > MINIMUM_DISTANCE_TO_START_SERVICE
        Log.d(TAG, "Is button locked: $isButtonLocked")
        binding.fragmentServiceProcessActButton.apply {
            visibility =
                if (show) View.VISIBLE else View.GONE
            text =
                orderService.status?.serviceNextProcess()?.getServiceActionLabel(requireContext())
                    ?.capitalizeWords()
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
        mainGraphViewModel.stopObserve()
        stopLocationUpdates()
    }

//    override fun showLoading(isLoading: Boolean) {
//        // Already handled by showCompleteStatus()
//    }

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
                .setTitle(
                    getString(
                        R.string.x_permission_is_not_granted,
                        getString(R.string.location)
                    )
                )
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
        locationRequest =
            LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, TimeUnit.SECONDS.toMillis(10))
                .apply {
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
        if (!hasLocationPermission() || orderService.status != OrderStatus.ON_THE_WAY || orderService.status != OrderStatus.ORDER_PLACED) {
            return
        }
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
        if (!hasLocationPermission() || orderService.status != OrderStatus.ON_THE_WAY) {
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
        aerialDistanceToTargetInMeters = calculateAerialDistance(
            currentLatitude,
            currentLongitude,
            orderService.selectedLocationLat!!,
            orderService.selectedLocationLng!!
        )

        Log.d(
            "Location",
            "Latitude: $currentLatitude, Longitude: $currentLongitude, Accuracy: $currentAccuracy, Distance: $aerialDistanceToTargetInMeters"
        )


        // Update your UI with the new location here
        // For example: updateTextView("Lat: $latitude, Long: $longitude")
        binding.fragmentServiceProcessTextViewCurrentDistance.text =
            "± ${NumberFormat.getInstance().format(aerialDistanceToTargetInMeters)} m"
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

    override val progressIndicator: LinearProgressIndicator
        get() = binding.fragmentServiceProcessLinearProgressIndicator
}