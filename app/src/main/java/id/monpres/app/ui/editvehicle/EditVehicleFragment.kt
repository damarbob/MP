package id.monpres.app.ui.editvehicle

import android.os.Build
import androidx.fragment.app.viewModels
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.transition.MaterialSharedAxis
import id.monpres.app.MainActivity
import id.monpres.app.R
import id.monpres.app.databinding.FragmentEditVehicleBinding
import id.monpres.app.model.Vehicle

class EditVehicleFragment : Fragment() {

    companion object {
        fun newInstance() = EditVehicleFragment()
    }

    private val viewModel: EditVehicleViewModel by viewModels()

    private lateinit var binding: FragmentEditVehicleBinding

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

        val vehicle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arguments?.getParcelable("vehicle", Vehicle::class.java)
        } else {
            arguments?.getParcelable("vehicle")
        }
        binding.fragmentEditVehicleTextInputLayoutVehicleName.editText?.setText(vehicle?.name)
        binding.fragmentEditVehicleTextInputLayoutVehicleRegistrationNumber.editText?.setText(vehicle?.registrationNumber)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val navController = findNavController()
        val drawerLayout = (requireActivity() as MainActivity).drawerLayout
        val appBarConfiguration =
            AppBarConfiguration(navController.graph, drawerLayout = drawerLayout)

        binding.fragmentEditVehicleToolbar.setupWithNavController(navController, appBarConfiguration)
    }
}