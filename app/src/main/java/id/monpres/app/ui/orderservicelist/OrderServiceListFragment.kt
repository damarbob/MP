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
import com.google.android.material.transition.MaterialSharedAxis
import dagger.hilt.android.AndroidEntryPoint
import id.monpres.app.MainActivity
import id.monpres.app.MainViewModel
import id.monpres.app.databinding.FragmentOrderServiceListBinding
import id.monpres.app.enums.OrderStatus
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
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
//            val isAppbarShowing = (requireActivity() as MainActivity).supportActionBar?.isShowing ?: false
            v.setPadding(
                insets.left,
//                if (isAppbarShowing) 0 else insets.top,
                0,
                insets.right,
                insets.bottom
            )
            windowInsets
        }
    }

    fun setupOrderServiceListRecyclerView() {
        orderServiceAdapter = OrderServiceAdapter { orderService ->
            when (orderService.status) {
                OrderStatus.RETURNED, OrderStatus.FAILED, OrderStatus.CANCELLED, OrderStatus.COMPLETED -> {
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

    fun setupOrderServiceListObservers() {
        observeUiState(mainViewModel.userOrderServicesState) {
            orderServiceAdapter.submitList(it)
        }
    }

    override fun showLoading(isLoading: Boolean) {
        binding.fragmentOrderServiceListProgressIndicator.visibility =
            if (isLoading) View.VISIBLE else View.GONE
    }
}