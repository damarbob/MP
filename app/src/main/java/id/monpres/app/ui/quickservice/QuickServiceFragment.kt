package id.monpres.app.ui.quickservice

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import id.monpres.app.databinding.FragmentQuickServiceBinding
import id.monpres.app.model.OrderService
import id.monpres.app.ui.BaseServiceFragment
import id.monpres.app.ui.baseservice.BaseServiceViewModel

class QuickServiceFragment : BaseServiceFragment() {
    private val viewModel: QuickServiceViewModel by viewModels()

    private lateinit var fragBinding: FragmentQuickServiceBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        fragBinding = FragmentQuickServiceBinding.inflate(inflater, container, false)
        binding = fragBinding
        return fragBinding.root
    }

    override fun getBaseOrderService() = OrderService()
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

        // Validate common inputs and place order
        fragBinding.quickServiceButtonPlaceOrder.setOnClickListener {
            if (validateLocation() &&
                validateLocationConsent() &&
                validateVehicle() &&
                validateIssue()
            ) {
                placeOrder()
            } else Log.d(TAG, "Validation failed")
        }
    }

    override fun getViewModel(): BaseServiceViewModel {
        return viewModel
    }
}