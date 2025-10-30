package id.monpres.app.ui.quickservice

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import dagger.hilt.android.AndroidEntryPoint
import id.monpres.app.MainApplication
import id.monpres.app.R
import id.monpres.app.databinding.FragmentQuickServiceBinding
import id.monpres.app.model.OrderService
import id.monpres.app.state.UiState
import id.monpres.app.ui.BaseServiceFragment
import id.monpres.app.ui.baseservice.BaseServiceViewModel

@AndroidEntryPoint
class QuickServiceFragment : BaseServiceFragment() {
    private val viewModel: QuickServiceViewModel by viewModels()

    private lateinit var fragBinding: FragmentQuickServiceBinding

    private val args: QuickServiceFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        fragBinding = FragmentQuickServiceBinding.inflate(inflater, container, false)

        // Set insets with keyboard
        ViewCompat.setOnApplyWindowInsetsListener(fragBinding.root) { v, windowInsets ->
            val insets =
                windowInsets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(insets.left, 0, insets.right, insets.bottom)
            windowInsets
        }
        ViewCompat.setOnApplyWindowInsetsListener(fragBinding.quickServiceScrollView) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(insets.left, 0, insets.right, insets.bottom)
            WindowInsetsCompat.CONSUMED
        }

        binding = fragBinding
        return fragBinding.root
    }

    override fun getBaseOrderService() = OrderService()

    override fun getPartnerSelectionButton() = fragBinding.quickServiceButtonSelectPartner
    override fun getVehicleAutoCompleteTextView() = fragBinding.quickServiceAutoCompleteVehicle
    override fun getIssueAutoCompleteTextView() = fragBinding.quickServiceAutoCompleteIssue
    override fun getAddressText() = fragBinding.quickServiceInputEditLocation.text.toString()
    override fun getIssueDescriptionText() =
        fragBinding.quickServiceInputEditIssueDescription.text.toString()

    override fun getLocationSelectButton() = fragBinding.quickServiceButtonSelectLocation
    override fun getLocationReSelectButton() = fragBinding.quickServiceButtonReSelectLocation
    override fun getLocationConsentCheckBox() = fragBinding.quickServiceCheckBoxLocationConsent
    override fun getVehicleInputLayout() = fragBinding.quickServiceInputLayoutVehicle
    override fun getIssueInputLayout() = fragBinding.quickServiceInputLayoutIssue
    override fun getPlaceOrderButton() = fragBinding.quickServiceButtonPlaceOrder

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        service = MainApplication.services?.find { it.id == args.serviceId }

        viewModel.getVehicles().observe(viewLifecycleOwner) { state ->
            when (state) {
                is UiState.Loading -> Log.d(TAG, "Loading vehicles")
                is UiState.Success -> {
                    myVehicles = state.data
                    val adapter = ArrayAdapter(
                        requireContext(),
                        R.layout.item_list,
                        myVehicles.map { it.name })
                    val vehicleInputView = getVehicleAutoCompleteTextView()
                    vehicleInputView.setAdapter(adapter)
                    vehicleInputView.setOnItemClickListener { _, _, position, _ ->
                        chosenMyVehicle = myVehicles[position]
                        Log.d(
                            TAG,
                            "Chosen vehicle: ${vehicleInputView.text}, object=$chosenMyVehicle"
                        )
                    }
                }

                is UiState.Error -> Log.e(
                    TAG,
                    "Failed to load vehicles: ${state.exception?.message}"
                )
            }
        }

        // Validate common inputs and place order
        fragBinding.quickServiceButtonPlaceOrder.setOnClickListener {
            if (
                validateSelectedPartner() &&
                validateLocation() &&
                validateLocationConsent() &&
                validateVehicle() &&
                validateIssue()
            ) {
                showLoading(true)
                fragBinding.quickServiceButtonPlaceOrder.isEnabled = false
                placeOrder()
            } else Log.d(TAG, "Validation failed")
        }

        registerOrderPlacedCallback(object : OrderPlacedCallback {
            override fun onSuccess(orderService: OrderService) {
                orderService.id?.let {
                    findNavController().navigate(
                        QuickServiceFragmentDirections.actionQuickServiceFragmentToServiceProcessFragment(
                            it
                        )
                    )
                }
            }

            override fun onFailure(
                orderService: OrderService,
                throwable: Throwable
            ) {
                showLoading(false)
                fragBinding.quickServiceButtonPlaceOrder.isEnabled = true
            }
        })
    }

    fun showLoading(show: Boolean) {
        fragBinding.quickServiceProgressIndicator.visibility = if (show) View.VISIBLE else View.GONE
    }

    override fun getViewModel(): BaseServiceViewModel {
        return viewModel
    }
}