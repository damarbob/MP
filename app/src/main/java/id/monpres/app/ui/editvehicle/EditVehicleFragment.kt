package id.monpres.app.ui.editvehicle

import android.graphics.Color
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
import androidx.navigation.fragment.navArgs
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.transition.MaterialContainerTransform
import dagger.hilt.android.AndroidEntryPoint
import dev.androidbroadcast.vbpd.viewBinding
import id.monpres.app.MainGraphViewModel
import id.monpres.app.R
import id.monpres.app.databinding.FragmentEditVehicleBinding
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
class EditVehicleFragment : BaseFragment(R.layout.fragment_edit_vehicle) {

    companion object {
        fun newInstance() = EditVehicleFragment()
        val TAG = EditVehicleFragment::class.java.simpleName
    }

    /* View models */
    private val viewModel: EditVehicleViewModel by viewModels()
    private val mainGraphViewModel: MainGraphViewModel by activityViewModels()

    /* Args */
    private val args: EditVehicleFragmentArgs by navArgs()

    /* Bindings */
    private val binding by viewBinding(FragmentEditVehicleBinding::bind)

    /* Variables */
    private var vehicle: Vehicle? = null
    private lateinit var vehicleTypes: List<VehicleType>
    private lateinit var vehicleTypeAdapter: ArrayAdapter<String>
    private lateinit var vehicleTransmissionAdapter: ArrayAdapter<String>
    private lateinit var vehiclePowerSourceAdapter: ArrayAdapter<String>
    private lateinit var vehicleWheelDriveAdapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set the transition for this fragment
//        enterTransition = MaterialSharedAxis(MaterialSharedAxis.X, /* forward= */ true)
//        returnTransition = MaterialSharedAxis(MaterialSharedAxis.X, /* forward= */ false)
//        exitTransition = MaterialSharedAxis(MaterialSharedAxis.X, /* forward= */ true)
//        reenterTransition = MaterialSharedAxis(MaterialSharedAxis.X, /* forward= */ false)
        sharedElementEnterTransition = MaterialContainerTransform().apply {
            drawingViewId = R.id.nav_host_fragment_activity_main
            scrimColor = Color.TRANSPARENT
//            setAllContainerColors(
//                MaterialColors.getColor(
//                    requireContext(),
//                    com.google.android.material.R.attr.colorSurfaceContainer,
//                    resources.getColor(
//                        R.color.md_theme_surfaceContainer,
//                        requireContext().theme
//                    )
//                )
//            )
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Get vehicle from arguments
        vehicle = args.vehicle

        // Set insets
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, windowInsets ->
            val insets =
                windowInsets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(insets.left, 0, insets.right, insets.bottom)
            windowInsets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.fragmentEditVehicleNestedScrollView) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(insets.left, 0, insets.right, insets.bottom)
            WindowInsetsCompat.CONSUMED
        }

        vehicleTypes = VehicleType.getSampleList(requireContext())
        setDropdownsOptions()
        setFormsValues()
        setupListeners()

        setFormMarks()
    }

    private fun setupListeners() {
        // Setup form validation on text change
        setupFormListener()

        binding.fragmentEditVehicleButtonSave.setOnClickListener {
            // Check if form is valid
            updateVehicle(it)
        }
    }

    private fun updateVehicle(view: View) {
        if (isFormValid()) {
            view.isEnabled = false
            val editedVehicle = Vehicle(id = vehicle?.id ?: "")
            with(binding) {
                editedVehicle.apply {
                    name =
                        fragmentEditVehicleTextInputLayoutVehicleName.editText?.text.toString()
                    registrationNumber =
                        fragmentEditVehicleTextInputLayoutVehicleRegistrationNumber.editText?.text.toString()
                    licensePlateNumber =
                        fragmentEditVehicleTextInputLayoutVehicleLicensePlateNumber.editText?.text.toString()
                    year =
                        fragmentEditVehicleTextInputLayoutVehicleYear.editText?.text.toString()
                    engineCapacity =
                        fragmentEditVehicleTextInputLayoutVehicleEngineCapacity.editText?.text.toString()
                    powerOutput =
                        fragmentEditVehicleTextInputLayoutVehiclePowerOutput.editText?.text.toString()
                    seat =
                        fragmentEditVehicleTextInputLayoutVehicleSeat.editText?.text.toString()
                    typeId = vehicleTypes.find { type ->
                        type.name.equals(
                            fragmentEditVehicleDropdownVehicleType.text.toString(),
                            true
                        )
                    }?.id
                    transmission =
                        VehicleTransmission.fromLabel(
                            requireContext(),
                            fragmentEditVehicleDropdownVehicleTransmission.text.toString()
                        )?.name
                    wheelDrive = fragmentEditVehicleDropdownVehicleWheelDrive.text.toString()
                    powerSource =
                        VehiclePowerSource.fromLabel(
                            requireContext(),
                            fragmentEditVehicleDropdownVehiclePowerSource.text.toString()
                        )?.name
                }
            }

            // Update vehicle
            observeUiStateOneShot(
                mainGraphViewModel.updateVehicle(editedVehicle),
                {
                    view.isEnabled = true
                }) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.vehicle_updated), Toast.LENGTH_SHORT
                ).show()
                findNavController().popBackStack()
            }
        } else {
            view.isEnabled = true
            with(binding) {
                when {
                    fragmentEditVehicleTextInputLayoutVehicleName.isErrorEnabled -> fragmentEditVehicleTextInputLayoutVehicleName.editText?.requestFocusAndShowKeyboard()
                    fragmentEditVehicleTextInputLayoutVehicleRegistrationNumber.isErrorEnabled -> fragmentEditVehicleTextInputLayoutVehicleRegistrationNumber.editText?.requestFocusAndShowKeyboard()
                    fragmentEditVehicleTextInputLayoutVehicleLicensePlateNumber.isErrorEnabled -> fragmentEditVehicleTextInputLayoutVehicleLicensePlateNumber.editText?.requestFocusAndShowKeyboard()
                    fragmentEditVehicleTextInputLayoutVehicleType.isErrorEnabled -> fragmentEditVehicleDropdownVehicleType.requestFocus()
                    fragmentEditVehicleTextInputLayoutVehiclePowerSource.isErrorEnabled -> fragmentEditVehicleDropdownVehiclePowerSource.requestFocus()
                    fragmentEditVehicleTextInputLayoutVehicleTransmission.isErrorEnabled -> fragmentEditVehicleDropdownVehicleTransmission.requestFocus()
                    fragmentEditVehicleTextInputLayoutVehicleWheelDrive.isErrorEnabled -> fragmentEditVehicleDropdownVehicleWheelDrive.requestFocus()
                }
            }
        }
    }

    private fun setFormsValues() {
        binding.fragmentEditVehicleTextInputLayoutVehicleName.editText?.setText(vehicle?.name)
        binding.fragmentEditVehicleTextInputLayoutVehicleRegistrationNumber.editText?.setText(
            vehicle?.registrationNumber
        )
        binding.fragmentEditVehicleTextInputLayoutVehicleLicensePlateNumber.editText?.setText(
            vehicle?.licensePlateNumber
        )
        binding.fragmentEditVehicleTextInputLayoutVehicleYear.editText?.setText(vehicle?.year)
        binding.fragmentEditVehicleTextInputLayoutVehicleEngineCapacity.editText?.setText(vehicle?.engineCapacity)
        binding.fragmentEditVehicleTextInputLayoutVehiclePowerOutput.editText?.setText(vehicle?.powerOutput)
        binding.fragmentEditVehicleTextInputLayoutVehicleSeat.editText?.setText(vehicle?.seat)

        // Set default value for vehicle type
        val selectedVehicleType = vehicleTypes.find { it.id == vehicle?.typeId }
        binding.fragmentEditVehicleDropdownVehicleType.setText(selectedVehicleType?.name, false)

        // Set default value for transmission
        // Find the corresponding enum entry for the vehicle's transmission string
        VehicleTransmission.entries.find { it.name == vehicle?.transmission }?.let { transmission ->
            // This block only executes if a non-null transmission is found
            binding.fragmentEditVehicleDropdownVehicleTransmission.setText(
                getString(transmission.label),
                false
            )
        }

        // Set default value for wheel drive
        binding.fragmentEditVehicleDropdownVehicleWheelDrive.setText(vehicle?.wheelDrive, false)

        // Set default value for power source
        VehiclePowerSource.entries.find { it.name == vehicle?.powerSource }?.let { powerSource ->
            binding.fragmentEditVehicleDropdownVehiclePowerSource.setText(
                getString(powerSource.label),
                false
            )
        }
    }

    private fun setDropdownsOptions() {
        // Type dropdown
        vehicleTypeAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            vehicleTypes.map { it.name })
        binding.fragmentEditVehicleDropdownVehicleType.setAdapter(vehicleTypeAdapter)

        // Transmission dropdown
        vehicleTransmissionAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            VehicleTransmission.toListString(requireContext())
        )
        binding.fragmentEditVehicleDropdownVehicleTransmission.setAdapter(vehicleTransmissionAdapter)

        // Wheel drive dropdown
        vehicleWheelDriveAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            VehicleWheelDrive.toListString()
        )
        binding.fragmentEditVehicleDropdownVehicleWheelDrive.setAdapter(vehicleWheelDriveAdapter)

        // Power source dropdown
        vehiclePowerSourceAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            VehiclePowerSource.toListString(requireContext())
        )
        binding.fragmentEditVehicleDropdownVehiclePowerSource.setAdapter(vehiclePowerSourceAdapter)
    }

    private fun setupFormListener() {
        // Setup form validation on text change
        with(binding) {
            fragmentEditVehicleTextInputLayoutVehicleName.editText?.addTextChangedListener { if (fragmentEditVehicleTextInputLayoutVehicleName.isErrorEnabled) validateName() }
            fragmentEditVehicleTextInputLayoutVehicleRegistrationNumber.editText?.addTextChangedListener { if (fragmentEditVehicleTextInputLayoutVehicleRegistrationNumber.isErrorEnabled) validateRegistrationNumber() }
            fragmentEditVehicleTextInputLayoutVehicleLicensePlateNumber.editText?.addTextChangedListener { if (fragmentEditVehicleTextInputLayoutVehicleLicensePlateNumber.isErrorEnabled) validateLicensePlateNumber() }
            fragmentEditVehicleDropdownVehicleType.addTextChangedListener { validateVehicleType() }
            fragmentEditVehicleDropdownVehiclePowerSource.addTextChangedListener { validateVehiclePowerSource() }
            fragmentEditVehicleDropdownVehicleTransmission.addTextChangedListener { validateVehicleTransmission() }
            fragmentEditVehicleDropdownVehicleWheelDrive.addTextChangedListener { validateVehicleWheelDrive() }

            fragmentEditVehicleTextInputLayoutVehicleName.editText?.setOnFocusChangeListener { v, hasFocus ->
                if (!hasFocus) {
                    validateName()
                    fragmentEditVehicleTextInputLayoutVehicleName.editText?.hint = null
                } else {
                    fragmentEditVehicleTextInputLayoutVehicleName.editText?.hint = getString(R.string.brand_name)
                    fragmentEditVehicleTextInputLayoutVehicleName.editText?.showKeyboard()
                }
            }
            fragmentEditVehicleTextInputLayoutVehicleRegistrationNumber.editText?.setOnFocusChangeListener { v, hasFocus ->
                if (!hasFocus) {
                    validateRegistrationNumber()
                }
            }
            fragmentEditVehicleTextInputLayoutVehicleLicensePlateNumber.editText?.setOnFocusChangeListener { v, hasFocus ->
                if (!hasFocus) {
                    validateLicensePlateNumber()
                }
            }

            listOf(
                fragmentEditVehicleDropdownVehicleType,
                fragmentEditVehicleDropdownVehicleTransmission,
                fragmentEditVehicleDropdownVehicleWheelDrive,
                fragmentEditVehicleDropdownVehiclePowerSource,
            ).forEach { autoCompleteTextView ->
                autoCompleteTextView.onFocusChangeListener =
                    View.OnFocusChangeListener { v, hasFocus ->
                        if (hasFocus) {
                            v.hideKeyboard()
                        }
                    }
            }

            fragmentEditVehicleTextInputLayoutVehiclePowerOutput.editText?.setOnEditorActionListener { editText, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    updateVehicle(editText)
                    true
                } else {
                    false
                }
            }
        }
    }

    private fun setFormMarks() {
        binding.apply {
            fragmentEditVehicleTextInputLayoutVehicleName.markRequiredInRed()
            fragmentEditVehicleTextInputLayoutVehicleRegistrationNumber.markRequiredInRed()
            fragmentEditVehicleTextInputLayoutVehicleLicensePlateNumber.markRequiredInRed()
            fragmentEditVehicleTextInputLayoutVehicleType.markRequiredInRed()
            fragmentEditVehicleTextInputLayoutVehiclePowerSource.markRequiredInRed()
            fragmentEditVehicleTextInputLayoutVehicleTransmission.markRequiredInRed()
            fragmentEditVehicleTextInputLayoutVehicleWheelDrive.markRequiredInRed()
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
        return if (binding.fragmentEditVehicleTextInputLayoutVehicleName.editText?.text.isNullOrBlank()) {
            binding.fragmentEditVehicleTextInputLayoutVehicleName.error =
                getString(R.string.x_is_required, getString(R.string.name))
            false
        } else {
            binding.fragmentEditVehicleTextInputLayoutVehicleName.apply {
                error = null
                isErrorEnabled = false
            }
            true
        }
    }

    private fun validateRegistrationNumber(): Boolean {
        // Validate registration number (required)
        return if (binding.fragmentEditVehicleTextInputLayoutVehicleRegistrationNumber.editText?.text.isNullOrBlank()) {
            binding.fragmentEditVehicleTextInputLayoutVehicleRegistrationNumber.error =
                getString(R.string.x_is_required, getString(R.string.registration_number))
            false
        } else {
            binding.fragmentEditVehicleTextInputLayoutVehicleRegistrationNumber.apply {
                error = null
                isErrorEnabled = false
            }
            true
        }
    }

    private fun validateLicensePlateNumber(): Boolean {
        // Validate license plate number (required)
        return if (binding.fragmentEditVehicleTextInputLayoutVehicleLicensePlateNumber.editText?.text.isNullOrBlank()) {
            binding.fragmentEditVehicleTextInputLayoutVehicleLicensePlateNumber.error =
                getString(R.string.x_is_required, getString(R.string.license_plate_number))
            false
        } else {
            binding.fragmentEditVehicleTextInputLayoutVehicleLicensePlateNumber.apply {
                error = null
                isErrorEnabled = false
            }
            true
        }
    }

    private fun validateVehicleType(): Boolean {
        // Validate vehicle type (required and valid vehicle type)
        return if (binding.fragmentEditVehicleDropdownVehicleType.text.isNullOrBlank()) {
            binding.fragmentEditVehicleTextInputLayoutVehicleType.error =
                getString(R.string.x_is_required, getString(R.string.type))
            false
        } else if (!vehicleTypes.any {
                it.name.equals(binding.fragmentEditVehicleDropdownVehicleType.text.toString(), true)
            }) {
            binding.fragmentEditVehicleTextInputLayoutVehicleType.error =
                getString(R.string.x_is_invalid, getString(R.string.type))
            false
        } else {
            binding.fragmentEditVehicleTextInputLayoutVehicleType.apply {
                error = null
                isErrorEnabled = false
            }
            true
        }
    }

    private fun validateVehiclePowerSource(): Boolean {
        // Validate vehicle power source (required and valid power source)
        return if (binding.fragmentEditVehicleDropdownVehiclePowerSource.text.isNullOrBlank()) {
            binding.fragmentEditVehicleTextInputLayoutVehiclePowerSource.error =
                getString(R.string.x_is_required, getString(R.string.power_source))
            false
        } else if (!VehiclePowerSource.toListString(requireContext()).any {
                it.equals(
                    binding.fragmentEditVehicleDropdownVehiclePowerSource.text.toString(),
                    true
                )
            }) {
            binding.fragmentEditVehicleTextInputLayoutVehiclePowerSource.error =
                getString(R.string.x_is_invalid, getString(R.string.power_source))
            false
        } else {
            binding.fragmentEditVehicleTextInputLayoutVehiclePowerSource.apply {
                error = null
                isErrorEnabled = false
            }
            true
        }
    }

    private fun validateVehicleTransmission(): Boolean {
        // Validate vehicle transmission (required and valid transmission)
        return if (binding.fragmentEditVehicleDropdownVehicleTransmission.text.isNullOrBlank()) {
            binding.fragmentEditVehicleTextInputLayoutVehicleTransmission.error =
                getString(R.string.x_is_required, getString(R.string.transmission))
            false
        } else if (!VehicleTransmission.toListString(requireContext()).any {
                it.equals(
                    binding.fragmentEditVehicleDropdownVehicleTransmission.text.toString(),
                    true
                )
            }) {
            binding.fragmentEditVehicleTextInputLayoutVehicleTransmission.error =
                getString(R.string.x_is_invalid, getString(R.string.transmission))
            false
        } else {
            binding.fragmentEditVehicleTextInputLayoutVehicleTransmission.apply {
                error = null
                isErrorEnabled = false
            }
            true
        }
    }

    private fun validateVehicleWheelDrive(): Boolean {
        // Validate vehicle wheel drive (required and valid wheel drive)
        return if (binding.fragmentEditVehicleDropdownVehicleWheelDrive.text.isNullOrBlank()) {
            binding.fragmentEditVehicleTextInputLayoutVehicleWheelDrive.error =
                getString(R.string.x_is_required, getString(R.string.wheel_drive))
            false
        } else if (!VehicleWheelDrive.toListString().any {
                it.equals(
                    binding.fragmentEditVehicleDropdownVehicleWheelDrive.text.toString(),
                    true
                )
            }) {
            binding.fragmentEditVehicleTextInputLayoutVehicleWheelDrive.error =
                getString(R.string.x_is_invalid, getString(R.string.wheel_drive))
            false
        } else {
            binding.fragmentEditVehicleTextInputLayoutVehicleWheelDrive.apply {
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
        get() = binding.fragmentEditVehicleProgressIndicator
}
