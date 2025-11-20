package id.monpres.app.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.CheckBox
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.transition.MaterialSharedAxis
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.mapbox.geojson.Point
import dagger.hilt.android.AndroidEntryPoint
import id.monpres.app.MainApplication
import id.monpres.app.MapsActivity
import id.monpres.app.R
import id.monpres.app.enums.OrderStatus
import id.monpres.app.enums.PartnerCategory
import id.monpres.app.model.MapsActivityExtraData
import id.monpres.app.model.OrderService
import id.monpres.app.model.Service
import id.monpres.app.model.Vehicle
import id.monpres.app.repository.PartnerRepository
import id.monpres.app.repository.UserRepository
import id.monpres.app.ui.baseservice.BaseServiceViewModel
import id.monpres.app.ui.partnerselection.PartnerSelectionFragment
import id.monpres.app.utils.markRequiredInRed
import javax.inject.Inject

/**
 * An abstract base class for fragments that handle service ordering.
 * It provides common functionality for selecting a vehicle, issue, location,
 * and placing an order. Subclasses must implement methods to provide
 * specific UI elements and the ViewModel.
 * It also handles navigation to the partner selection screen and receives the result.
 *
 * This fragment handles:
 * - Setting up MaterialSharedAxis transitions for enter, return, exit, and reenter.
 * - Handling partner selection by listening to results from `PartnerSelectionFragment`.
 * - Managing a list of user's vehicles and the chosen vehicle.
 * - Handling the selection of a location via an external MapsActivity using `pickLocationLauncher`.
 * - Observing changes to the selected location and updating the UI accordingly.
 * - Observing the result of placing an order and displaying appropriate toasts.
 * - Providing common validation methods for location, location consent, vehicle, and issue.
 * - Providing a common method to open the map for location selection.
 * - Providing a common method to construct and place an order.
 *
 * Subclasses are expected to:
 * - Handle validation logic by calling the provided `validate...` methods before placing an order.
 * - Provide implementations for abstract methods to return specific UI components (e.g., AutoCompleteTextViews, Buttons, CheckBox, TextInputLayouts).
 * - Provide an implementation for `getBaseOrderService()` to return an instance of `OrderService`.
 * - Provide an implementation for `getViewModel()` to return an instance of `BaseServiceViewModel` (or a subclass).
 * - Initialize the `binding` property with the appropriate ViewBinding instance.
 */
@AndroidEntryPoint
abstract class BaseServiceFragment(layoutId: Int) : Fragment(layoutId) {
    protected val TAG = this::class.java.simpleName

    /* Properties */
    protected var service: Service? = null
    protected var selectedPartnerId: String? = null
    protected var myVehicles: List<Vehicle> = listOf()
    protected var chosenMyVehicle: Vehicle? = null
    protected var selectedLocationPoint: Point? = null
    protected var orderPlacedCallback: OrderPlacedCallback? = null

    /* Dependencies */
    @Inject
    lateinit var partnerRepository: PartnerRepository
    @Inject
    lateinit var userRepository: UserRepository

    /* Views */
    protected lateinit var binding: Any

    interface OrderPlacedCallback {
        fun onSuccess(orderService: OrderService)
        fun onFailure(orderService: OrderService, throwable: Throwable)
    }

    /**
     * An [androidx.activity.result.ActivityResultLauncher] for starting the [MapsActivity]
     * to pick a location.
     *
     * It uses [ActivityResultContracts.StartActivityForResult] to handle the activity result.
     * When the result is `Activity.RESULT_OK`, it extracts the selected location and the user's
     * current location from the result data.
     *
     * If the location data is valid, it updates the ViewModel with the selected location point
     * and the user's location point.
     * If the location data is missing or blank, it displays a `MaterialAlertDialog` to inform
     * the user about the failure to retrieve the location.
     */
    private val pickLocationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { data ->
                // Extract selected location and user's current location from the result data
                val selectedLocation =
                    data.getStringExtra(MapsActivityExtraData.SELECTED_LOCATION) ?: ""
                val userLocation = data.getStringExtra(MapsActivityExtraData.USER_LOCATION) ?: ""

                // If either location is missing or blank, display an error message
                if (selectedLocation.isBlank() || userLocation.isBlank()) {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle(getString(R.string.location))
                        .setMessage(getString(R.string.unable_to_get_your_location_please_try_again_with_location_permission_enabled))
                        .setPositiveButton(getString(R.string.close)) { dialog, _ ->
                            dialog.dismiss()
                        }
                        .show()
                    return@let
                }
                // Otherwise, set the selected and user location to the view model
                else {
                    Log.d(TAG, "User's location $userLocation")
                    Log.d(TAG, "Selected location $selectedLocation")
                    getViewModel().setSelectedLocationPoint(Point.fromJson(selectedLocation))
                    getViewModel().setUserLocationPoint(Point.fromJson(userLocation))
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set transition
        enterTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
        exitTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
        reenterTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set special transition to selected destinations
        findNavController().addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.profileFragment, R.id.monpresSettingFragment, R.id.orderServiceListFragment -> {
                    exitTransition = MaterialSharedAxis(MaterialSharedAxis.Y, /* forward= */ true)
                    reenterTransition =
                        MaterialSharedAxis(MaterialSharedAxis.Y, /* forward= */ false)
                }
            }
        }

        // Setup vehicle dropdown
        val vehicleInputView = getVehicleAutoCompleteTextView()
        val vehicles = myVehicles.map { it.name }
        val adapter = ArrayAdapter(requireContext(), R.layout.item_list, vehicles)
        vehicleInputView.setAdapter(adapter)
        vehicleInputView.setOnItemClickListener { _, _, position, _ ->
            chosenMyVehicle = myVehicles[position]
            Log.d(TAG, "Chosen vehicle: ${vehicleInputView.text}, object=$chosenMyVehicle")
        }

        // Setup buttons visibility
        getLocationSelectButton().visibility = View.VISIBLE
        getLocationReSelectButton().visibility = View.GONE

        // Select partner observer
        // ① Listen for the result
        parentFragmentManager.setFragmentResultListener(
            PartnerSelectionFragment.REQUEST_KEY_PARTNER_SELECTION,
            viewLifecycleOwner
        ) { _, bundle ->
            val partnerId = bundle.getString(PartnerSelectionFragment.KEY_SELECTED_USER_ID)
            // ② React to the chosen userId
            partnerId?.let {
                // Update selected partner id
                selectedPartnerId = partnerId
                getPartnerSelectionButton().text = partnerRepository.getRecordByUserId(partnerId)?.displayName
            }
        }


        // Location observer
        getViewModel().selectedLocationPoint.observe(viewLifecycleOwner) { point ->
            selectedLocationPoint = point
            if (point == null) return@observe
            getLocationSelectButton().visibility = View.GONE
            getLocationReSelectButton().visibility = View.VISIBLE
        }

        // Order result observer
        getViewModel().placeOrderResult.observe(viewLifecycleOwner) { result ->
            result?.onSuccess { orderService ->
                Toast.makeText(
                    requireContext(),
                    getString(R.string.order_placed_successfully),
                    Toast.LENGTH_SHORT
                ).show()
                if (orderService != null) {
                    orderPlacedCallback?.onSuccess(orderService)
                }
            }?.onFailure {
                val error = it.localizedMessage ?: it.message ?: "Unknown error"
                Log.e(TAG, error)
                Toast.makeText(
                    requireContext(),
                    getString(R.string.failed_to_place_order),
                    Toast.LENGTH_SHORT
                ).show()
                orderPlacedCallback?.onFailure(getBaseOrderService(), it)
            }
        }

        // Select partner button
        getPartnerSelectionButton().setOnClickListener {

            if (!validateIssue()) {
                return@setOnClickListener
            }

            // Navigate to partner selection fragment with selected location point (if any)
            val bundle = Bundle()
            bundle.putString(PartnerSelectionFragment.KEY_SELECTED_LOCATION_POINT,
                selectedLocationPoint?.toJson()
            )
            bundle.putStringArray(PartnerSelectionFragment.KEY_CATEGORIES, arrayOf(getIssueAutoCompleteTextView().text.toString()))

            findNavController().navigate(R.id.action_global_partnerSelectionFragment, bundle)
        }

        // Location button listeners
        getLocationSelectButton().setOnClickListener { openMap() }
        getLocationReSelectButton().setOnClickListener { openMap() }

        // Place order listener
        getPlaceOrderButton().setOnClickListener { placeOrder() }

        // Set form marks
        getLocationSelectButton().markRequiredInRed()
        getVehicleInputLayout().markRequiredInRed()
        getIssueInputLayout().markRequiredInRed()
        getPartnerSelectionButton().markRequiredInRed()
        getLocationConsentCheckBox().markRequiredInRed()
    }

    /**
     * Opens the map activity to allow the user to select a location.
     *
     * This function creates an intent for [MapsActivity], sets it to "pick mode",
     * and launches it using [pickLocationLauncher] to get the selected location back.
     */
    protected fun openMap() {
        val intent = Intent(requireContext(), MapsActivity::class.java).apply {
            putExtra(MapsActivityExtraData.EXTRA_PICK_MODE, true)
        }
        pickLocationLauncher.launch(intent)
    }

    /**
     * Constructs an [OrderService] object and submits it for placement via the ViewModel.
     *
     * This function gathers all necessary information from the system, user inputs, and selected
     * data points to create a comprehensive [OrderService] object. The process involves:
     * 1.  Getting a base `OrderService` object from the abstract `getBaseOrderService()` method.
     * 2.  Populating system-generated data like user ID, timestamps, and order status.
     * 3.  Creating a snapshot of the service details (type, name, description).
     * 4.  Adding delivery data, including the user's current location and the selected service location.
     * 5.  Incorporating user-provided inputs such as the selected partner, vehicle, address, issue, and issue description.
     *
     * After the `OrderService` object is fully constructed, it is passed to the ViewModel's
     * `placeOrder` method to be processed and sent to the repository. The result of this
     * operation is observed by the `placeOrderResult` LiveData.
     */
    protected fun placeOrder() {
        val orderService = getBaseOrderService().apply {
            /* System */
            userId = Firebase.auth.currentUser?.uid
            user = userRepository.getCurrentUserRecord()
            serviceId = service?.id
            status = OrderStatus.ORDER_PLACED
            createdAt = Timestamp.now()
            updatedAt = Timestamp.now()

            /* Service snapshot */
            type = MainApplication.serviceTypes?.find { it.id == service?.typeId }?.name
            name = service?.name
            description = service?.description

            /* Delivery data */
            userLocationLat = getViewModel().userLocationPoint.value?.latitude()
            userLocationLng = getViewModel().userLocationPoint.value?.longitude()
            selectedLocationLat = selectedLocationPoint?.latitude()
            selectedLocationLng = selectedLocationPoint?.longitude()

            /* User inputs */
            partnerId = selectedPartnerId
            partner = selectedPartnerId?.let { partnerRepository.getRecordByUserId(it) }
            userAddress = getAddressText()
            vehicle = chosenMyVehicle
            issue = PartnerCategory.fromLabel(requireContext(), getIssueAutoCompleteTextView().text.toString())?.name
            issueDescription = getIssueDescriptionText()
        }
        getViewModel().placeOrder(orderService)
    }

    /**
     * Registers a callback to be invoked when an order placement operation completes.
     * This allows the caller (e.g., the hosting Activity or another Fragment) to react to the
     * success or failure of the order placement.
     *
     * The provided callback will be triggered from the `placeOrderResult` LiveData observer
     * within this fragment.
     * - `onSuccess` is called when the order is successfully placed.
     * - `onFailure` is called when the order placement fails.
     *
     * @param callback An implementation of [OrderPlacedCallback] that defines the actions
     *                 to be taken on success or failure.
     */
    protected fun registerOrderPlacedCallback(callback: OrderPlacedCallback) {
        orderPlacedCallback = callback
    }

    /* Validation */

    protected fun isValidated(): Boolean =
        validateLocation() && validateLocationConsent() && validateVehicle() && validateIssue() && validateSelectedPartner()

    protected fun validateLocation(): Boolean {
        return if (selectedLocationPoint == null) {
            getLocationSelectButton().error = ""
            getLocationReSelectButton().error = ""
            false
        } else {
            getLocationSelectButton().error = null
            getLocationReSelectButton().error = null
            true
        }
    }

    protected fun validateLocationConsent(): Boolean {
        val consented = getLocationConsentCheckBox().isChecked
        return if (!consented) {
            getLocationConsentCheckBox().error = getString(R.string.this_field_is_required)
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.location))
                .setMessage(getString(R.string.you_must_agree_to_share_your_location_before_placing_an_order))
                .setNeutralButton(getString(R.string.close), null)
                .show()
            false
        } else {
            getLocationConsentCheckBox().error = null
            true
        }
    }

    protected fun validateVehicle(): Boolean {
        val text = getVehicleAutoCompleteTextView().text.toString()
        return if (text.isBlank()) {
            getVehicleInputLayout().error = getString(R.string.this_field_is_required)
            false
        } else {
            getVehicleInputLayout().isErrorEnabled = false
            true
        }
    }

    protected fun validateIssue(): Boolean {
        val text = getIssueAutoCompleteTextView().text.toString()
        return if (text.isBlank()) {
            getIssueInputLayout().error = getString(R.string.this_field_is_required)
            false
        } else {
            getIssueInputLayout().isErrorEnabled = false
            true
        }
    }

    protected fun validateSelectedPartner(): Boolean {
        return if (selectedPartnerId == null) {
            getPartnerSelectionButton().error = getString(R.string.this_field_is_required)
            false
        } else {
            getPartnerSelectionButton().error = null
            true
        }
    }

    /* End of validation */

    /* UI elements */

    abstract fun getPartnerSelectionButton(): Button
    abstract fun getVehicleAutoCompleteTextView(): AutoCompleteTextView
    abstract fun getIssueAutoCompleteTextView(): AutoCompleteTextView
    abstract fun getAddressText(): String
    abstract fun getIssueDescriptionText(): String
    abstract fun getLocationSelectButton(): Button
    abstract fun getLocationReSelectButton(): Button
    abstract fun getLocationConsentCheckBox(): CheckBox
    abstract fun getVehicleInputLayout(): TextInputLayout
    abstract fun getIssueInputLayout(): TextInputLayout
    abstract fun getPlaceOrderButton(): Button

    /* End of UI elements */

    /**
     * Abstract method to be implemented by subclasses.
     * This method should return an instance of `OrderService` which represents
     * the basic structure of the order being placed. Subclasses might return
     * a specific type of `OrderService` (e.g., `RepairOrderService`, `TowingOrderService`).
     * This base order object will then be populated with details like user information,
     * service details, location, and user inputs before being sent to the ViewModel
     * for processing.
     *
     * @return An instance of [OrderService] or its subclass.
     */
    abstract fun getBaseOrderService(): OrderService

    /**
     * Abstract method that must be implemented by subclasses to provide their specific
     * instance of [BaseServiceViewModel] or a subclass.
     *
     * This ViewModel is crucial for the base fragment's functionality, as it holds the state
     * for the selected location, user location, and handles the logic for placing an order.
     * The base fragment observes LiveData from this ViewModel to react to UI changes and
     * operation results.
     *
     * @return An instance of [BaseServiceViewModel] or its subclass.
     */
    protected abstract fun getViewModel(): BaseServiceViewModel
}