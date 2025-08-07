package id.monpres.app.ui.scheduledservice

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.viewModels
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import id.monpres.app.R
import id.monpres.app.databinding.FragmentScheduledServiceBinding
import id.monpres.app.model.OrderService
import id.monpres.app.ui.BaseServiceFragment
import id.monpres.app.ui.baseservice.BaseServiceViewModel
import id.monpres.app.utils.UiState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@AndroidEntryPoint
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

        // Set insets with keyboard
        ViewCompat.setOnApplyWindowInsetsListener(fragBinding.root) { v, windowInsets ->
            val insets =
                windowInsets.getInsets(WindowInsetsCompat.Type.ime() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(insets.left, 0, insets.right, insets.bottom)
            windowInsets
        }
        ViewCompat.setOnApplyWindowInsetsListener(fragBinding.scheduledServiceScrollView) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(insets.left, 0, insets.right, insets.bottom)
            WindowInsetsCompat.CONSUMED
        }

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
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
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