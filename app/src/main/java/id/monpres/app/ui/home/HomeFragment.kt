package id.monpres.app.ui.home

import android.os.Bundle
import android.transition.TransitionManager
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnPreDraw
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.carousel.CarouselLayoutManager
import com.google.android.material.carousel.HeroCarouselStrategy
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.transition.MaterialElevationScale
import com.google.android.material.transition.MaterialSharedAxis
import dagger.hilt.android.AndroidEntryPoint
import dev.androidbroadcast.vbpd.viewBinding
import id.monpres.app.MainApplication
import id.monpres.app.MainGraphViewModel
import id.monpres.app.R
import id.monpres.app.databinding.FragmentHomeBinding
import id.monpres.app.enums.OrderStatus
import id.monpres.app.enums.OrderStatusType
import id.monpres.app.model.Banner
import id.monpres.app.model.MontirPresisiUser
import id.monpres.app.model.OrderService
import id.monpres.app.model.Vehicle
import id.monpres.app.ui.BaseFragment
import id.monpres.app.ui.adapter.BannerAdapter
import id.monpres.app.ui.adapter.OrderServiceAdapter
import id.monpres.app.ui.adapter.ServiceAdapter
import id.monpres.app.ui.adapter.VehicleAdapter
import id.monpres.app.ui.itemdecoration.SpacingItemDecoration
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HomeFragment : BaseFragment(R.layout.fragment_home) {

    companion object {
        fun newInstance() = HomeFragment()
    }

    /* View models */
    private val viewModel: HomeViewModel by viewModels()
    private val mainGraphViewModel: MainGraphViewModel by activityViewModels()

    /* Bindings */
    private val binding by viewBinding(FragmentHomeBinding::bind)

    /* UI */
    private lateinit var serviceAdapter: ServiceAdapter
    private lateinit var vehicleAdapter: VehicleAdapter
    private lateinit var orderServiceAdapter: OrderServiceAdapter

    private var vehicles: List<Vehicle> = emptyList()
    private var currentUser: MontirPresisiUser? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        postponeEnterTransition()
        view.doOnPreDraw { startPostponedEnterTransition() }

        ViewCompat.setOnApplyWindowInsetsListener(binding.fragmentHomeNestedScrollView) { v, windowInsets ->
            val insets =
                windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(insets.left, 0, insets.right, insets.bottom)
            windowInsets
        }

        val carousel = binding.fragmentHomeRecyclerViewCarousel
        carousel.adapter = BannerAdapter(
            listOf(
                Banner(
                    "https://simsinfotekno.com/MontirPresisi/mp-1.jpg",
                    0
                ),
                Banner(
                    "https://simsinfotekno.com/MontirPresisi/mp-2.jpg",
                    1
                ),
                Banner(
                    "https://simsinfotekno.com/MontirPresisi/mp-3.jpg",
                    2
                ),
            )
        )
        carousel.layoutManager = CarouselLayoutManager(HeroCarouselStrategy())

        setupServiceRecyclerView()
        setupVehicleRecyclerView()
        setupOrderServiceRecyclerView()
        setupOrderServiceObservers()
        vehiclesObservers()
        setupListeners()
    }

    /**
     * Sets up the click listeners for the various UI elements in the fragment.
     * This includes navigation to other fragments etc.
     */
    private fun setupListeners() {
        with(binding) {
            fragmentHomeButtonSeeAllVehicle.setOnClickListener {
                exitTransition = MaterialSharedAxis(MaterialSharedAxis.X, /* forward= */ true)
                reenterTransition = MaterialSharedAxis(MaterialSharedAxis.X, /* forward= */ false)
                findNavController().navigate(R.id.action_homeFragment_to_vehicleListFragment)
            }

            fragmentHomeButtonAddVehicle.setOnClickListener {
                if (currentUser != null) {
                    exitTransition = MaterialSharedAxis(MaterialSharedAxis.X, /* forward= */ true)
                    reenterTransition =
                        MaterialSharedAxis(MaterialSharedAxis.X, /* forward= */ false)
                    findNavController().navigate(R.id.action_homeFragment_to_insertVehicleFragment)
                } else {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.error_network),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            fragmentHomeButtonSeeAllHistory.setOnClickListener {
                exitTransition = MaterialSharedAxis(MaterialSharedAxis.Y, /* forward= */ true)
                reenterTransition = MaterialSharedAxis(MaterialSharedAxis.Y, /* forward= */ false)
                findNavController().navigate(HomeFragmentDirections.actionHomeFragmentToOrderServiceListFragment())
            }

            fragmentHomeChipGroupOrderStatus.setOnCheckedStateChangeListener { group, checkedIds ->
                viewModel.setSelectedChipId(group.checkedChipId)
            }
        }
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
                    "1" -> {
                        handleServiceClicked {
                            exitTransition =
                                MaterialSharedAxis(MaterialSharedAxis.X, /* forward= */ true)
                            reenterTransition =
                                MaterialSharedAxis(MaterialSharedAxis.X, /* forward= */ false)
                            findNavController().navigate(
                                HomeFragmentDirections.actionHomeFragmentToQuickServiceFragment(
                                    serviceId
                                )
                            )
                        }
                    }
                    // Scheduled Service (temp id) TODO: Finalize ID
                    "2" -> {
                        handleServiceClicked {
                            exitTransition =
                                MaterialSharedAxis(MaterialSharedAxis.X, /* forward= */ true)
                            reenterTransition =
                                MaterialSharedAxis(MaterialSharedAxis.X, /* forward= */ false)
                            findNavController().navigate(
                                R.id.action_homeFragment_to_scheduledServiceFragment,
                                bundleOf(Pair("serviceId", serviceId))
                            )
                        }
                    }
                    // Component Replacement (temp id) TODO: Finalize ID
                    "3" ->
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.coming_soon),
                            Toast.LENGTH_SHORT
                        ).show()
                }
            }

            private fun handleServiceClicked(action: () -> Unit) {
                when {
                    currentUser == null -> Toast.makeText(
                        requireContext(),
                        getString(R.string.error_network),
                        Toast.LENGTH_SHORT
                    ).show()

                    vehicles.isEmpty() -> handleNoVehicle()

                    else -> action()
                }
            }
        })
    }

    private fun handleNoVehicle() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.warning))
            .setMessage(getString(R.string.no_vehicle_has_been_added_yet))
            .setPositiveButton(getString(R.string.okay)) { _, _ ->
                exitTransition = MaterialSharedAxis(MaterialSharedAxis.X, /* forward= */ true)
                reenterTransition = MaterialSharedAxis(MaterialSharedAxis.X, /* forward= */ false)
                findNavController().navigate(HomeFragmentDirections.actionHomeFragmentToInsertVehicleFragment())
            }
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
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
        vehicleAdapter = VehicleAdapter { vehicle, root ->
            exitTransition = MaterialElevationScale(false)
            reenterTransition = MaterialElevationScale(true)
            val editVehicleTransitionName = getString(R.string.edit_vehicle_transition_name)
            val extras = FragmentNavigatorExtras(root to editVehicleTransitionName)
            val action = HomeFragmentDirections.actionHomeFragmentToEditVehicleFragment(vehicle)
            findNavController().navigate(
                action, extras
            )
        }

        binding.fragmentHomeRecyclerViewVehicle.apply {
            addItemDecoration(SpacingItemDecoration(2))
            adapter = vehicleAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    /**
     * Observes the vehicles data from the [MainGraphViewModel].
     * Updates the [VehicleAdapter] with the list of vehicles, limiting it to the first 5 vehicles.
     */
    private fun vehiclesObservers() {
        observeUiState(mainGraphViewModel.userVehiclesState, onEmpty = {
            binding.fragmentHomeButtonSeeAllVehicle.visibility = View.GONE
            binding.fragmentHomeLinearLayoutVehicleEmptyState.visibility = View.VISIBLE
        }) { vehicles ->
            this.vehicles = vehicles
            vehicleAdapter.submitList(vehicles.take(5))

            binding.fragmentHomeButtonSeeAllVehicle.visibility =
                if (vehicles.isNotEmpty()) View.VISIBLE else View.GONE
            binding.fragmentHomeLinearLayoutVehicleEmptyState.visibility =
                if (vehicles.isNotEmpty()) View.GONE else View.VISIBLE
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainGraphViewModel.currentUser.collect { currentUser ->
                    this@HomeFragment.currentUser = currentUser
                }
            }
        }
    }

    fun setupOrderServiceRecyclerView() {
        orderServiceAdapter = OrderServiceAdapter { orderService, root ->
            navigateToDetail(orderService, root)
        }

        binding.fragmentHomeRecyclerViewHistory.apply {
            addItemDecoration(SpacingItemDecoration(2))
            adapter = orderServiceAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun navigateToDetail(orderService: OrderService, root: View) {
        val isClosed =
            orderService.status in OrderStatus.entries.filter { it.type == OrderStatusType.CLOSED }

        exitTransition = MaterialElevationScale(false)
        reenterTransition = MaterialElevationScale(true)

        val transitionName =
            if (isClosed) getString(R.string.order_detail_transition_name) else getString(R.string.service_process_transition_name)
        val extras = FragmentNavigatorExtras(root to transitionName)

        val directions = if (isClosed) {
            HomeFragmentDirections.actionHomeFragmentToOrderServiceDetailFragment(
                orderService, mainGraphViewModel.getCurrentUser()
            )
        } else {
            HomeFragmentDirections.actionHomeFragmentToServiceProcessFragment(
                orderService.id!!
            )
        }

        findNavController().navigate(directions, extras)
    }

    fun setupOrderServiceObservers() {
        //1. Pass the master list of orders to the ViewModel whenever it changes.
        observeUiState(mainGraphViewModel.userOrderServicesState, onEmpty = {
            binding.fragmentHomeButtonSeeAllHistory.visibility = View.GONE
            toggleOrderServiceEmptyState(true)
        }) { serviceOrders ->
            viewModel.setAllOrderServices(serviceOrders)
            binding.fragmentHomeButtonSeeAllHistory.visibility =
                if (serviceOrders.isEmpty()) View.GONE else View.VISIBLE
        }

        // 2. Observe the final, filtered list and submit it to the adapter.
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.filteredOrderServices.collect { filteredList ->
                    orderServiceAdapter.submitList(filteredList)
                    toggleOrderServiceEmptyState(filteredList.isEmpty())
                }
            }
        }

        // 3. Observe the selected chip ID just to update the UI (the ChipGroup).
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.selectedChipId.collect { chipId ->
                    // Ensure the correct chip is visually checked without triggering the listener again.
                    if (binding.fragmentHomeChipGroupOrderStatus.checkedChipId != chipId) {
                        binding.fragmentHomeChipGroupOrderStatus.check(chipId ?: View.NO_ID)
                    }
                }
            }
        }
    }

    private fun toggleOrderServiceEmptyState(isEmpty: Boolean) {
        if (isEmpty) {
            binding.apply {
                fragmentHomeLinearLayoutOrderServiceEmptyState.visibility = View.VISIBLE
                fragmentHomeRecyclerViewHistory.visibility = View.GONE
            }
        } else {
            binding.apply {
                fragmentHomeLinearLayoutOrderServiceEmptyState.visibility = View.GONE
                fragmentHomeRecyclerViewHistory.visibility = View.VISIBLE
            }
        }
    }

    override fun onDestroyView() {
        // Crucial: Force the Transition Manager to stop tracking the root layout
        // This removes the reference from the ThreadLocal map causing the leak.
        (view as? ViewGroup)?.let { rootView ->
            androidx.transition.TransitionManager.endTransitions(rootView)
            TransitionManager.endTransitions(rootView)
        }

        // Clean up the RecyclerView specifically
        // This prevents the Adapter from holding onto ViewHolders that might still
        // have transition tags on them.
        binding.fragmentHomeRecyclerViewHistory.adapter = null
        binding.fragmentHomeRecyclerViewVehicle.adapter = null
        super.onDestroyView()
    }

    override val progressIndicator: LinearProgressIndicator
        get() = binding.fragmentHomeProgressIndicator
}
