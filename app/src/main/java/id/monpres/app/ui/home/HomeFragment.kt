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
import com.google.android.material.carousel.CarouselLayoutManager
import com.google.android.material.carousel.HeroCarouselStrategy
import com.google.android.material.transition.MaterialSharedAxis
import dagger.hilt.android.AndroidEntryPoint
import id.monpres.app.MainActivity
import id.monpres.app.MainApplication
import id.monpres.app.R
import id.monpres.app.databinding.FragmentHomeBinding
import id.monpres.app.model.Banner
import id.monpres.app.ui.BaseFragment
import id.monpres.app.ui.adapter.BannerAdapter
import id.monpres.app.ui.adapter.ServiceAdapter
import id.monpres.app.ui.adapter.VehicleAdapter
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

    /* UI */
    private lateinit var serviceAdapter: ServiceAdapter
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

        setupServiceRecyclerView()
        setupVehicleRecyclerView()
        vehiclesObservers()
        setupListeners()

        val carousel = binding.fragmentHomeRecyclerViewCarousel
        carousel.adapter = BannerAdapter(listOf(
            Banner("https://monpres.id/wp-content/uploads/2023/09/WhatsApp-Image-2023-09-27-at-14.10.13.jpeg", 0),
            Banner("https://monpres.id/wp-content/uploads/2023/09/WhatsApp-Image-2023-09-27-at-14.10.11-1-1536x1025.jpeg", 1),
            Banner("https://monpres.id/wp-content/uploads/2023/09/WhatsApp-Image-2023-09-27-at-14.10.12.jpeg", 2),
        ))
        carousel.layoutManager = CarouselLayoutManager(HeroCarouselStrategy())

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as MainActivity).binding.activityMainAppBarLayout.background =
            binding.root.background
    }

    /**
     * Sets up the click listeners for the various UI elements in the fragment.
     * This includes navigation to other fragments etc.
     */
    private fun setupListeners() {
        with(binding) {
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

    private fun setupServiceRecyclerView() {
        // Create adapter with current state
        serviceAdapter = ServiceAdapter(MainApplication.services)

        binding.fragmentHomeRecyclerViewService.apply {
            addItemDecoration(SpacingItemDecoration(2))
            adapter = serviceAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }

        serviceAdapter.setOnServiceClickListener(object : ServiceAdapter.OnServiceClickListener {
            override fun onServiceClicked(serviceId: String?) {
                when (serviceId) {
                    // Quick Service (temp id) TODO: Finalize ID
                    "1" ->
                        findNavController().navigate(R.id.action_homeFragment_to_quickServiceFragment)
                    // Scheduled Service (temp id) TODO: Finalize ID
                    "2" ->
                        findNavController().navigate(R.id.action_homeFragment_to_scheduledServiceFragment)
                    // Component Replacement (temp id) TODO: Finalize ID
                    "3" ->
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.coming_soon),
                            Toast.LENGTH_SHORT
                        ).show()
                }
            }
        })
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