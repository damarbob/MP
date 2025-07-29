package id.monpres.app.ui.insertvehicle

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.transition.MaterialSharedAxis
import dagger.hilt.android.AndroidEntryPoint
import id.monpres.app.MainActivity
import id.monpres.app.R
import id.monpres.app.databinding.FragmentInsertVehicleBinding
import id.monpres.app.enums.VehiclePowerSource
import id.monpres.app.enums.VehicleTransmission
import id.monpres.app.enums.VehicleWheelDrive
import id.monpres.app.model.Vehicle
import id.monpres.app.model.VehicleType
import id.monpres.app.ui.BaseFragment
import id.monpres.app.ui.insets.InsetsWithKeyboardCallback

@AndroidEntryPoint
class InsertVehicleFragment : BaseFragment() {

    companion object {
        fun newInstance() = InsertVehicleFragment()
    }

    /* View models */
    private val viewModel: InsertVehicleViewModel by viewModels()

    /* Bindings */
    private lateinit var binding: FragmentInsertVehicleBinding

    /* Variables */
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

        binding = FragmentInsertVehicleBinding.inflate(inflater, container, false)

        // Set the window insets listener (so the keyboard can be detected and views not hide by keyboard)
        val insetsWithKeyboardCallback = InsetsWithKeyboardCallback(requireActivity().window, 0, null)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root, insetsWithKeyboardCallback)

        /* Observe */
        // Observe vehicle types
        viewModel.vehicleTypes.observe(viewLifecycleOwner) {
            vehicleTypes = it
            setDropdownsOptions()
        }

        setupListeners()

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as MainActivity).binding.activityMainAppBarLayout.background = binding.root.background
//        val navController = findNavController()
//        val drawerLayout = (requireActivity() as MainActivity).drawerLayout
//        val appBarConfiguration =
//            AppBarConfiguration(navController.graph, drawerLayout = drawerLayout)
//
//        binding.fragmentInsertVehicleToolbar.setupWithNavController(
//            navController,
//            appBarConfiguration
//        )
    }

    private fun setupListeners() {
        // Setup form validation on text change
        setupFormListener()

        binding.fragmentInsertVehicleButtonSave.setOnClickListener {
            if (isFormValid()) {
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
                        typeId = vehicleTypes.find {
                            it.name.equals(
                                fragmentInsertVehicleDropdownVehicleType.text.toString(),
                                true
                            )
                        }?.id
                        transmission =
                            fragmentInsertVehicleDropdownVehicleTransmission.text.toString()
                        wheelDrive = fragmentInsertVehicleDropdownVehicleWheelDrive.text.toString()
                        powerSource = fragmentInsertVehicleDropdownVehiclePowerSource.text.toString()
                    }
                }

                observeUiStateOneShot(viewModel.insertVehicle(newVehicle)) {
                    Toast.makeText(requireContext(), "Vehicle added", Toast.LENGTH_SHORT).show()
                    findNavController().popBackStack()
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
            VehicleTransmission.toListString()
        )
        vehicleTransmissionAdapter.setDropDownViewResource(android.R.layout.simple_dropdown_item_1line)
        binding.fragmentInsertVehicleDropdownVehicleTransmission.setAdapter(vehicleTransmissionAdapter)

        // Wheel drive dropdown
        vehicleWheelDriveAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            VehicleWheelDrive.toListString()
        )
        vehicleWheelDriveAdapter.setDropDownViewResource(android.R.layout.simple_dropdown_item_1line)
        binding.fragmentInsertVehicleDropdownVehicleWheelDrive.setAdapter(vehicleWheelDriveAdapter)

        // Power source dropdown
        vehiclePowerSourceAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            VehiclePowerSource.toListString()
        )
        vehiclePowerSourceAdapter.setDropDownViewResource(android.R.layout.simple_dropdown_item_1line)
        binding.fragmentInsertVehicleDropdownVehiclePowerSource.setAdapter(vehiclePowerSourceAdapter)
    }

    private fun setupFormListener() {
        binding.fragmentInsertVehicleTextInputLayoutVehicleName.editText?.addTextChangedListener { validateName() }
        binding.fragmentInsertVehicleTextInputLayoutVehicleRegistrationNumber.editText?.addTextChangedListener { validateRegistrationNumber() }
        binding.fragmentInsertVehicleTextInputLayoutVehicleLicensePlateNumber.editText?.addTextChangedListener { validateLicensePlateNumber() }
        binding.fragmentInsertVehicleDropdownVehicleType.addTextChangedListener { validateVehicleType() }
        binding.fragmentInsertVehicleDropdownVehiclePowerSource.addTextChangedListener { validateVehiclePowerSource() }
        binding.fragmentInsertVehicleDropdownVehicleTransmission.addTextChangedListener { validateVehicleTransmission() }
        binding.fragmentInsertVehicleDropdownVehicleWheelDrive.addTextChangedListener { validateVehicleWheelDrive() }
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
            binding.fragmentInsertVehicleTextInputLayoutVehicleName.error = getString(R.string.x_is_required, getString(R.string.name))
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
                getString(R.string.x_is_required,getString(R.string.registration_number))
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
            binding.fragmentInsertVehicleTextInputLayoutVehicleType.error = getString(R.string.x_is_required, getString(R.string.type))
            false
        } else if (!vehicleTypes.any {
                it.name.equals(binding.fragmentInsertVehicleDropdownVehicleType.text.toString(), true)
            }) {
            binding.fragmentInsertVehicleTextInputLayoutVehicleType.error = getString(R.string.x_is_invalid, getString(R.string.type))
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
        } else if (!VehiclePowerSource.toListString().any {
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
        } else if (!VehicleTransmission.toListString().any {
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
}