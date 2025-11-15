package id.monpres.app.ui.partnerhome

import android.os.Bundle
import android.view.View
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
import dev.androidbroadcast.vbpd.viewBinding
import id.monpres.app.MainGraphViewModel
import id.monpres.app.R
import id.monpres.app.databinding.FragmentPartnerHomeBinding
import id.monpres.app.enums.OrderStatus
import id.monpres.app.enums.OrderStatusType
import id.monpres.app.model.Banner
import id.monpres.app.repository.UserRepository
import id.monpres.app.ui.BaseFragment
import id.monpres.app.ui.adapter.BannerAdapter
import id.monpres.app.ui.adapter.OrderServiceAdapter
import id.monpres.app.ui.itemdecoration.SpacingItemDecoration
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PartnerHomeFragment : BaseFragment(R.layout.fragment_partner_home) {

    companion object {
        fun newInstance() = PartnerHomeFragment()
    }

    /* View models */
    private val viewModel: PartnerHomeViewModel by viewModels()
    private val mainGraphViewModel: MainGraphViewModel by activityViewModels()

    /* UI */
    private val binding by viewBinding(FragmentPartnerHomeBinding::bind)

    /* Repositories */
    @Inject
    lateinit var userRepository: UserRepository

    private lateinit var orderServiceAdapter: OrderServiceAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
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
        orderServiceAdapter = OrderServiceAdapter(requireContext()) { orderService, root ->
            when (orderService.status) {
                in OrderStatus.entries.filter { it.type == OrderStatusType.CLOSED } -> {
                    // The status is closed (completed, cancelled, returned, failed)
                    findNavController().navigate(
                        PartnerHomeFragmentDirections.actionPartnerHomeFragmentToOrderServiceDetailFragment(
                            orderService, mainGraphViewModel.getCurrentUser()
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
        //1. Pass the master list of orders to the ViewModel whenever it changes.
        observeUiState(mainGraphViewModel.partnerOrderServicesState) { serviceOrders ->
            viewModel.setAllOrderServices(serviceOrders)
            binding.fragmentPartnerHomeButtonSeeAllOrderService.visibility =
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
                    if (binding.fragmentPartnerHomeChipGroupOrderStatus.checkedChipId != chipId) {
                        binding.fragmentPartnerHomeChipGroupOrderStatus.check(chipId ?: View.NO_ID)
                    }
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
