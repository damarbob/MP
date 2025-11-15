package id.monpres.app.ui.quickservice

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import dagger.hilt.android.AndroidEntryPoint
import dev.androidbroadcast.vbpd.viewBinding
import id.monpres.app.MainApplication
import id.monpres.app.MainGraphViewModel
import id.monpres.app.R
import id.monpres.app.databinding.FragmentQuickServiceBinding
import id.monpres.app.model.OrderService
import id.monpres.app.ui.BaseServiceFragment
import id.monpres.app.ui.baseservice.BaseServiceViewModel
import kotlinx.coroutines.launch

@AndroidEntryPoint
class QuickServiceFragment : BaseServiceFragment(R.layout.fragment_quick_service) {
    private val viewModel: QuickServiceViewModel by viewModels()
    private val mainGraphViewModel: MainGraphViewModel by activityViewModels()

    private val fragBinding by viewBinding(FragmentQuickServiceBinding::bind)

    private val args: QuickServiceFragmentArgs by navArgs()

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

        service = MainApplication.services?.find { it.id == args.serviceId }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainGraphViewModel.userVehicles.collect { vehicles ->
                    myVehicles = vehicles
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