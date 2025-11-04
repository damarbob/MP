package id.monpres.app.ui.editvehicle

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.transition.MaterialSharedAxis
import dagger.hilt.android.AndroidEntryPoint
import id.monpres.app.MainActivity
import id.monpres.app.R
import id.monpres.app.databinding.FragmentEditVehicleBinding
import id.monpres.app.enums.VehiclePowerSource
import id.monpres.app.enums.VehicleTransmission
import id.monpres.app.enums.VehicleWheelDrive
import id.monpres.app.model.Vehicle
import id.monpres.app.model.VehicleType
import id.monpres.app.ui.BaseFragment
import id.monpres.app.utils.markRequiredInRed

@AndroidEntryPoint
class EditVehicleFragment : BaseFragment() {

    companion object {
        fun newInstance() = EditVehicleFragment()
        val TAG = EditVehicleFragment::class.java.simpleName
    }

    /* View models */
    private val viewModel: EditVehicleViewModel by viewModels()

    /* Args */
    private val args: EditVehicleFragmentArgs by navArgs()

    /* Bindings */
    private lateinit var binding: FragmentEditVehicleBinding

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
        enterTransition = MaterialSharedAxis(MaterialSharedAxis.X, /* forward= */ true)
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.X, /* forward= */ false)
        exitTransition = MaterialSharedAxis(MaterialSharedAxis.X, /* forward= */ true)
        reenterTransition = MaterialSharedAxis(MaterialSharedAxis.X, /* forward= */ false)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentEditVehicleBinding.inflate(inflater, container, false)
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

        /* Observe */
        // Observe vehicle types
        viewModel.vehicleTypes.observe(viewLifecycleOwner) {
            vehicleTypes = it
            setDropdownsOptions()
            setFormsValues()
            setupListeners()
        }

        setFormMarks()

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        (activity as MainActivity).binding.activityMainAppBarLayout.background =
            binding.root.background
//        val navController = findNavController()
//        val drawerLayout = (requireActivity() as MainActivity).drawerLayout
//        val appBarConfiguration =
//            AppBarConfiguration(navController.graph, drawerLayout = drawerLayout)
//
//        binding.fragmentEditVehicleToolbar.setupWithNavController(
//            navController,
//            appBarConfiguration
//        )
    }

    private fun setupListeners() {
        // Setup form validation on text change
        setupFormListener()

        binding.fragmentEditVehicleButtonSave.setOnClickListener {
            // Check if form is valid
            if (isFormValid()) {
                it.isEnabled = false
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
                        typeId = vehicleTypes.find {
                            it.name.equals(
                                fragmentEditVehicleDropdownVehicleType.text.toString(),
                                true
                            )
                        }?.id
                        transmission =
                            fragmentEditVehicleDropdownVehicleTransmission.text.toString()
                        wheelDrive = fragmentEditVehicleDropdownVehicleWheelDrive.text.toString()
                        powerSource = fragmentEditVehicleDropdownVehiclePowerSource.text.toString()
                    }
                }

                // Update vehicle
                observeUiStateOneShot(viewModel.updateVehicle(editedVehicle), {
                    it.isEnabled = true
                }) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.vehicle_updated), Toast.LENGTH_SHORT
                    ).show()
                    findNavController().popBackStack()
                }
            } else {
                it.isEnabled = true
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
        binding.fragmentEditVehicleDropdownVehicleTransmission.setText(vehicle?.transmission, false)

        // Set default value for wheel drive
        binding.fragmentEditVehicleDropdownVehicleWheelDrive.setText(vehicle?.wheelDrive, false)

        // Set default value for power source
        binding.fragmentEditVehicleDropdownVehiclePowerSource.setText(vehicle?.powerSource, false)
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
            VehicleTransmission.toListString()
        )
        vehicleTransmissionAdapter.setDropDownViewResource(android.R.layout.simple_dropdown_item_1line)
        binding.fragmentEditVehicleDropdownVehicleTransmission.setAdapter(vehicleTransmissionAdapter)

        // Wheel drive dropdown
        vehicleWheelDriveAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            VehicleWheelDrive.toListString()
        )
        vehicleWheelDriveAdapter.setDropDownViewResource(android.R.layout.simple_dropdown_item_1line)
        binding.fragmentEditVehicleDropdownVehicleWheelDrive.setAdapter(vehicleWheelDriveAdapter)

        // Power source dropdown
        vehiclePowerSourceAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            VehiclePowerSource.toListString()
        )
        vehiclePowerSourceAdapter.setDropDownViewResource(android.R.layout.simple_dropdown_item_1line)
        binding.fragmentEditVehicleDropdownVehiclePowerSource.setAdapter(vehiclePowerSourceAdapter)
    }

    private fun setupFormListener() {
        // Setup form validation on text change
        binding.fragmentEditVehicleTextInputLayoutVehicleName.editText?.addTextChangedListener { validateName() }
        binding.fragmentEditVehicleTextInputLayoutVehicleRegistrationNumber.editText?.addTextChangedListener { validateRegistrationNumber() }
        binding.fragmentEditVehicleTextInputLayoutVehicleLicensePlateNumber.editText?.addTextChangedListener { validateLicensePlateNumber() }
        binding.fragmentEditVehicleDropdownVehicleType.addTextChangedListener { validateVehicleType() }
        binding.fragmentEditVehicleDropdownVehiclePowerSource.addTextChangedListener { validateVehiclePowerSource() }
        binding.fragmentEditVehicleDropdownVehicleTransmission.addTextChangedListener { validateVehicleTransmission() }
        binding.fragmentEditVehicleDropdownVehicleWheelDrive.addTextChangedListener { validateVehicleWheelDrive() }
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
        } else if (!VehiclePowerSource.toListString().any {
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
        } else if (!VehicleTransmission.toListString().any {
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

    override val progressIndicator: LinearProgressIndicator
        get() = binding.fragmentEditVehicleProgressIndicator
}