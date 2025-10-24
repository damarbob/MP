package id.monpres.app.ui.partnerhome

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.carousel.CarouselLayoutManager
import com.google.android.material.carousel.HeroCarouselStrategy
import com.google.android.material.progressindicator.LinearProgressIndicator
import dagger.hilt.android.AndroidEntryPoint
import id.monpres.app.MainViewModel
import id.monpres.app.R
import id.monpres.app.databinding.FragmentPartnerHomeBinding
import id.monpres.app.enums.OrderStatus
import id.monpres.app.enums.OrderStatusType
import id.monpres.app.model.Banner
import id.monpres.app.model.OrderService
import id.monpres.app.repository.UserRepository
import id.monpres.app.ui.BaseFragment
import id.monpres.app.ui.adapter.BannerAdapter
import id.monpres.app.ui.adapter.OrderServiceAdapter
import id.monpres.app.ui.itemdecoration.SpacingItemDecoration
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PartnerHomeFragment : BaseFragment() {

    companion object {
        fun newInstance() = PartnerHomeFragment()
    }

    /* View models */
    private val viewModel: PartnerHomeViewModel by viewModels()
    private val mainViewModel: MainViewModel by activityViewModels()

    /* UI */
    private lateinit var binding: FragmentPartnerHomeBinding

    /* Repositories */
    @Inject
    lateinit var userRepository: UserRepository

    private lateinit var orderServiceAdapter: OrderServiceAdapter

    private var orderServices: List<OrderService> = emptyList()
    private var ongoingOrders: List<OrderService> = emptyList()
    private var completedOrders: List<OrderService> = emptyList()


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentPartnerHomeBinding.inflate(inflater, container, false)

        ViewCompat.setOnApplyWindowInsetsListener(binding.fragmentPartnerHomeNestedScrollView) { v, windowInsets ->
            val insets =
                windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(insets.left, 0, insets.right, insets.bottom)
            windowInsets
        }

        /* Setup UI */
        setupUI()
        setupHeroCarousel()
        setupOrderServiceRecyclerView()

        /* Observers */
        setupObservers()

        return binding.root
    }

    private fun setupUI() {
        /* Initialize UI */
        binding.fragmentPartnerHomeProgressIndicator.visibility = View.GONE

        binding.fragmentPartnerHomeChipGroupOrderStatus.isSingleSelection = true

        // This listener now only has one job: update the state in the ViewModel.
        binding.fragmentPartnerHomeChipGroupOrderStatus.setOnCheckedStateChangeListener { group, _ ->
            viewModel.setSelectedChipId(group.checkedChipId)
        }

        binding.fragmentPartnerHomeButtonSeeAllOrderService.setOnClickListener {
            findNavController().navigate(PartnerHomeFragmentDirections.actionPartnerHomeFragmentToOrderServiceListFragment())
        }
    }

    private fun setupHeroCarousel() {
        /* Setup carousel */
        val carousel = binding.fragmentPartnerHomeRecyclerViewCarousel
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
    }

    private fun setupOrderServiceRecyclerView() {
        orderServiceAdapter = OrderServiceAdapter(requireContext()) { orderService ->
            when (orderService.status) {
                in OrderStatus.entries.filter { it.type == OrderStatusType.CLOSED } -> {
                    // The status is closed (completed, cancelled, returned, failed)
                    findNavController().navigate(
                        PartnerHomeFragmentDirections.actionPartnerHomeFragmentToOrderServiceDetailFragment(
                            orderService
                        )
                    )
                }

                else -> findNavController().navigate(
                    PartnerHomeFragmentDirections.actionPartnerHomeFragmentToServiceProcessFragment(
                        orderService.id!!
                    )
                )
            }
        }
        binding.fragmentPartnerHomeRecyclerViewOrderService.apply {
            addItemDecoration(SpacingItemDecoration(2))
            adapter = orderServiceAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun setupObservers() {
        // This observer reacts to the list of orders changing
        observeUiState(mainViewModel.partnerOrderServicesState) { serviceOrders ->
            this.orderServices = serviceOrders
            // Re-group the orders whenever the main list changes
            ongoingOrders =
                serviceOrders.filter { it.status?.type == OrderStatusType.OPEN || it.status?.type == OrderStatusType.IN_PROGRESS }
            completedOrders = serviceOrders.filter { it.status == OrderStatus.COMPLETED }

            // Set the default filter, but only if one isn't already set (e.g. from screen rotation)
            if (viewModel.selectedChipId.value == null) {
                val defaultChipId = if (ongoingOrders.isNotEmpty()) {
                    R.id.fragmentPartnerHomeChipOrderStatusOngoing
                } else {
                    View.NO_ID // Special value for no chip checked
                }
                viewModel.setSelectedChipId(defaultChipId)
            }

            binding.fragmentPartnerHomeButtonSeeAllOrderService.visibility =
                if (serviceOrders.isEmpty()) View.GONE else View.VISIBLE
        }

        // This observer reacts to the selected chip changing and updates the UI accordingly.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.selectedChipId.collect { chipId ->
                    // Programmatically check the chip. This won't re-trigger the listener.
                    binding.fragmentPartnerHomeChipGroupOrderStatus.check(chipId ?: View.NO_ID)

                    // Submit the correct list to the adapter based on the selected chip
                    val listToSubmit = when (chipId) {
                        R.id.fragmentPartnerHomeChipOrderStatusOngoing ->
                            ongoingOrders.sortedByDescending { it.createdAt }
                        R.id.fragmentPartnerHomeChipOrderStatusCompleted ->
                            completedOrders.take(5).sortedByDescending { it.updatedAt }
                        else ->
                            orderServices.take(5).sortedByDescending { it.updatedAt }
                    }
                    orderServiceAdapter.submitList(listToSubmit)
                    toggleEmptyState(listToSubmit.isEmpty())
                }
            }
        }
    }

    private fun toggleEmptyState(isEmpty: Boolean) {
        if (isEmpty) {
            binding.apply {
                fragmentPartnerHomeLinearLayoutEmptyState.visibility = View.VISIBLE
                fragmentPartnerHomeRecyclerViewOrderService.visibility = View.GONE
            }
        } else {
            binding.apply {
                fragmentPartnerHomeLinearLayoutEmptyState.visibility = View.GONE
                fragmentPartnerHomeRecyclerViewOrderService.visibility = View.VISIBLE
            }
        }
    }

    override val progressIndicator: LinearProgressIndicator
        get() = binding.fragmentPartnerHomeProgressIndicator
}
