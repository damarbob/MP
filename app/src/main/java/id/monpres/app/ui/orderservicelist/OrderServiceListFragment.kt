package id.monpres.app.ui.orderservicelist

import android.content.Context
import android.os.Bundle
import android.transition.TransitionManager
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
import id.monpres.app.model.OrderService
import id.monpres.app.ui.BaseFragment
import id.monpres.app.ui.adapter.OrderServiceAdapter
import id.monpres.app.ui.itemdecoration.SpacingItemDecoration
import kotlinx.coroutines.launch

@AndroidEntryPoint
class OrderServiceListFragment : BaseFragment(R.layout.fragment_order_service_list) {

    companion object {
        fun newInstance() = OrderServiceListFragment()
    }

    // We still keep MainGraphViewModel if you need it for shared data,
    // but the list logic is now moved to OrderServiceListViewModel
    private val mainGraphViewModel: MainGraphViewModel by activityViewModels()
    private val viewModel: OrderServiceListViewModel by viewModels()

    private val binding by viewBinding(FragmentOrderServiceListBinding::bind)
    private lateinit var orderServiceAdapter: OrderServiceAdapter
    private lateinit var layoutManager: LinearLayoutManager

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupTransitions()
        setupInsets()
        setupRecyclerView()
        setupSearchAndFilters()
        setupObservers()
    }

    private fun setupTransitions() {
        postponeEnterTransition()
        enterTransition = MaterialSharedAxis(MaterialSharedAxis.Y, true)
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.Y, false)
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.fragmentOrderServiceListRecyclerViewOrderServiceList) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Only apply bottom padding to RecyclerView so it clips correctly but content scrolls behind nav bar
            v.setPadding(0, 0, 0, insets.bottom + 150) // +150 for safety space
            windowInsets
        }
    }

    private fun setupRecyclerView() {
        orderServiceAdapter = OrderServiceAdapter { orderService, root ->
            navigateToDetail(orderService, root)
        }

        // We initialize this locally or as a class property, but we must cast it inside the listener
        val linearLayoutManager = LinearLayoutManager(requireContext())

        binding.fragmentOrderServiceListRecyclerViewOrderServiceList.apply {
            addItemDecoration(SpacingItemDecoration(2))
            adapter = orderServiceAdapter
            layoutManager = linearLayoutManager

            // Start postponed transition
            viewTreeObserver.addOnPreDrawListener {
                startPostponedEnterTransition()
                true
            }

            // Infinite Scroll Listener
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)

                    // FIX: Cast the layoutManager to LinearLayoutManager
                    val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return

                    val visibleItemCount = layoutManager.childCount
                    val totalItemCount = layoutManager.itemCount
                    val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                    // Trigger load when within 3 items of bottom
                    if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount - 3 && firstVisibleItemPosition >= 0) {
                        viewModel.onScrollBottomReached()
                    }
                }
            })
        }
    }

    private fun setupSearchAndFilters() {
        // Search Input
        binding.fragmentOrderServiceListEditTextSearch.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                viewModel.setSearchQuery(v.text.toString().trim())
                hideKeyboard()
                true
            } else false
        }

        // Text Watcher for Debounced Search (Optional, if you want search-as-you-type)
        binding.fragmentOrderServiceListEditTextSearch.addTextChangedListener(object :
            android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                viewModel.setSearchQuery(s.toString().trim())
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // Clear Search
        binding.fragmentOrderServiceListTextInputLayoutSearch.setEndIconOnClickListener {
            binding.fragmentOrderServiceListEditTextSearch.text?.clear()
            viewModel.setSearchQuery("")
            hideKeyboard()
        }

        // Filters
        // Set Default Check
        binding.fragmentOrderServiceListChipGroupOrderStatus.check(R.id.fragmentOrderServiceListChipOrderStatusOngoing)

        binding.fragmentOrderServiceListChipGroupOrderStatus.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                viewModel.setSelectedChipId(checkedIds.first())
            }
        }
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // List Data
                launch {
                    viewModel.orderListState.collect { list ->
                        orderServiceAdapter.submitList(list)
                    }
                }

                // Initial Loading State (Center ProgressBar)
                launch {
                    viewModel.isLoading.collect { isLoading ->
                        binding.fragmentOrderServiceListProgressBarCenter.isVisible = isLoading
                        // Hide Empty state while loading
                        if (isLoading) binding.fragmentOrderServiceListLinearLayoutEmptyState.isVisible =
                            false
                    }
                }

                // Load More State (Bottom Linear Indicator)
                launch {
                    viewModel.isLoadingMore.collect { isLoadingMore ->
                        binding.fragmentOrderServiceListProgressIndicator.isVisible = isLoadingMore
                        if (isLoadingMore) binding.fragmentOrderServiceListProgressIndicator.show()
                        else binding.fragmentOrderServiceListProgressIndicator.hide()
                    }
                }

                // Empty State
                launch {
                    viewModel.isEmptyState.collect { isEmpty ->
                        binding.fragmentOrderServiceListLinearLayoutEmptyState.isVisible = isEmpty
                        binding.fragmentOrderServiceListRecyclerViewOrderServiceList.isVisible =
                            !isEmpty
                    }
                }
            }
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
            OrderServiceListFragmentDirections.actionOrderServiceListFragmentToOrderServiceDetailFragment(
                orderService, mainGraphViewModel.getCurrentUser()
            )
        } else {
            OrderServiceListFragmentDirections.actionOrderServiceListFragmentToServiceProcessFragment(
                orderService.id!!
            )
        }

        findNavController().navigate(directions, extras)
    }

    private fun hideKeyboard() {
        val imm =
            requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
        binding.fragmentOrderServiceListEditTextSearch.clearFocus()
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
        binding.fragmentOrderServiceListRecyclerViewOrderServiceList.adapter = null
        super.onDestroyView()
    }

    override val progressIndicator: LinearProgressIndicator
        get() = binding.fragmentOrderServiceListProgressIndicator
}