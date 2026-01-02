package id.monpres.app.ui.serviceprocess

import android.Manifest
import android.animation.ValueAnimator
import android.app.Activity.RESULT_OK
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.insets.GradientProtection
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.LocationSettingsResponse
import com.google.android.gms.location.Priority
import com.google.android.gms.location.SettingsClient
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Task
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.transition.MaterialContainerTransform
import com.google.android.material.transition.MaterialFade
import com.google.firebase.Timestamp
import com.google.firebase.firestore.GeoPoint
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.layers.properties.generated.IconAnchor
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotation
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.ncorti.slidetoact.SlideToActView
import com.ncorti.slidetoact.SlideToActView.OnSlideCompleteListener
import dagger.hilt.android.AndroidEntryPoint
import dev.androidbroadcast.vbpd.viewBinding
import id.monpres.app.MainGraphViewModel
import id.monpres.app.R
import id.monpres.app.databinding.FragmentServiceProcessBinding
import id.monpres.app.enums.OrderStatus
import id.monpres.app.enums.OrderStatusType
import id.monpres.app.enums.PartnerCategory
import id.monpres.app.enums.UserRole
import id.monpres.app.enums.VehiclePowerSource
import id.monpres.app.enums.VehicleTransmission
import id.monpres.app.interfaces.IOrderServiceProvider
import id.monpres.app.model.MontirPresisiUser
import id.monpres.app.model.OrderItem
import id.monpres.app.model.OrderService
import id.monpres.app.model.PaymentMethod
import id.monpres.app.model.VehicleType
import id.monpres.app.notification.OrderServiceNotification
import id.monpres.app.state.UiState
import id.monpres.app.ui.BaseFragment
import id.monpres.app.ui.adapter.OrderItemAdapter
import id.monpres.app.ui.itemdecoration.SpacingItemDecoration
import id.monpres.app.ui.orderitemeditor.OrderItemEditorFragment
import id.monpres.app.ui.payment.PaymentGuideBottomSheetFragment
import id.monpres.app.ui.payment.PaymentMethodBottomSheetFragment
import id.monpres.app.usecase.GoogleMapsIntentUseCase
import id.monpres.app.usecase.IndonesianCurrencyFormatter
import id.monpres.app.usecase.NumberFormatterUseCase
import id.monpres.app.usecase.OpenWhatsAppUseCase
import id.monpres.app.utils.capitalizeWords
import id.monpres.app.utils.enumByNameIgnoreCaseOrNull
import id.monpres.app.utils.toDateTimeDisplayString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.DateFormat
import java.util.Calendar
import java.util.concurrent.TimeUnit


@AndroidEntryPoint
class ServiceProcessFragment : BaseFragment(R.layout.fragment_service_process),
    IOrderServiceProvider {

    companion object {
        fun newInstance() = ServiceProcessFragment()
        val TAG = ServiceProcessFragment::class.simpleName
        const val ARG_ORDER_SERVICE_ID = "orderServiceId"
        const val MINIMUM_DISTANCE_TO_START_SERVICE = 50f
    }

    private var partnerPointAnnotation: PointAnnotation? = null
    private var observePartnerLiveLocationJob: Job? = null
    private val viewModel: ServiceProcessViewModel by viewModels()
    private val mainGraphViewModel: MainGraphViewModel by activityViewModels()

    private val args: ServiceProcessFragmentArgs by navArgs()

    private val binding by viewBinding(FragmentServiceProcessBinding::bind)

    private lateinit var orderService: OrderService
    private var orderItems: ArrayList<OrderItem>? = null
    private var price: Double? = null
    private lateinit var orderItemAdapter: OrderItemAdapter

    private lateinit var paymentMethodBottomSheet: PaymentMethodBottomSheetFragment
    private lateinit var paymentGuideBottomSheet: PaymentGuideBottomSheetFragment

    private val indonesianCurrencyFormatter = IndonesianCurrencyFormatter()
    private val googleMapsIntentUseCase = GoogleMapsIntentUseCase()
    private val numberFormatterUseCase = NumberFormatterUseCase()
    private val openWhatsAppUseCase = OpenWhatsAppUseCase()

    private var aerialDistanceToTargetInMeters: Float = 40000000f

    private var currentUser: MontirPresisiUser? = null

    // Permission request launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            // All permissions granted
            // Permissions granted. Check if we need to start the service now.
            if (::orderService.isInitialized &&
                orderService.status == OrderStatus.ON_THE_WAY &&
                currentUser?.role == UserRole.PARTNER
            ) {
                checkLocationSettingsAndStartService()
            }
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
            // User enabled location services. Start the service.
//            if (::orderService.isInitialized &&
//                orderService.status == OrderStatus.ON_THE_WAY &&
//                currentUser?.role == UserRole.PARTNER
//            ) {
//                startLocationService(OrderServiceLocationTrackingService.MODE_PARTNER)
//            }
        } else {
            // User didn't enable location services
            handleLocationServicesDisabled()
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set the transition for this fragment
        sharedElementEnterTransition = MaterialContainerTransform().apply {
            drawingViewId = R.id.nav_host_fragment_activity_main
            scrimColor = Color.TRANSPARENT
            setAllContainerColors(
                MaterialColors.getColor(
                    requireContext(),
                    com.google.android.material.R.attr.colorSurfaceContainer,
                    resources.getColor(
                        R.color.md_theme_surfaceContainer,
                        requireContext().theme
                    )
                )
            )
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
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
            orderItems = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                bundle.getParcelableArrayList(
                    OrderItemEditorFragment.KEY_ORDER_ITEMS,
                    OrderItem::class.java
                )
            else
                bundle.getParcelableArrayList(OrderItemEditorFragment.KEY_ORDER_ITEMS)
            price = OrderService.getPriceFromOrderItems(orderItems)
            Log.d(TAG, "OrderItems: $orderItems")
            observeUiStateOneShot(
                mainGraphViewModel.updateOrderService(
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

        currentUser = mainGraphViewModel.getCurrentUser()
        mainGraphViewModel.observeOrderServiceById(args.orderServiceId)

        if (!OrderServiceNotification.isPostPromotionEnabled(requireContext())) {
            Log.d(
                TAG,
                "onCreate: ${OrderServiceNotification.isPostPromotionEnabled(requireContext())}"
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                openNotificationDeviceSetting()
            }
        }

        setupMap()
        setupRecyclerView()
        setupObservers()
        setupListeners()

        handlePaymentMethodResult()
    }

    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    private fun openNotificationDeviceSetting() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.enable_live_update_feature))
            .setPositiveButton(getString(R.string.okay)) { dialog, which ->
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
                }
                requireContext().startActivity(intent)
            }.show()
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
        // --- The New, Combined Observer ---
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Combine the order state from the MainGraphViewModel and the preferred payment method from our local ViewModel
                mainGraphViewModel.openedOrderServiceState
                    .combine(viewModel.preferredPaymentMethod) { orderServiceState, preferredPayment ->
                        Pair(orderServiceState, preferredPayment)
                    }
                    .distinctUntilChanged() // IMPORTANT: Prevents re-calculation if states are the same
                    .collect { (orderServiceState, preferredPayment) ->

                        if (orderServiceState !is UiState.Success) {
                            // Handle loading or error state if needed
                            return@collect
                        }

                        // This 'data' is now correctly a single OrderService object.
                        orderService = orderServiceState.data
                        orderItems =
                            orderService.orderItems?.toMutableList() as ArrayList<OrderItem>?
                        Log.d(TAG, "Single OrderService updated: ${orderService.status}")

                        val effectivePaymentMethod: PaymentMethod
                        if (!orderService.paymentMethod.isNullOrBlank()) {
                            // Priority 1: The order document already has a payment method. This is the source of truth.
                            effectivePaymentMethod = PaymentMethod.getDefaultPaymentMethodById(
                                requireContext(),
                                orderService.paymentMethod!!
                            ) ?: PaymentMethod.getDefaultPaymentMethods(requireContext()).first()
                            viewModel.onPaymentMethodSelected(effectivePaymentMethod)
                        } else {
                            // Priority 2: The order has no payment method yet, so use the user's local preference.
                            effectivePaymentMethod = preferredPayment

                            // If the order has no payment method and the current user is a customer,
                            // automatically save their preferred method to the order.
                            if (currentUser?.role == UserRole.CUSTOMER) {
                                Log.d(
                                    TAG,
                                    "Auto-saving preferred payment method '${effectivePaymentMethod.id}' to order ${orderService.id}"
                                )
                                mainGraphViewModel.updateOrderService(
                                    orderService.copy(paymentMethod = effectivePaymentMethod.id)
                                )
                                    // We can launch this in a new scope so it doesn't block the collector
                                    .launchIn(viewLifecycleOwner.lifecycleScope)
                            }
                        }
                        binding.fragmentServiceProcessTextViewPaymentMethod.text =
                            effectivePaymentMethod.name


                        // This function sets up all the static UI text based on the order.
                        setupView()

                        // This function handles the visibility of the action button based on role and status.
                        updateUiBasedOnRoleAndStatus()

                        // If the order we are currently viewing is ON_THE_WAY, we need to observe its live location for UI updates.
                        if (orderService.status == OrderStatus.ON_THE_WAY || orderService.status == OrderStatus.ORDER_PLACED) {
                            observePartnerLiveLocation()
                        } else {
                            updateMap()
                            stopObservingPartnerLiveLocation()
                        }
                    }
            }
        }
    }

    /**
     * A new helper function to consolidate UI visibility logic that depends on the user's role.
     */
    private fun updateUiBasedOnRoleAndStatus() {
        val role = currentUser?.role
        val status = orderService.status

        val isClosed = status?.type == OrderStatusType.CLOSED

        // Determine button visibility based on role and status
        when (role) {
            UserRole.CUSTOMER, UserRole.ADMIN -> {
                // Admin is treated like a Customer (Viewer)
                showCancelButton(status == OrderStatus.ORDER_PLACED)
                showActionButton(false) // Customer/Admin never sees the main action button

                TransitionManager.beginDelayedTransition(binding.root, AutoTransition())
                // Show/hide payment section based on status
                if (status == OrderStatus.WAITING_FOR_PAYMENT) {
                    binding.fragmentServiceProcessLinearLayoutPaymentMethodContainer.visibility =
                        View.VISIBLE
                } else {
                    binding.fragmentServiceProcessLinearLayoutPaymentMethodContainer.visibility =
                        View.GONE
                }
            }

            UserRole.PARTNER -> {
                showCancelButton(status == OrderStatus.ORDER_PLACED)
                showActionButton(!isClosed)

                if (status == OrderStatus.ON_THE_WAY || status == OrderStatus.ORDER_PLACED) {
                    checkLocationSettingsAndStartService()
                }
            }

            else -> {
                // Default case, hide all action buttons
                showCancelButton(false)
                showActionButton(false)
            }
        }

        showCompleteStatus(isClosed)

        // Dismiss bottom sheet if status is not waiting for payment
        if (status != OrderStatus.WAITING_FOR_PAYMENT) {
            if (::paymentMethodBottomSheet.isInitialized && paymentMethodBottomSheet.isAdded) {
                paymentMethodBottomSheet.dismiss()
            }
            if (::paymentGuideBottomSheet.isInitialized && paymentGuideBottomSheet.isAdded) {
                paymentGuideBottomSheet.dismiss()
            }
        }

        // Disable button if order is on the way and distance is greater than minimum
        if (role == UserRole.PARTNER && status == OrderStatus.ON_THE_WAY && aerialDistanceToTargetInMeters > MINIMUM_DISTANCE_TO_START_SERVICE) {
            binding.fragmentServiceProcessActButton.isLocked = true
            binding.fragmentServiceProcessTextViewWarningMessage.visibility = View.VISIBLE
        } else {
            binding.fragmentServiceProcessActButton.isLocked = false
            binding.fragmentServiceProcessTextViewWarningMessage.visibility = View.GONE
        }
    }

    private fun observePartnerLiveLocation() {
        observePartnerLiveLocationJob?.cancel()
        // Launch a new collector for the live location
        observePartnerLiveLocationJob = lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                orderService.id?.let {
                    Log.d(TAG, "OrderServiceId: $it")
                    viewModel.observePartnerLocation(it).collect { livePartnerLocation ->
                        val partnerGeoPoint = livePartnerLocation.location ?: return@collect
                        Log.d(TAG, "Customer received partner location: $partnerGeoPoint")

                        updateDistanceAndProgress(partnerGeoPoint)
                        updateMap(partnerGeoPoint)

                        if (livePartnerLocation.isArrived) {
                            Log.d(TAG, "Partner arrived at the location")
                            Toast.makeText(
                                requireContext(),
                                getString(R.string.partner_has_arrived),
                                Toast.LENGTH_SHORT
                            ).show()
                            stopObservingPartnerLiveLocation()
                        }
                    }
                }
            }
        }
    }

    private fun stopObservingPartnerLiveLocation() {
        observePartnerLiveLocationJob?.cancel()
        observePartnerLiveLocationJob = null
        Log.d(TAG, "Stopped observing partner live location.")
    }

    /**
     * New helper function to encapsulate the distance and progress UI updates.
     */
    private fun updateDistanceAndProgress(partnerGeoPoint: GeoPoint) {
        val partnerLoc = Location("partner").apply {
            latitude = partnerGeoPoint.latitude
            longitude = partnerGeoPoint.longitude
        }
        val targetLoc = Location("target").apply {
            latitude = orderService.selectedLocationLat ?: 0.0
            longitude = orderService.selectedLocationLng ?: 0.0
        }
        val initialDistance = getInitialDistance(targetLoc)
        val currentDistance = partnerLoc.distanceTo(targetLoc)
        val progress = calculateProgress(initialDistance, currentDistance)
        Log.d(
            TAG,
            "initialDistance: $initialDistance, currentDistance: $currentDistance, progress: $progress"
        )

        aerialDistanceToTargetInMeters = currentDistance

        // Update the UI components
        if (orderService.status == OrderStatus.ON_THE_WAY) {
            binding.fragmentServiceProcessProgressIndicator.isIndeterminate = false
            binding.fragmentServiceProcessProgressIndicator.progress = progress
        } else {
            binding.fragmentServiceProcessProgressIndicator.isIndeterminate = true
        }

        binding.fragmentServiceProcessTextViewCurrentDistance.text =
            getString(R.string.x_distance_m, numberFormatterUseCase(currentDistance))

        binding.fragmentServiceProcessTextViewWarningMessage.text = getString(
            R.string.haven_t_arrived_at_the_location_yet,
            numberFormatterUseCase(aerialDistanceToTargetInMeters)
        )

        // Disable button if order is on the way and distance is greater than minimum
        if (currentUser?.role == UserRole.PARTNER && orderService.status == OrderStatus.ON_THE_WAY && aerialDistanceToTargetInMeters > MINIMUM_DISTANCE_TO_START_SERVICE) {
            binding.fragmentServiceProcessActButton.isLocked = true
            binding.fragmentServiceProcessTextViewWarningMessage.visibility = View.VISIBLE
        } else {
            binding.fragmentServiceProcessActButton.isLocked = false
            binding.fragmentServiceProcessTextViewWarningMessage.visibility = View.GONE
        }
    }

    // New helper functions for distance calculation
    private fun getInitialDistance(targetLoc: Location): Float {
        return orderService.partner?.locationLat?.let { lat ->
            orderService.partner?.locationLng?.let { lng ->
                val initialPartnerLoc = Location(getString(R.string.partner_base_location)).apply {
                    latitude = lat.toDouble()
                    longitude = lng.toDouble()
                }
                initialPartnerLoc.distanceTo(targetLoc)
            }
        } ?: 0f
    }

    private fun calculateProgress(initialDistance: Float, currentDistance: Float): Int {
        return if (initialDistance > 0) {
            ((initialDistance - currentDistance) / initialDistance * 100).toInt().coerceIn(0, 100)
        } else {
            100 // If initial distance is 0, they are already there.
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

            // Edit button only for Partner
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

            // Determine logic for Name and Detail based on current user role
            // Admin & Customer see Partner's Name. Partner sees Customer's Name.
            val isPartnerView = currentUser?.role == UserRole.PARTNER

            fragmentServiceProcessTextViewUserName.text =
                if (isPartnerView) orderService.user?.displayName ?: "-"
                else orderService.partner?.displayName ?: "-"

            fragmentServiceProcessTextViewUserDetail.text =
                if (isPartnerView) getString(R.string.customer)
                else getString(R.string.partner)

            // Admin sees both Customer and Partner
            if (currentUser?.role == UserRole.ADMIN) {
                fragmentServiceProcessLinearLayoutUserOrPartnerContainer2.visibility = View.VISIBLE
                fragmentServiceProcessTextViewUserName2.text =
                    orderService.user?.displayName ?: "-"
                fragmentServiceProcessTextViewUserDetail2.text = getString(R.string.customer)
            } else {
                fragmentServiceProcessLinearLayoutUserOrPartnerContainer2.visibility = View.GONE
            }

            fragmentServiceProcessOrderId.text = orderService.id ?: "-"
            fragmentServiceProcessLocation.text =
                "${orderService.selectedLocationLat}, ${orderService.selectedLocationLng}"
            fragmentServiceProcessAddress.text =
                if (orderService.userAddress?.isNotBlank() == true) orderService.userAddress else "-"
            fragmentServiceProcessPartner.text = orderService.partnerId ?: "-"
            fragmentServiceProcessVehicle.text = orderService.vehicle?.name ?: "-"
            val issueEnum = orderService.issue?.let { PartnerCategory.fromName(it) }
            val issueString = issueEnum?.let { getString(it.label) } ?: orderService.issue
            ?: getString(R.string.unknown_issue)
            fragmentServiceProcessIssue.text = issueString
            fragmentServiceProcessIssueDescription.text =
                if (orderService.issueDescription?.isNotBlank() == true) orderService.issueDescription else "-"

            fragmentServiceProcessRegistrationNumber.text =
                orderService.vehicle?.registrationNumber ?: "-"
            fragmentServiceProcessLicensePlateNumber.text =
                orderService.vehicle?.licensePlateNumber ?: "-"
            fragmentServiceProcessType.text = VehicleType.getSampleList(requireContext())
                .find { it.id == orderService.vehicle?.typeId }?.name ?: "-"
            orderService.vehicle?.powerSource
                ?.let { enumByNameIgnoreCaseOrNull<VehiclePowerSource>(it) }
                ?.let { fragmentServiceProcessPowerSource.text = getString(it.label) }
            orderService.vehicle?.transmission
                ?.let { enumByNameIgnoreCaseOrNull<VehicleTransmission>(it) }
                ?.let { fragmentServiceProcessTransmission.text = getString(it.label) }
            fragmentServiceProcessWheelDrive.text = orderService.vehicle?.wheelDrive ?: "-"
            fragmentServiceProcessProductionYear.text = orderService.vehicle?.year ?: "-"
            fragmentServiceProcessSeat.text = orderService.vehicle?.seat ?: "-"
            fragmentServiceProcessPowerOutput.text = orderService.vehicle?.powerOutput ?: "-"

            // Show total price with currency format
            price = orderService.price ?: OrderService.getPriceFromOrderItems(orderItems)
            fragmentServiceProcessOrderItemsTotalPrice.text = indonesianCurrencyFormatter(price!!)

            // Set order items list
            orderItemAdapter.submitList(orderItems?.toList())

            // Current distance visibility (Usually only for Partner execution view, or if Customer wants to see distance to Partner)
            // Keeping existing logic: Visible for Partner during active states.
            fragmentServiceProcessTextViewCurrentDistance.visibility =
                if (currentUser?.role == UserRole.PARTNER && (orderService.status == OrderStatus.ON_THE_WAY || orderService.status == OrderStatus.ORDER_PLACED)) View.VISIBLE else View.GONE

            if (orderService.selectedDateMillis != null) {
                fragmentServiceProcessLinearLayoutDayContainer.visibility = View.VISIBLE
                // Convert the Double timestamp to a Long, then to a Calendar instance
                val selectedTimeInMillis = orderService.selectedDateMillis!!.toLong()
                val selectedCalendar = Calendar.getInstance().apply {
                    timeInMillis = selectedTimeInMillis
                }

                // Get today's date at midnight for accurate comparison
                val todayCalendar = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                // Calculate the difference in days
                val diffInMillis = selectedCalendar.timeInMillis - todayCalendar.timeInMillis
                val diffInDays = TimeUnit.MILLISECONDS.toDays(diffInMillis)

                // Set the text based on the difference
                fragmentServiceProcessTextViewDay.text = when (diffInDays) {
                    0L -> getString(R.string.today)
                    1L -> getString(R.string.tomorrow)
                    -1L -> getString(R.string.yesterday)
                    in 2..Long.MAX_VALUE -> getString(R.string.in_x_days, diffInDays)
                    else -> {
                        // For past days, show "X days ago"
                        val daysAgo = -diffInDays
                        getString(R.string.x_days_ago, daysAgo)
                    }
                }

            } else {
                // If the date is null, hide the container
                fragmentServiceProcessLinearLayoutDayContainer.visibility = View.GONE
            }

        }
    }

    private fun setupMap() {
        with(binding) {
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

    private fun updateMap(partnerGeoPoint: GeoPoint? = null) {
        with(binding) {

            val selectedPoint = Point.fromLngLat(
                orderService.selectedLocationLng ?: 0.0,
                orderService.selectedLocationLat ?: 0.0
            )

            // Create Annotation API instance
            val annotationApi = fragmentServiceProcessMapView.annotations
            val pointAnnotationManager = annotationApi.createPointAnnotationManager()

            // Clear existing annotations if needed
            pointAnnotationManager.deleteAll()

            // Add marker for SELECTED LOCATION
            val selectedMarkerBitmap = BitmapFactory.decodeResource(
                resources,
                R.drawable.mp_marker  // Your existing marker for selected location
            ).scale(60, 96, false)

            val selectedAnnotationOptions: PointAnnotationOptions = PointAnnotationOptions()
                .withPoint(selectedPoint)
                .withIconImage(selectedMarkerBitmap)
                .withIconAnchor(IconAnchor.BOTTOM)

            // Add annotations to map
            pointAnnotationManager.create(selectedAnnotationOptions)

            Log.d(TAG, "updateMap: $partnerGeoPoint")

            if (partnerGeoPoint != null) {

                val partnerPoint = Point.fromLngLat(
                    partnerGeoPoint.longitude,
                    partnerGeoPoint.latitude
                )

                // Update or create user annotation
                partnerPointAnnotation?.let { existingAnnotation ->
                    // Update existing annotation
                    existingAnnotation.point = partnerPoint
                    pointAnnotationManager.update(existingAnnotation)
                } ?: run {
                    // Create new annotation
                    val partnerMarkerBitmap =
                        BitmapFactory.decodeResource(
                            resources,
                            R.drawable.mp_custom_marker  // Your existing marker for selected location
                        ).scale(80, 80, false)

                    val userAnnotationOptions: PointAnnotationOptions = PointAnnotationOptions()
                        .withPoint(partnerPoint)
                        .withIconImage(partnerMarkerBitmap)
                        .withIconAnchor(IconAnchor.CENTER)

                    partnerPointAnnotation = pointAnnotationManager.create(userAnnotationOptions)
                }

                // Optional: Recalculate bounds if you want camera to follow
                val selectedPoint = Point.fromLngLat(
                    orderService.selectedLocationLng ?: 0.0,
                    orderService.selectedLocationLat ?: 0.0
                )

                val coordinates = listOf(selectedPoint, partnerPoint)
                Log.d(TAG, "updateMap: coordinates: $coordinates")
                fragmentServiceProcessMapView.mapboxMap.loadStyle(Style.STANDARD) {
                    fragmentServiceProcessMapView.mapboxMap.cameraForCoordinates(
                        coordinates,
//                        CameraOptions.Builder().padding(EdgeInsets(100.0, 100.0, 100.0, 100.0)).build(),
                        CameraOptions.Builder().build(),
                        EdgeInsets(100.0, 100.0, 100.0, 100.0), null, null
//                        null, null, null
                    ) {
                        Log.d(TAG, "updateMap: Camera updated: $it")
                        fragmentServiceProcessMapView.mapboxMap.setCamera(it)
                    }
                }
            } else {
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
            }
        }
    }

    private fun setupListeners() {
        binding.fragmentServiceProcessButtonWhatsApp.setOnClickListener {
            var whatsAppNumber =
                when (currentUser?.role) {
                    UserRole.PARTNER -> orderService.user?.phoneNumber
                    // Customer & Admin contact the Partner
                    else -> orderService.partner?.phoneNumber
                }

            if (whatsAppNumber == null) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.there_is_no_phone_number_for_that_user),
                    Toast.LENGTH_LONG
                ).show()
                it.isEnabled = false
                return@setOnClickListener
            } else {

                val phoneUtil = PhoneNumberUtil.getInstance()
                try {
                    val numberProto = phoneUtil.parse(whatsAppNumber, "ID")
                    whatsAppNumber = "" + numberProto.countryCode + numberProto.nationalNumber
                } catch (e: Exception) {
                    // Keep original number if parsing fails
                }

                Log.d(TAG, "WhatsApp Number 2: $whatsAppNumber")

                openWhatsAppUseCase(requireContext(), whatsAppNumber)
            }
        }

        binding.fragmentServiceProcessButtonWhatsApp2.setOnClickListener {
            var whatsAppNumber =
                if (currentUser?.role == UserRole.ADMIN) orderService.user?.phoneNumber else null

            if (whatsAppNumber == null) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.there_is_no_phone_number_for_that_user),
                    Toast.LENGTH_LONG
                ).show()
                it.isEnabled = false
                return@setOnClickListener
            } else {

                val phoneUtil = PhoneNumberUtil.getInstance()
                try {
                    val numberProto = phoneUtil.parse(whatsAppNumber, "ID")
                    whatsAppNumber = "" + numberProto.countryCode + numberProto.nationalNumber
                } catch (e: Exception) {
                    // Keep original number if parsing fails
                }

                Log.d(TAG, "WhatsApp Number 2: $whatsAppNumber")

                openWhatsAppUseCase(requireContext(), whatsAppNumber)
            }
        }

        binding.fragmentServiceProcessButtonCancel.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.cancel_order)
                .setMessage(R.string.are_you_sure)
                .setPositiveButton(R.string.cancel_order) { dialog, which ->
                    observeUiStateOneShot(
                        mainGraphViewModel.updateOrderService(
                            orderService.copy(
                                updatedAt = Timestamp.now(),
                                status = OrderStatus.CANCELLED
                            )
                        )
                    ) {
                    }
                }
                .setNegativeButton(getString(R.string.dismiss)) { dialog, which ->
                    dialog.dismiss()
                }
                .show()
        }

        binding.fragmentServiceProcessButtonComplete.setOnClickListener {
            findNavController().navigate(
                ServiceProcessFragmentDirections.actionServiceProcessFragmentToOrderServiceDetailFragment(
                    orderService, currentUser
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

                    if (orderService.status?.serviceNextProcess() == OrderStatus.REPAIRING && aerialDistanceToTargetInMeters > MINIMUM_DISTANCE_TO_START_SERVICE) {
                        when {
                            hasLocationPermission() -> {
                                val currentDistance =
                                    numberFormatterUseCase(aerialDistanceToTargetInMeters)
                                Toast.makeText(
                                    requireContext(),
                                    getString(
                                        R.string.haven_t_arrived_at_the_location_yet,
                                        currentDistance
                                    ),
                                    Toast.LENGTH_LONG
                                ).show()
                            }

                            shouldShowRequestPermissionRationale() -> {
                                showPermissionRationale()
                            }

                            else -> requestLocationPermission()
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

        binding.fragmentServiceProcessButtonChangePaymentMethod.setOnClickListener {
            val currentSelectedPaymentMethod = viewModel.preferredPaymentMethod.value
            paymentMethodBottomSheet = PaymentMethodBottomSheetFragment.newInstance(
                currentSelectedPaymentMethod.id
            )

            paymentMethodBottomSheet.show(
                parentFragmentManager,
                PaymentMethodBottomSheetFragment.TAG
            )
        }

        binding.fragmentServiceProcessButtonPaymentGuide.setOnClickListener {
            val currentSelectedPaymentMethod = viewModel.preferredPaymentMethod.value
            if (currentSelectedPaymentMethod.guideRes == null) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.no_guide_available_for_this_payment_method),
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            paymentGuideBottomSheet = PaymentGuideBottomSheetFragment.newInstance(
                guideResId = currentSelectedPaymentMethod.guideRes
            )
            paymentGuideBottomSheet.show(parentFragmentManager, PaymentGuideBottomSheetFragment.TAG)
        }

        // --- NEW: Copy Order ID ---
        binding.fragmentServiceProcessOrderId.setOnLongClickListener {
            val textToCopy = binding.fragmentServiceProcessOrderId.text.toString()
            if (textToCopy.isNotBlank() && textToCopy != "-") {
                val clipboard =
                    requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Order ID", textToCopy)
                clipboard.setPrimaryClip(clip)

                // Only show toast for Android 12 and below.
                // Android 13+ (Tiramisu) shows a system UI confirmation automatically.
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                    Toast.makeText(requireContext(), getString(R.string.copied), Toast.LENGTH_SHORT)
                        .show()
                }
            }
            true
        }
    }

    private fun updateOrderStatus(view: SlideToActView) {
        observeUiStateOneShot(
            mainGraphViewModel.updateOrderService(
                orderService.copy(
                    updatedAt = Timestamp.now(),
                    status = orderService.status?.serviceNextProcess(),
                    orderItems = orderItems,
                    price = price,
                )
            ), onEmpty = {
                view.isLocked = true
            }
        ) {
            view.isLocked = false
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
        binding.fragmentServiceProcessDividerForButtons.visibility =
            if (show) View.VISIBLE else View.GONE
    }

    private fun showActionButton(show: Boolean) {
        val materialFade = MaterialFade().apply {
            duration = 150L
        }
        TransitionManager.beginDelayedTransition(binding.root, materialFade)

        binding.fragmentServiceProcessActButton.apply {
            visibility =
                if (show) View.VISIBLE else View.GONE
            text =
                orderService.status?.serviceNextProcess()?.getServiceActionLabel(requireContext())
                    ?.capitalizeWords()
                    ?: requireContext().getString(R.string.next)
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

    /**
     * Checks for location permission first. If granted, checks for location settings.
     * This is the entry point for the partner's location setup flow.
     */
    private fun checkLocationSettingsAndStartService() {
        if (ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            // Permission is granted, now check if the GPS setting is enabled.
            checkDeviceLocationSettings()
        } else {
            // Permission is not granted, so request it.
            // The result will be handled by the 'requestPermissionLauncher'.
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    /**
     * Checks if the device's location services are enabled and either starts the tracking service
     * or prompts the user to enable them.
     */
    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun checkDeviceLocationSettings() {
        val locationRequest = com.google.android.gms.location.LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, TimeUnit.SECONDS.toMillis(30)
        ).build()

        val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)
        val client: SettingsClient = LocationServices.getSettingsClient(requireActivity())
        val task: Task<LocationSettingsResponse> = client.checkLocationSettings(builder.build())

        task.addOnSuccessListener {
            // All location settings are satisfied. The environment is ready.
            Log.d(TAG, "Location settings are satisfied.")
            if (orderService.status == OrderStatus.ORDER_PLACED) {
                updateLocation()
            }
        }

        task.addOnFailureListener { exception ->
            if (exception is ResolvableApiException) {
                // Location settings are not satisfied, but this can be fixed
                // by showing the user a dialog.
                try {
                    val intentSenderRequest =
                        IntentSenderRequest.Builder(exception.resolution).build()
                    // The result of this dialog will be handled by 'locationSettingsRequestLauncher'.
                    locationSettingsRequestLauncher.launch(intentSenderRequest)
                } catch (_: Exception) {
                    // Ignore the error.
                }
            }
        }
    }

    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun updateLocation() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val result =
                LocationServices.getFusedLocationProviderClient(requireContext())
                    .getCurrentLocation(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        CancellationTokenSource().token
                    ).await()
            result.let {
                orderService.id?.let { orderId ->
                    Log.d(TAG, "Location updated: $it")
                    viewModel.updatePartnerLiveLocation(
                        orderId,
                        it.latitude,
                        it.longitude
                    )
                }
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

    private fun handlePaymentMethodResult() {
        parentFragmentManager.setFragmentResultListener(
            PaymentMethodBottomSheetFragment.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, bundle ->
            val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                bundle.getParcelable(
                    PaymentMethodBottomSheetFragment.KEY_PAYMENT_METHOD,
                    PaymentMethod::class.java
                )
            } else {
                bundle.getParcelable(PaymentMethodBottomSheetFragment.KEY_PAYMENT_METHOD)
            }
            if (result != null) {
                viewModel.onPaymentMethodSelected(result)

                if (!::orderService.isInitialized) return@setFragmentResultListener

                observeUiStateOneShot(
                    mainGraphViewModel.updateOrderService(
                        orderService.copy(paymentMethod = result.id)
                    )
                ) {}
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mainGraphViewModel.stopObservingOpenedOrder()
        stopObservingPartnerLiveLocation()
    }

    override val progressIndicator: LinearProgressIndicator
        get() = binding.fragmentServiceProcessLinearProgressIndicator

    override fun getCurrentOrderService(): OrderService? {
        // Return the local variable we already have populated
        return if (::orderService.isInitialized) orderService else null
    }

    // on below line we are creating a function to get bitmap
// from image and passing params as context and an int for drawable.
    private fun getBitmapFromImage(context: Context, drawable: Int): Bitmap {
// Get the drawable
        val db = ContextCompat.getDrawable(context, drawable)

        // Get theme colors - alternative method using MaterialColors if available
        val secondaryContainerColor = MaterialColors.getColor(
            context,
            com.google.android.material.R.attr.colorTertiaryContainer,
            Color.GRAY
        )
        val onSecondaryContainerColor = MaterialColors.getColor(
            context,
            com.google.android.material.R.attr.colorOnTertiaryContainer,
            Color.BLACK
        )

        // Create bitmap
        val bit = createBitmap(db!!.intrinsicWidth, db.intrinsicHeight)

        // Create canvas
        val canvas = Canvas(bit)

        // Draw rounded background
        val cornerRadius = dpToPx(context, 8f) // 8dp corner radius
        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = secondaryContainerColor
            style = Paint.Style.FILL
        }

        canvas.drawRect(
            0f, 0f,
            canvas.width.toFloat(), canvas.height.toFloat(),
//            cornerRadius, cornerRadius,
            backgroundPaint
        )

        // Apply color filter to drawable
        db.setBounds(0, 0, canvas.width, canvas.height)
        db.colorFilter = PorterDuffColorFilter(onSecondaryContainerColor, PorterDuff.Mode.SRC_ATOP)

        // Draw drawable
        db.draw(canvas)

        return bit
    }

    private fun dpToPx(context: Context, dp: Float): Float {
        return dp * context.resources.displayMetrics.density
    }
}