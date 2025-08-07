package id.monpres.app.ui.orderservicelist

import androidx.fragment.app.viewModels
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.transition.MaterialSharedAxis
import dagger.hilt.android.AndroidEntryPoint
import id.monpres.app.MainActivity
import id.monpres.app.R
import id.monpres.app.databinding.FragmentOrderServiceListBinding
import id.monpres.app.ui.BaseFragment
import id.monpres.app.ui.adapter.OrderServiceAdapter
import id.monpres.app.ui.itemdecoration.SpacingItemDecoration

@AndroidEntryPoint
class OrderServiceListFragment : BaseFragment() {

    companion object {
        fun newInstance() = OrderServiceListFragment()
    }

    private val viewModel: OrderServiceListViewModel by viewModels()

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
        // Set insets
        ViewCompat.setOnApplyWindowInsetsListener(binding.fragmentOrderServiceListNestedScrollView) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                insets.left,
                0,
                insets.right,
                insets.bottom
            )
            WindowInsetsCompat.CONSUMED
        }

        setupOrderServiceListRecyclerView()
        setupOrderServiceListObservers()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as MainActivity).binding.activityMainAppBarLayout.background =
            binding.root.background
    }

    fun setupOrderServiceListRecyclerView() {
        orderServiceAdapter = OrderServiceAdapter { orderService ->
            findNavController().navigate(
                OrderServiceListFragmentDirections.actionOrderServiceListFragmentToOrderServiceDetailFragment(
                    orderService
                )
            )
        }

        binding.fragmentOrderServiceListRecyclerViewOrderServiceList.apply {
            addItemDecoration(SpacingItemDecoration(2))
            adapter = orderServiceAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    fun setupOrderServiceListObservers() {
        observeUiState(viewModel.getOrderService()) {
            orderServiceAdapter.submitList(it)
        }
    }

    override fun showLoading(isLoading: Boolean) {
        binding.fragmentOrderServiceListProgressIndicator.visibility =
            if (isLoading) View.VISIBLE else View.GONE
    }
}