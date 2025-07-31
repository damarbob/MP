package id.monpres.app.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.transition.MaterialSharedAxis
import dagger.hilt.android.AndroidEntryPoint
import id.monpres.app.MainActivity
import id.monpres.app.R
import id.monpres.app.databinding.FragmentHomeBinding
import id.monpres.app.ui.BaseFragment
import id.monpres.app.ui.adapter.VehicleAdapter
import id.monpres.app.ui.insets.InsetsWithKeyboardCallback
import id.monpres.app.ui.itemdecoration.SpacingItemDecoration

@AndroidEntryPoint
class HomeFragment : BaseFragment() {

    companion object {
        fun newInstance() = HomeFragment()
    }

    /* View models */
    private val viewModel: HomeViewModel by viewModels()

    /* Bindings */
    private lateinit var binding: FragmentHomeBinding

    /* Variables */
    private lateinit var vehicleAdapter: VehicleAdapter

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

        binding = FragmentHomeBinding.inflate(inflater, container, false)

        ViewCompat.setOnApplyWindowInsetsListener(binding.fragmentHomeNestedScrollView) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
            WindowInsetsCompat.CONSUMED
        }

        setupVehicleRecyclerView()
        vehiclesObservers()
        setupListeners()

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as MainActivity).binding.activityMainAppBarLayout.background = binding.root.background
    }

    /**
     * Sets up the click listeners for the various UI elements in the fragment.
     * This includes navigation to other fragments etc.
     */
    private fun setupListeners() {
        with(binding) {
            fragmentHomeCardViewQuickService.setOnClickListener {
                findNavController().navigate(R.id.action_homeFragment_to_quickServiceFragment)
            }
            fragmentHomeCardViewScheduledService.setOnClickListener {
                findNavController().navigate(R.id.action_homeFragment_to_scheduledServiceFragment)
            }
            fragmentHomeCardViewComponentReplacement.setOnClickListener {
                Toast.makeText(
                    requireContext(),
                    "Coming Soon...",
                    Toast.LENGTH_SHORT
                )
                    .show()
            }
            fragmentHomeButtonSeeAllVehicle.setOnClickListener {
                findNavController().navigate(R.id.action_homeFragment_to_vehicleListFragment)
            }

            fragmentHomeButtonAddVehicle.setOnClickListener {
                findNavController().navigate(R.id.action_homeFragment_to_insertVehicleFragment)
            }
        }
    }

    override fun showLoading(isLoading: Boolean) {
        binding.fragmentHomeProgressIndicator.visibility =
            if (isLoading) View.VISIBLE else View.GONE
    }

    /**
     * Sets up the RecyclerView for displaying vehicles.
     * Initializes the [VehicleAdapter] and sets it to the RecyclerView.
     * Sets the LayoutManager for the RecyclerView.
     * Defines the click listener for each item in the RecyclerView, which navigates to the
     * EditVehicleFragment when an item is clicked.
     */
    private fun setupVehicleRecyclerView() {
        // Create adapter with current state
        vehicleAdapter = VehicleAdapter { vehicle ->
            val action = HomeFragmentDirections.actionHomeFragmentToEditVehicleFragment(vehicle)
            findNavController().navigate(
                action
            )
        }

        binding.fragmentHomeRecyclerViewVehicle.apply {
            addItemDecoration(SpacingItemDecoration(2))
            adapter = vehicleAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    /**
     * Observes the vehicles data from the [HomeViewModel].
     * Updates the [VehicleAdapter] with the list of vehicles, limiting it to the first 5 vehicles.
     */
    private fun vehiclesObservers() {
        observeUiState(viewModel.getVehiclesFlow()) { vehicles ->
            vehicleAdapter.submitList(vehicles.take(5))

            if (vehicles.size > 0) {
                binding.fragmentHomeButtonSeeAllVehicle.visibility = View.VISIBLE
            } else {
                binding.fragmentHomeButtonSeeAllVehicle.visibility = View.GONE
            }
        }
    }
}