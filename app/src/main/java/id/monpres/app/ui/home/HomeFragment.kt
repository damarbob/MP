package id.monpres.app.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.viewModels
import androidx.hilt.navigation.fragment.hiltNavGraphViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.carousel.CarouselLayoutManager
import com.google.android.material.carousel.HeroCarouselStrategy
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.transition.MaterialSharedAxis
import dagger.hilt.android.AndroidEntryPoint
import id.monpres.app.MainActivity
import id.monpres.app.MainApplication
import id.monpres.app.MainGraphViewModel
import id.monpres.app.R
import id.monpres.app.databinding.FragmentHomeBinding
import id.monpres.app.enums.OrderStatus
import id.monpres.app.enums.OrderStatusType
import id.monpres.app.model.Banner
import id.monpres.app.model.OrderService
import id.monpres.app.ui.BaseFragment
import id.monpres.app.ui.adapter.BannerAdapter
import id.monpres.app.ui.adapter.OrderServiceAdapter
import id.monpres.app.ui.adapter.ServiceAdapter
import id.monpres.app.ui.adapter.VehicleAdapter
import id.monpres.app.ui.itemdecoration.SpacingItemDecoration
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HomeFragment : BaseFragment() {

    companion object {
        fun newInstance() = HomeFragment()
    }

    /* View models */
    private val viewModel: HomeViewModel by viewModels()
    private val mainGraphViewModel: MainGraphViewModel by hiltNavGraphViewModels(R.id.nav_main)

    /* Bindings */
    private lateinit var binding: FragmentHomeBinding

    /* UI */
    private lateinit var serviceAdapter: ServiceAdapter
    private lateinit var vehicleAdapter: VehicleAdapter
    private lateinit var orderServiceAdapter: OrderServiceAdapter

    private var orderServices: List<OrderService> = emptyList()
    private var ongoingOrders: List<OrderService> = emptyList()
    private var completedOrders: List<OrderService> = emptyList()

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

        ViewCompat.setOnApplyWindowInsetsListener(binding.fragmentHomeNestedScrollView) { v, windowInsets ->
            val insets =
                windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(insets.left, 0, insets.right, insets.bottom)
            windowInsets
        }

        setupServiceRecyclerView()
        setupVehicleRecyclerView()
        setupOrderServiceRecyclerView()
        setupOrderServiceObservers()
        vehiclesObservers()
        setupListeners()

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

            fragmentHomeButtonSeeAllHistory.setOnClickListener {
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
                    "1" ->
                        findNavController().navigate(HomeFragmentDirections.actionHomeFragmentToQuickServiceFragment(serviceId))
                    // Scheduled Service (temp id) TODO: Finalize ID
                    "2" ->
                        findNavController().navigate(R.id.action_homeFragment_to_scheduledServiceFragment,
                            bundleOf(Pair("serviceId", serviceId))
                        )
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
        observeUiState(mainGraphViewModel.userVehiclesState) { vehicles ->
            vehicleAdapter.submitList(vehicles.take(5))

            binding.fragmentHomeButtonSeeAllVehicle.visibility =
                if (vehicles.isNotEmpty()) View.VISIBLE else View.GONE

        }
    }

    fun setupOrderServiceRecyclerView() {
        orderServiceAdapter = OrderServiceAdapter(requireContext()) { orderService ->
            when (orderService.status) {
                in OrderStatus.entries.filter { it.type == OrderStatusType.CLOSED } -> {
                    // The status is closed (completed, cancelled, returned, failed)
                    findNavController().navigate(
                        HomeFragmentDirections.actionHomeFragmentToOrderServiceDetailFragment(
                            orderService
                        )
                    )
                }

                else -> findNavController().navigate(
                    HomeFragmentDirections.actionHomeFragmentToServiceProcessFragment(
                        orderService.id!!
                    )
                )
            }
        }

        binding.fragmentHomeRecyclerViewHistory.apply {
            addItemDecoration(SpacingItemDecoration(2))
            adapter = orderServiceAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    fun setupOrderServiceObservers() {
        //1. Pass the master list of orders to the ViewModel whenever it changes.
        observeUiState(mainGraphViewModel.userOrderServicesState) { serviceOrders ->
            viewModel.setAllOrderServices(serviceOrders)
            binding.fragmentHomeButtonSeeAllHistory.visibility =
                if (serviceOrders.isEmpty()) View.GONE else View.VISIBLE
        }

        // 2. Observe the final, filtered list and submit it to the adapter.
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.filteredOrderServices.collect { filteredList ->
                    orderServiceAdapter.submitList(filteredList)
                    toggleEmptyState(filteredList.isEmpty())
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

    private fun toggleEmptyState(isEmpty: Boolean) {
        if (isEmpty) {
            binding.apply {
                fragmentHomeLinearLayoutEmptyState.visibility = View.VISIBLE
                fragmentHomeRecyclerViewHistory.visibility = View.GONE
            }
        } else {
            binding.apply {
                fragmentHomeLinearLayoutEmptyState.visibility = View.GONE
                fragmentHomeRecyclerViewHistory.visibility = View.VISIBLE
            }
        }
    }

    override val progressIndicator: LinearProgressIndicator
        get() = binding.fragmentHomeProgressIndicator
}