package id.monpres.app.ui.orderservicedetail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.transition.MaterialSharedAxis
import id.monpres.app.MainActivity
import id.monpres.app.databinding.FragmentOrderServiceDetailBinding
import id.monpres.app.model.OrderService
import id.monpres.app.model.Summary
import id.monpres.app.ui.adapter.SummaryAdapter
import id.monpres.app.ui.itemdecoration.SpacingItemDecoration

class OrderServiceDetailFragment : Fragment() {

    companion object {
        fun newInstance() = OrderServiceDetailFragment()
    }

    private val viewModel: OrderServiceDetailViewModel by viewModels()

    private val args: OrderServiceDetailFragmentArgs by navArgs()

    private lateinit var binding: FragmentOrderServiceDetailBinding

    private lateinit var orderService: OrderService

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
        binding = FragmentOrderServiceDetailBinding.inflate(inflater, container, false)

        // Set insets
        ViewCompat.setOnApplyWindowInsetsListener(binding.fragmentOrderServiceDetailNestedScrollView) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                insets.left,
                insets.top,
                insets.right,
                insets.bottom
            )
            WindowInsetsCompat.CONSUMED
        }

        orderService = args.orderService

        setupView()
        setupListeners()

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as MainActivity).supportActionBar?.hide()
    }

    private fun setupView() {
        // TODO: Set up view
        with(binding) {
            // Header
            fragmentOrderServiceDetailTitle.text = orderService.name ?: ""
            fragmentOrderServiceDetailDate.text =
                if (orderService.selectedDateMillis != null) orderService.selectedDateMillis.toString() else ""
            fragmentOrderServiceDetailPrice.text = if (orderService.price != null) orderService.price.toString() else ""

            // General info
            fragmentOrderServiceDetailInvoiceNumber.text = orderService.id ?: ""
            fragmentOrderServiceDetailPaymentMethod.text = orderService.type ?: ""
            fragmentOrderServiceDetailPartner.text = orderService.serviceId ?: ""
            fragmentOrderServiceDetailVehicle.text = orderService.vehicle?.name ?: ""
            fragmentOrderServiceDetailIssue.text = orderService.issue ?: ""
            fragmentOrderServiceDetailIssueDescription.text = orderService.issueDescription ?: ""

            // Distance detail
            fragmentOrderServiceDetailPartnerLocation.text = orderService.userLocationLat.toString()
            fragmentOrderServiceDetailServiceLocation.text =
                orderService.selectedLocationLat.toString()
            fragmentOrderServiceDetailDistance.text = orderService.selectedLocationLat.toString()

            // Summary
            fragmentOrderServiceDetailRecyclerViewSummaryDetail.apply {
                adapter =
                    SummaryAdapter(
                        listOf(
                            Summary("Wheel change", 1000000.toDouble()),
                            Summary("Oil change", 1000000.toDouble())
                        )
                    )
                layoutManager = LinearLayoutManager(requireContext())
                addItemDecoration(SpacingItemDecoration(4))
            }
            fragmentOrderServiceDetailSummaryTotal.text = if (orderService.price != null) orderService.price.toString() else ""
        }
    }

    private fun setupListeners() {
        // TODO: Set up listeners
        with(binding) {
            fragmentOrderServiceDetailButtonShare.setOnClickListener {

            }
            fragmentOrderServiceDetailButtonSave.setOnClickListener {

            }
        }
    }
}