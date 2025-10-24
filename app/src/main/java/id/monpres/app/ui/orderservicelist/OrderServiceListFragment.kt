package id.monpres.app.ui.orderservicelist

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.transition.MaterialSharedAxis
import dagger.hilt.android.AndroidEntryPoint
import id.monpres.app.MainActivity
import id.monpres.app.MainViewModel
import id.monpres.app.R
import id.monpres.app.databinding.FragmentOrderServiceListBinding
import id.monpres.app.enums.OrderStatus
import id.monpres.app.enums.OrderStatusType
import id.monpres.app.enums.UserRole
import id.monpres.app.model.OrderService
import id.monpres.app.ui.BaseFragment
import id.monpres.app.ui.adapter.OrderServiceAdapter
import id.monpres.app.ui.itemdecoration.SpacingItemDecoration

@AndroidEntryPoint
class OrderServiceListFragment : BaseFragment() {

    companion object {
        fun newInstance() = OrderServiceListFragment()
    }

    private val viewModel: OrderServiceListViewModel by viewModels()
    private val mainViewModel: MainViewModel by activityViewModels()

    private lateinit var binding: FragmentOrderServiceListBinding

    private lateinit var orderServiceAdapter: OrderServiceAdapter

    private var orderServices: List<OrderService> = emptyList()
    private var ongoingOrders: List<OrderService> = emptyList()
    private var completedOrders: List<OrderService> = emptyList()
    private var cancelledOrders: List<OrderService> = emptyList()

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
        binding = FragmentOrderServiceListBinding.inflate(inflater, container, false)

        setupOrderServiceListRecyclerView()
        setupOrderServiceListObservers()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as MainActivity).binding.activityMainAppBarLayout.background =
            binding.root.background
        // Set insets
        ViewCompat.setOnApplyWindowInsetsListener(binding.fragmentOrderServiceListNestedScrollView) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                insets.left,
                0,
                insets.right,
                insets.bottom
            )
            windowInsets
        }

        binding.fragmentOrderServiceListChipGroupOrderStatus.setOnCheckedStateChangeListener { group, checkedIds ->
            viewModel.setSelectedChipId(group.checkedChipId)
        }
    }

    private fun setupOrderServiceListRecyclerView() {
        orderServiceAdapter = OrderServiceAdapter(requireContext()) { orderService ->
            when (orderService.status) {
                in OrderStatus.entries.filter { it.type == OrderStatusType.CLOSED } -> {
                    // The status is closed (completed, cancelled, returned, failed)
                    findNavController().navigate(
                        OrderServiceListFragmentDirections.actionOrderServiceListFragmentToOrderServiceDetailFragment(
                            orderService
                        )
                    )
                }

                else -> findNavController().navigate(
                    OrderServiceListFragmentDirections.actionOrderServiceListFragmentToServiceProcessFragment(
                        orderService.id!!
                    )
                )
            }
        }

        binding.fragmentOrderServiceListRecyclerViewOrderServiceList.apply {
            addItemDecoration(SpacingItemDecoration(2))
            adapter = orderServiceAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun setupOrderServiceListObservers() {
        if (mainViewModel.getCurrentUser()?.role == UserRole.CUSTOMER) {
            observeUiState(mainViewModel.userOrderServicesState) {
                setupList(it)
            }
        } else if (mainViewModel.getCurrentUser()?.role == UserRole.PARTNER) {
            observeUiState(mainViewModel.partnerOrderServicesState) {
                setupList(it)
            }
        }

        // This observer reacts to the selected chip changing and updates the UI accordingly.
        viewModel.selectedChipId.observe(viewLifecycleOwner) { chipId ->
            // Programmatically check the chip. This won't re-trigger the listener.
            binding.fragmentOrderServiceListChipGroupOrderStatus.check(chipId ?: View.NO_ID)

            // Submit the correct list to the adapter based on the selected chip
            val listToSubmit = when (chipId) {
                R.id.fragmentOrderServiceListChipOrderStatusOngoing ->
                    ongoingOrders.sortedByDescending { it.createdAt }

                R.id.fragmentOrderServiceListChipOrderStatusCompleted ->
                    completedOrders.sortedByDescending { it.updatedAt }

                R.id.fragmentOrderServiceListChipOrderStatusCancelled ->
                    cancelledOrders.sortedByDescending { it.updatedAt }

                else ->
                    orderServices.sortedByDescending { it.updatedAt }
            }
            orderServiceAdapter.submitList(listToSubmit)
            toggleEmptyState(listToSubmit.isEmpty())
        }

    }

    private fun setupList(serviceOrders: List<OrderService>) {
        this.orderServices = serviceOrders
        // Re-group the orders whenever the main list changes
        ongoingOrders =
            serviceOrders.filter { it.status?.type == OrderStatusType.OPEN || it.status?.type == OrderStatusType.IN_PROGRESS }
        completedOrders = serviceOrders.filter { it.status == OrderStatus.COMPLETED }
        cancelledOrders = serviceOrders.filter { it.status == OrderStatus.CANCELLED || it.status == OrderStatus.FAILED || it.status == OrderStatus.RETURNED }


        // Set the default filter, but only if one isn't already set (e.g. from screen rotation)
        if (viewModel.selectedChipId.value == null) {
            val defaultChipId = if (ongoingOrders.isNotEmpty()) {
                R.id.fragmentOrderServiceListChipOrderStatusOngoing
            } else {
                View.NO_ID // Special value for no chip checked
            }
            viewModel.setSelectedChipId(defaultChipId)
        }
    }

    private fun toggleEmptyState(isEmpty: Boolean) {
        if (isEmpty) {
            binding.apply {
                fragmentOrderServiceListLinearLayoutEmptyState.visibility = View.VISIBLE
                fragmentOrderServiceListRecyclerViewOrderServiceList.visibility = View.GONE
            }
        } else {
            binding.apply {
                fragmentOrderServiceListLinearLayoutEmptyState.visibility = View.GONE
                fragmentOrderServiceListRecyclerViewOrderServiceList.visibility = View.VISIBLE
            }
        }
    }

    override val progressIndicator: LinearProgressIndicator
        get() = binding.fragmentOrderServiceListProgressIndicator
}