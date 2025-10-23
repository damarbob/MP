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
import id.monpres.app.model.MapsActivityExtraData
import id.monpres.app.model.OrderService
import id.monpres.app.model.Service
import id.monpres.app.model.Vehicle
import id.monpres.app.repository.PartnerRepository
import id.monpres.app.ui.baseservice.BaseServiceViewModel
import id.monpres.app.ui.partnerselection.PartnerSelectionFragment
import javax.inject.Inject

/**
 * An abstract base class for fragments that handle service ordering.
 * It provides common functionality for selecting a vehicle, issue, location,
 * and placing an order. Subclasses must implement methods to provide
 * specific UI elements and the ViewModel.
 *
 * This fragment handles:
 * - Setting up MaterialSharedAxis transitions for enter, return, exit, and reenter.
 * - Managing a list of user's vehicles and the chosen vehicle.
 * - Handling the selection of a location via an external MapsActivity using `pickLocationLauncher`.
 * - Observing changes to the selected location and updating the UI accordingly.
 * - Observing the result of placing an order and displaying appropriate toasts.
 * - Providing common validation methods for location, location consent, vehicle, and issue.
 * - Providing a common method to open the map for location selection.
 * - Providing a common method to construct and place an order.
 *
 * Subclasses are expected to:
 * - Provide implementations for abstract methods to return specific UI components (e.g., AutoCompleteTextViews, Buttons, CheckBox, TextInputLayouts).
 * - Provide an implementation for `getBaseOrderService()` to return an instance of `OrderService`.
 * - Provide an implementation for `getViewModel()` to return an instance of `BaseServiceViewModel` (or a subclass).
 * - Initialize the `binding` property with the appropriate ViewBinding instance.
 */
@AndroidEntryPoint
abstract class BaseServiceFragment : Fragment() {
    protected val TAG = this::class.java.simpleName
    protected lateinit var binding: Any
    protected var service: Service? = null
    protected var selectedPartnerId: String? = null
    protected var myVehicles: List<Vehicle> = listOf()
    protected var chosenMyVehicle: Vehicle? = null
    protected var selectedLocationPoint: Point? = null
    protected var orderPlacedCallback: OrderPlacedCallback? = null

    @Inject
    lateinit var partnerRepository: PartnerRepository

    interface OrderPlacedCallback {
        fun onSuccess(orderService: OrderService)
        fun onFailure(orderService: OrderService, throwable: Throwable)
    }

    private val pickLocationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { data ->
                val selectedLocation =
                    data.getStringExtra(MapsActivityExtraData.SELECTED_LOCATION) ?: ""
                val userLocation = data.getStringExtra(MapsActivityExtraData.USER_LOCATION) ?: ""

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
        enterTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
        exitTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
        reenterTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
    }

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

    protected fun registerOrderPlacedCallback(callback: OrderPlacedCallback) {
        orderPlacedCallback = callback
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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

            // Navigate to partner selection fragment with selected location point (if any)
            val bundle = Bundle()
            bundle.putString(PartnerSelectionFragment.KEY_SELECTED_LOCATION_POINT,
                selectedLocationPoint?.toJson()
            )

            findNavController().navigate(R.id.action_global_partnerSelectionFragment, bundle)
        }

        // Location button listeners
        getLocationSelectButton().setOnClickListener { openMap() }
        getLocationReSelectButton().setOnClickListener { openMap() }

        // Place order listener
        getPlaceOrderButton().setOnClickListener { placeOrder() }
    }

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

    protected fun openMap() {
        val intent = Intent(requireContext(), MapsActivity::class.java).apply {
            putExtra(MapsActivityExtraData.EXTRA_PICK_MODE, true)
        }
        pickLocationLauncher.launch(intent)
    }

    protected fun placeOrder() {
        val orderService = getBaseOrderService().apply {
            /* System */
            userId = Firebase.auth.currentUser?.uid
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
            userAddress = getAddressText()
            vehicle = chosenMyVehicle
            issue = getIssueAutoCompleteTextView().text.toString()
            issueDescription = getIssueDescriptionText()
        }
        getViewModel().placeOrder(orderService)
    }

    protected abstract fun getViewModel(): BaseServiceViewModel
}