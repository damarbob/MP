package id.monpres.app.ui.insertvehicle

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.transition.MaterialSharedAxis
import dagger.hilt.android.AndroidEntryPoint
import dev.androidbroadcast.vbpd.viewBinding
import id.monpres.app.MainGraphViewModel
import id.monpres.app.R
import id.monpres.app.databinding.FragmentInsertVehicleBinding
import id.monpres.app.enums.VehiclePowerSource
import id.monpres.app.enums.VehicleTransmission
import id.monpres.app.enums.VehicleWheelDrive
import id.monpres.app.model.Vehicle
import id.monpres.app.model.VehicleType
import id.monpres.app.ui.BaseFragment
import id.monpres.app.utils.hideKeyboard
import id.monpres.app.utils.markRequiredInRed
import id.monpres.app.utils.requestFocusAndShowKeyboard
import id.monpres.app.utils.showKeyboard

@AndroidEntryPoint
class InsertVehicleFragment : BaseFragment(R.layout.fragment_insert_vehicle) {

    companion object {
        fun newInstance() = InsertVehicleFragment()
    }

    /* View models */
    private val viewModel: InsertVehicleViewModel by viewModels()
    private val mainGraphViewModel: MainGraphViewModel by activityViewModels()

    /* Bindings */
    private val binding by viewBinding(FragmentInsertVehicleBinding::bind)

    /* Variables */
    private lateinit var vehicleTypes: List<VehicleType>
    private lateinit var vehicleTypeAdapter: ArrayAdapter<String>
    private lateinit var vehicleTransmissionAdapter: ArrayAdapter<String>
    private lateinit var vehiclePowerSourceAdapter: ArrayAdapter<String>
    private lateinit var vehicleWheelDriveAdapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Set the transition for this fragment
        enterTransition = MaterialSharedAxis(MaterialSharedAxis.X, /* forward= */ true)
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.X, /* forward= */ false)
        exitTransition = MaterialSharedAxis(MaterialSharedAxis.X, /* forward= */ true)
        reenterTransition = MaterialSharedAxis(MaterialSharedAxis.X, /* forward= */ false)

        // Set the window insets listener (so the keyboard can be detected and views not hide by keyboard)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(insets.left, 0, insets.right, insets.bottom)
            windowInsets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.fragmentInsertVehicleNestedScrollView) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(insets.left, 0, insets.right, insets.bottom)
            WindowInsetsCompat.CONSUMED
        }

        vehicleTypes = VehicleType.getSampleList(requireContext())

        setupListeners()
        setFormMarks()
    }

    private fun setupListeners() {
        // Setup form validation on text change
        setupFormListener()

        binding.fragmentInsertVehicleButtonSave.setOnClickListener {
            insertVehicle(it)
        }
    }

    private fun insertVehicle(submitButton: View) {
        if (isFormValid()) {
            submitButton.isEnabled = false
            val newVehicle = Vehicle()
            with(binding) {
                newVehicle.apply {
                    name =
                        fragmentInsertVehicleTextInputLayoutVehicleName.editText?.text.toString()
                    registrationNumber =
                        fragmentInsertVehicleTextInputLayoutVehicleRegistrationNumber.editText?.text.toString()
                    licensePlateNumber =
                        fragmentInsertVehicleTextInputLayoutVehicleLicensePlateNumber.editText?.text.toString()
                    year =
                        fragmentInsertVehicleTextInputLayoutVehicleYear.editText?.text.toString()
                    engineCapacity =
                        fragmentInsertVehicleTextInputLayoutVehicleEngineCapacity.editText?.text.toString()
                    powerOutput =
                        fragmentInsertVehicleTextInputLayoutVehiclePowerOutput.editText?.text.toString()
                    seat =
                        fragmentInsertVehicleTextInputLayoutVehicleSeat.editText?.text.toString()
                    typeId = vehicleTypes.find { vehicleType ->
                        vehicleType.name.equals(
                            fragmentInsertVehicleDropdownVehicleType.text.toString(),
                            true
                        )
                    }?.id
                    transmission =
                        VehicleTransmission.fromLabel(
                            requireContext(),
                            fragmentInsertVehicleDropdownVehicleTransmission.text.toString()
                        )?.name
                    wheelDrive = fragmentInsertVehicleDropdownVehicleWheelDrive.text.toString()
                    powerSource =
                        VehiclePowerSource.fromLabel(
                            requireContext(),
                            fragmentInsertVehicleDropdownVehiclePowerSource.text.toString()
                        )?.name
                }
            }

            observeUiStateOneShot(mainGraphViewModel.insertVehicle(newVehicle), {
                submitButton.isEnabled = true
            }) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.vehicle_added), Toast.LENGTH_SHORT
                ).show()
                findNavController().popBackStack()
            }
        } else {
            submitButton.isEnabled = true
            with(binding) {
                when {
                    fragmentInsertVehicleTextInputLayoutVehicleName.isErrorEnabled -> fragmentInsertVehicleTextInputLayoutVehicleName.editText?.requestFocusAndShowKeyboard()
                    fragmentInsertVehicleTextInputLayoutVehicleRegistrationNumber.isErrorEnabled -> fragmentInsertVehicleTextInputLayoutVehicleRegistrationNumber.editText?.requestFocusAndShowKeyboard()
                    fragmentInsertVehicleTextInputLayoutVehicleLicensePlateNumber.isErrorEnabled -> fragmentInsertVehicleTextInputLayoutVehicleLicensePlateNumber.editText?.requestFocusAndShowKeyboard()

                    fragmentInsertVehicleTextInputLayoutVehicleType.isErrorEnabled -> fragmentInsertVehicleDropdownVehicleType.requestFocus()
                    fragmentInsertVehicleTextInputLayoutVehiclePowerSource.isErrorEnabled -> fragmentInsertVehicleDropdownVehiclePowerSource.requestFocus()
                    fragmentInsertVehicleTextInputLayoutVehicleTransmission.isErrorEnabled -> fragmentInsertVehicleDropdownVehicleTransmission.requestFocus()
                    fragmentInsertVehicleTextInputLayoutVehicleWheelDrive.isErrorEnabled -> fragmentInsertVehicleDropdownVehicleWheelDrive.requestFocus()
                }
            }

        }
    }

    private fun setDropdownsOptions() {
        // Type dropdown
        vehicleTypeAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            vehicleTypes.map { it.name })
        binding.fragmentInsertVehicleDropdownVehicleType.setAdapter(vehicleTypeAdapter)

        // Transmission dropdown
        vehicleTransmissionAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            VehicleTransmission.toListString(requireContext())
        )
        binding.fragmentInsertVehicleDropdownVehicleTransmission.setAdapter(
            vehicleTransmissionAdapter
        )

        // Wheel drive dropdown
        vehicleWheelDriveAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            VehicleWheelDrive.toListString()
        )
        binding.fragmentInsertVehicleDropdownVehicleWheelDrive.setAdapter(vehicleWheelDriveAdapter)

        // Power source dropdown
        vehiclePowerSourceAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            VehiclePowerSource.toListString(requireContext())
        )
        binding.fragmentInsertVehicleDropdownVehiclePowerSource.setAdapter(vehiclePowerSourceAdapter)
    }

    private fun setupFormListener() {
        with(binding) {
            fragmentInsertVehicleTextInputLayoutVehicleName.editText?.addTextChangedListener { if (fragmentInsertVehicleTextInputLayoutVehicleName.isErrorEnabled) validateName() }
            fragmentInsertVehicleTextInputLayoutVehicleRegistrationNumber.editText?.addTextChangedListener { if (fragmentInsertVehicleTextInputLayoutVehicleRegistrationNumber.isErrorEnabled) validateRegistrationNumber() }
            fragmentInsertVehicleTextInputLayoutVehicleLicensePlateNumber.editText?.addTextChangedListener { if (fragmentInsertVehicleTextInputLayoutVehicleLicensePlateNumber.isErrorEnabled) validateLicensePlateNumber() }
            fragmentInsertVehicleDropdownVehicleType.addTextChangedListener { validateVehicleType() }
            fragmentInsertVehicleDropdownVehiclePowerSource.addTextChangedListener { validateVehiclePowerSource() }
            fragmentInsertVehicleDropdownVehicleTransmission.addTextChangedListener { validateVehicleTransmission() }
            fragmentInsertVehicleDropdownVehicleWheelDrive.addTextChangedListener { validateVehicleWheelDrive() }

            fragmentInsertVehicleTextInputLayoutVehicleName.editText?.setOnFocusChangeListener { v, hasFocus ->
                if (!hasFocus) {
                    validateName()
                    fragmentInsertVehicleTextInputLayoutVehicleName.editText?.hint = null
                } else {
                    fragmentInsertVehicleTextInputLayoutVehicleName.editText?.hint = getString(R.string.brand_name)
                    fragmentInsertVehicleTextInputLayoutVehicleName.editText?.showKeyboard()
                }
            }
            fragmentInsertVehicleTextInputLayoutVehicleRegistrationNumber.editText?.setOnFocusChangeListener { v, hasFocus ->
                if (!hasFocus) {
                    validateRegistrationNumber()
                }
            }
            fragmentInsertVehicleTextInputLayoutVehicleLicensePlateNumber.editText?.setOnFocusChangeListener { v, hasFocus ->
                if (!hasFocus) {
                    validateLicensePlateNumber()
                }
            }

            listOf(
                fragmentInsertVehicleDropdownVehicleType,
                fragmentInsertVehicleDropdownVehicleTransmission,
                fragmentInsertVehicleDropdownVehicleWheelDrive,
                fragmentInsertVehicleDropdownVehiclePowerSource,
            ).forEach { autoCompleteTextView ->
                autoCompleteTextView.setOnFocusChangeListener { v, hasFocus ->
                        if (hasFocus) {
                            v.hideKeyboard()
                        }
                    }
            }

            fragmentInsertVehicleTextInputLayoutVehiclePowerOutput.editText?.setOnEditorActionListener { editText, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    insertVehicle(editText)
                    true
                } else {
                    false
                }
            }
        }
    }

    private fun setFormMarks() {
        binding.apply {
            fragmentInsertVehicleTextInputLayoutVehicleName.markRequiredInRed()
            fragmentInsertVehicleTextInputLayoutVehicleRegistrationNumber.markRequiredInRed()
            fragmentInsertVehicleTextInputLayoutVehicleLicensePlateNumber.markRequiredInRed()
            fragmentInsertVehicleTextInputLayoutVehicleType.markRequiredInRed()
            fragmentInsertVehicleTextInputLayoutVehiclePowerSource.markRequiredInRed()
            fragmentInsertVehicleTextInputLayoutVehicleTransmission.markRequiredInRed()
            fragmentInsertVehicleTextInputLayoutVehicleWheelDrive.markRequiredInRed()
        }
    }

    private fun isFormValid(): Boolean {
        val isNameValid = validateName()
        val isRegistrationNumberValid = validateRegistrationNumber()
        val isLicensePlateNumberValid = validateLicensePlateNumber()
        val isVehicleTypeValid = validateVehicleType()
        val isVehiclePowerSourceValid = validateVehiclePowerSource()
        val isVehicleTransmissionValid = validateVehicleTransmission()
        val isVehicleWheelDriveValid = validateVehicleWheelDrive()
        return isNameValid && isRegistrationNumberValid && isLicensePlateNumberValid && isVehicleTypeValid && isVehiclePowerSourceValid && isVehicleTransmissionValid && isVehicleWheelDriveValid
    }

    private fun validateName(): Boolean {
        // Validate name (required)
        return if (binding.fragmentInsertVehicleTextInputLayoutVehicleName.editText?.text.isNullOrBlank()) {
            binding.fragmentInsertVehicleTextInputLayoutVehicleName.apply {
                error =
                    getString(R.string.x_is_required, getString(R.string.name))
            }
            false
        } else {
            binding.fragmentInsertVehicleTextInputLayoutVehicleName.apply {
                error = null
                isErrorEnabled = false
            }
            true
        }
    }

    private fun validateRegistrationNumber(): Boolean {
        // Validate registration number (required)
        return if (binding.fragmentInsertVehicleTextInputLayoutVehicleRegistrationNumber.editText?.text.isNullOrBlank()) {
            binding.fragmentInsertVehicleTextInputLayoutVehicleRegistrationNumber.error =
                getString(R.string.x_is_required, getString(R.string.registration_number))
            false
        } else {
            binding.fragmentInsertVehicleTextInputLayoutVehicleRegistrationNumber.apply {
                error = null
                isErrorEnabled = false
            }
            true
        }
    }

    private fun validateLicensePlateNumber(): Boolean {
        // Validate license plate number (required)
        return if (binding.fragmentInsertVehicleTextInputLayoutVehicleLicensePlateNumber.editText?.text.isNullOrBlank()) {
            binding.fragmentInsertVehicleTextInputLayoutVehicleLicensePlateNumber.error =
                getString(R.string.x_is_required, getString(R.string.license_plate_number))
            false
        } else {
            binding.fragmentInsertVehicleTextInputLayoutVehicleLicensePlateNumber.apply {
                error = null
                isErrorEnabled = false
            }
            true
        }
    }

    private fun validateVehicleType(): Boolean {
        // Validate vehicle type (required and valid vehicle type)
        return if (binding.fragmentInsertVehicleDropdownVehicleType.text.isNullOrBlank()) {
            binding.fragmentInsertVehicleTextInputLayoutVehicleType.error =
                getString(R.string.x_is_required, getString(R.string.type))
            false
        } else if (!vehicleTypes.any {
                it.name.equals(
                    binding.fragmentInsertVehicleDropdownVehicleType.text.toString(),
                    true
                )
            }) {
            binding.fragmentInsertVehicleTextInputLayoutVehicleType.error =
                getString(R.string.x_is_invalid, getString(R.string.type))
            false
        } else {
            binding.fragmentInsertVehicleTextInputLayoutVehicleType.apply {
                error = null
                isErrorEnabled = false
            }
            true
        }
    }

    private fun validateVehiclePowerSource(): Boolean {
        // Validate vehicle power source (required and valid power source)
        return if (binding.fragmentInsertVehicleDropdownVehiclePowerSource.text.isNullOrBlank()) {
            binding.fragmentInsertVehicleTextInputLayoutVehiclePowerSource.error =
                getString(R.string.x_is_required, getString(R.string.power_source))
            false
        } else if (!VehiclePowerSource.toListString(requireContext()).any {
                it.equals(
                    binding.fragmentInsertVehicleDropdownVehiclePowerSource.text.toString(),
                    true
                )
            }) {
            binding.fragmentInsertVehicleTextInputLayoutVehiclePowerSource.error =
                getString(R.string.x_is_invalid, getString(R.string.power_source))
            false
        } else {
            binding.fragmentInsertVehicleTextInputLayoutVehiclePowerSource.apply {
                error = null
                isErrorEnabled = false
            }
            true
        }
    }

    private fun validateVehicleTransmission(): Boolean {
        // Validate vehicle transmission (required and valid transmission)
        return if (binding.fragmentInsertVehicleDropdownVehicleTransmission.text.isNullOrBlank()) {
            binding.fragmentInsertVehicleTextInputLayoutVehicleTransmission.error =
                getString(R.string.x_is_required, getString(R.string.transmission))
            false
        } else if (!VehicleTransmission.toListString(requireContext()).any {
                it.equals(
                    binding.fragmentInsertVehicleDropdownVehicleTransmission.text.toString(),
                    true
                )
            }) {
            binding.fragmentInsertVehicleTextInputLayoutVehicleTransmission.error =
                getString(R.string.x_is_invalid, getString(R.string.transmission))
            false
        } else {
            binding.fragmentInsertVehicleTextInputLayoutVehicleTransmission.apply {
                error = null
                isErrorEnabled = false
            }
            true
        }
    }

    private fun validateVehicleWheelDrive(): Boolean {
        // Validate vehicle wheel drive (required and valid wheel drive)
        return if (binding.fragmentInsertVehicleDropdownVehicleWheelDrive.text.isNullOrBlank()) {
            binding.fragmentInsertVehicleTextInputLayoutVehicleWheelDrive.error =
                getString(R.string.x_is_required, getString(R.string.wheel_drive))
            false
        } else if (!VehicleWheelDrive.toListString().any {
                it.equals(
                    binding.fragmentInsertVehicleDropdownVehicleWheelDrive.text.toString(),
                    true
                )
            }) {
            binding.fragmentInsertVehicleTextInputLayoutVehicleWheelDrive.error =
                getString(R.string.x_is_invalid, getString(R.string.wheel_drive))
            false
        } else {
            binding.fragmentInsertVehicleTextInputLayoutVehicleWheelDrive.apply {
                error = null
                isErrorEnabled = false
            }
            true
        }
    }

    override fun onResume() {
        super.onResume()
        setDropdownsOptions()
    }

    override val progressIndicator: LinearProgressIndicator
        get() = binding.fragmentInsertVehicleProgressIndicator
}
