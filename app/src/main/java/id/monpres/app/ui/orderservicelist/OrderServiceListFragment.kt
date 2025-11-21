package id.monpres.app.ui.orderservicelist

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
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
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.transition.MaterialElevationScale
import com.google.android.material.transition.MaterialSharedAxis
import dagger.hilt.android.AndroidEntryPoint
import dev.androidbroadcast.vbpd.viewBinding
import id.monpres.app.MainGraphViewModel
import id.monpres.app.R
import id.monpres.app.databinding.FragmentOrderServiceListBinding
import id.monpres.app.enums.OrderStatus
import id.monpres.app.enums.OrderStatusType
import id.monpres.app.enums.UserRole
import id.monpres.app.ui.BaseFragment
import id.monpres.app.ui.adapter.OrderServiceAdapter
import id.monpres.app.ui.itemdecoration.SpacingItemDecoration
import kotlinx.coroutines.launch

@AndroidEntryPoint
class OrderServiceListFragment : BaseFragment(R.layout.fragment_order_service_list) {

    companion object {
        fun newInstance() = OrderServiceListFragment()
    }

    private val viewModel: OrderServiceListViewModel by viewModels()
    private val mainGraphViewModel: MainGraphViewModel by activityViewModels()

    private val binding by viewBinding(FragmentOrderServiceListBinding::bind)

    private lateinit var orderServiceAdapter: OrderServiceAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        postponeEnterTransition()
        view.doOnPreDraw { startPostponedEnterTransition() }

        enterTransition = MaterialSharedAxis(MaterialSharedAxis.Y, /* forward= */ true)
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.Y, /* forward= */ false)

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

        setupOrderServiceListRecyclerView()
        setupSearchListener() // Added Search Listener setup
        setupOrderServiceListObservers()

        binding.fragmentOrderServiceListChipGroupOrderStatus.setOnCheckedStateChangeListener { group, checkedIds ->
            viewModel.setSelectedChipId(group.checkedChipId)
        }
    }

    private fun setupSearchListener() {
        // Handle Enter / Submit button on keyboard
        binding.fragmentOrderServiceListEditTextSearch.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                val query = v.text.toString().trim()
                viewModel.setSearchQuery(query)
                hideKeyboard()
                return@setOnEditorActionListener true
            }
            false
        }

        // Handle the Clear Text icon click to reset search
        binding.fragmentOrderServiceListTextInputLayoutSearch.setEndIconOnClickListener {
            binding.fragmentOrderServiceListEditTextSearch.text?.clear()
            viewModel.setSearchQuery("")
            hideKeyboard()
        }
    }

    private fun hideKeyboard() {
        val imm =
            requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
        binding.fragmentOrderServiceListEditTextSearch.clearFocus()
    }

    private fun setupOrderServiceListRecyclerView() {
        orderServiceAdapter = OrderServiceAdapter(requireContext()) { orderService, root ->
            when (orderService.status) {
                in OrderStatus.entries.filter { it.type == OrderStatusType.CLOSED } -> {
                    exitTransition = MaterialElevationScale(false)
                    reenterTransition = MaterialElevationScale(true)
                    val orderDetailTransitionName =
                        getString(R.string.order_detail_transition_name)
                    val extras = FragmentNavigatorExtras(root to orderDetailTransitionName)
                    val directions =
                        OrderServiceListFragmentDirections.actionOrderServiceListFragmentToOrderServiceDetailFragment(
                            orderService, mainGraphViewModel.getCurrentUser()
                        )
                    findNavController().navigate(
                        directions, extras
                    )
                }

                else -> {
                    exitTransition = MaterialElevationScale(false)
                    reenterTransition = MaterialElevationScale(true)
                    val serviceProcessTransitionName =
                        getString(R.string.service_process_transition_name)
                    val extras = FragmentNavigatorExtras(root to serviceProcessTransitionName)
                    val directions =
                        OrderServiceListFragmentDirections.actionOrderServiceListFragmentToServiceProcessFragment(
                            orderService.id!!
                        )
                    findNavController().navigate(
                        directions, extras
                    )
                }
            }
        }

        binding.fragmentOrderServiceListRecyclerViewOrderServiceList.apply {
            addItemDecoration(SpacingItemDecoration(2))
            adapter = orderServiceAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun setupOrderServiceListObservers() {
        val currentUser = mainGraphViewModel.getCurrentUser()

        when (currentUser?.role) {
            UserRole.CUSTOMER -> {
                observeUiState(mainGraphViewModel.userOrderServicesState) {
                    viewModel.setAllOrderServices(it)
                }
            }

            UserRole.PARTNER -> {
                observeUiState(mainGraphViewModel.partnerOrderServicesState) {
                    viewModel.setAllOrderServices(it)
                }
            }

            UserRole.ADMIN -> {
                mainGraphViewModel.observeAllOrderServices()
                observeUiState(mainGraphViewModel.allOrderServicesState) {
                    viewModel.setAllOrderServices(it)
                }
            }

            else -> {}
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.filteredOrderServices.collect { filteredList ->
                    orderServiceAdapter.submitList(filteredList)
                    toggleEmptyState(filteredList.isEmpty())
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.selectedChipId.collect { chipId ->
                    if (binding.fragmentOrderServiceListChipGroupOrderStatus.checkedChipId != chipId) {
                        binding.fragmentOrderServiceListChipGroupOrderStatus.check(
                            chipId ?: View.NO_ID
                        )
                    }
                }
            }
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