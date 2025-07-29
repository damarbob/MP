package id.monpres.app.ui.scheduledservice

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import id.monpres.app.R
import id.monpres.app.databinding.FragmentScheduledServiceBinding
import id.monpres.app.model.OrderService
import id.monpres.app.ui.BaseServiceFragment
import id.monpres.app.ui.baseservice.BaseServiceViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ScheduledServiceFragment : BaseServiceFragment() {
    private val viewModel: ScheduledServiceViewModel by viewModels()

    private lateinit var fragBinding: FragmentScheduledServiceBinding
    private var selectedDateMillis: Long? = null
    private val datePicker by lazy {
        MaterialDatePicker.Builder.datePicker()
            .setTitleText(getString(R.string.select_date))
            .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
            .build()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        fragBinding = FragmentScheduledServiceBinding.inflate(inflater, container, false)
        binding = fragBinding
        return fragBinding.root
    }

    override fun getBaseOrderService() = OrderService().apply {
        selectedDateMillis = this@ScheduledServiceFragment.selectedDateMillis?.toDouble()
    }

    override fun getVehicleAutoCompleteTextView() = fragBinding.scheduledServiceAutoCompleteVehicle
    override fun getIssueAutoCompleteTextView() = fragBinding.scheduledServiceAutoCompleteIssue
    override fun getAddressText() = fragBinding.scheduledServiceInputEditLocation.text.toString()
    override fun getIssueDescriptionText() =
        fragBinding.scheduledServiceInputEditIssueDescription.text.toString()

    override fun getLocationSelectButton() = fragBinding.scheduledServiceButtonSelectLocation
    override fun getLocationReSelectButton() = fragBinding.scheduledServiceButtonReSelectLocation
    override fun getLocationConsentCheckBox() = fragBinding.scheduledServiceCheckBoxLocationConsent
    override fun getVehicleInputLayout() = fragBinding.scheduledServiceInputLayoutVehicle
    override fun getIssueInputLayout() = fragBinding.scheduledServiceInputLayoutIssue
    override fun getPlaceOrderButton() = fragBinding.scheduledServiceButtonPlaceOrder

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        datePicker.addOnPositiveButtonClickListener { millis ->
            selectedDateMillis = millis
            val formattedDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(millis)
            fragBinding.scheduledServiceButtonSelectDate.text = formattedDate
        }

        fragBinding.scheduledServiceButtonSelectDate.setOnClickListener {
            datePicker.show(parentFragmentManager, TAG)
        }

        fragBinding.scheduledServiceButtonPlaceOrder.setOnClickListener {
            if (validateDate() &&
                validateLocation() &&
                validateLocationConsent() &&
                validateVehicle() &&
                validateIssue()
            ) {
                placeOrder()
            } else Log.d(TAG, "Validation failed")
        }
    }

    private fun validateDate(): Boolean {
        if (selectedDateMillis == null) {
            fragBinding.scheduledServiceButtonSelectDate.error =
                getString(R.string.this_field_is_required)
            return false
        }

        // Calendar setup
        val selectedCal = Calendar.getInstance().apply { timeInMillis = selectedDateMillis!! }
        val todayCal = Calendar.getInstance()
        val yesterdayCal = Calendar.getInstance().apply { add(Calendar.DATE, -1) }

        // Reset time component
        fun Calendar.resetTime() = apply {
            set(Calendar.HOUR_OF_DAY, 0);
            set(Calendar.MINUTE, 0);
            set(Calendar.SECOND, 0);
            set(Calendar.MILLISECOND, 0)
        }

        // Compare dates
        if (selectedCal.resetTime().timeInMillis <= todayCal.resetTime().timeInMillis) {
            fragBinding.scheduledServiceButtonSelectDate.error =
                getString(R.string.you_cannot_select_today_or_yesterday)
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.date))
                .setMessage(getString(R.string.you_cannot_select_today_or_yesterday))
                .setNeutralButton(getString(R.string.close), null)
                .show()
            return false
        } else {
            fragBinding.scheduledServiceButtonSelectDate.error = null
            return true
        }
    }

    override fun getViewModel(): BaseServiceViewModel {
        return viewModel
    }
}