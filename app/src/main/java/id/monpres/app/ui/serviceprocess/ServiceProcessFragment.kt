package id.monpres.app.ui.serviceprocess

import android.animation.ValueAnimator
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.insets.GradientProtection
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.transition.TransitionManager
import com.google.android.material.color.MaterialColors
import com.google.android.material.transition.MaterialFade
import com.google.android.material.transition.MaterialSharedAxis
import com.google.firebase.Timestamp
import com.ncorti.slidetoact.SlideToActView
import com.ncorti.slidetoact.SlideToActView.OnSlideCompleteListener
import dagger.hilt.android.AndroidEntryPoint
import id.monpres.app.MainViewModel
import id.monpres.app.R
import id.monpres.app.databinding.FragmentServiceProcessBinding
import id.monpres.app.enums.OrderStatus
import id.monpres.app.enums.OrderStatusType
import id.monpres.app.enums.UserRole
import id.monpres.app.model.OrderService
import id.monpres.app.ui.BaseFragment
import id.monpres.app.utils.capitalizeWords
import id.monpres.app.utils.toDateTimeDisplayString
import java.text.DateFormat


@AndroidEntryPoint
class ServiceProcessFragment : BaseFragment() {

    companion object {
        fun newInstance() = ServiceProcessFragment()
        const val TAG = "ServiceProcessFragment"
        const val ARG_ORDER_SERVICE_ID = "orderServiceId"
    }

    private val viewModel: ServiceProcessViewModel by viewModels()
    private val mainViewModel: MainViewModel by activityViewModels()

    private val args: ServiceProcessFragmentArgs by navArgs()

    private lateinit var binding: FragmentServiceProcessBinding

    private lateinit var orderService: OrderService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set the transition for this fragment
        enterTransition = MaterialSharedAxis(MaterialSharedAxis.Y, /* forward= */ true)
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.Y, /* forward= */ false)
        exitTransition = MaterialSharedAxis(MaterialSharedAxis.Y, /* forward= */ true)
        reenterTransition = MaterialSharedAxis(MaterialSharedAxis.Y, /* forward= */ false)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentServiceProcessBinding.inflate(inflater, container, false)

        // Set insets
        ViewCompat.setOnApplyWindowInsetsListener(binding.fragmentServiceProcessNestedScrollView) { v, windowInsets ->
            val insets =
                windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(
                insets.left,
                0,
                insets.right,
                insets.bottom
            )
            windowInsets
        }

        mainViewModel.observeOrderServiceById(args.orderServiceId)

        setupObservers()
        setupListeners()

        return binding.root
    }

    private fun setupObservers() {
        Log.d(TAG, "OrderServiceId: ${args.orderServiceId}")
        when (mainViewModel.getCurrentUser()?.role) {
            UserRole.CUSTOMER ->
                observeUiState(mainViewModel.userOrderServiceState) { data ->
                    orderService = data
                    Log.d(TAG, "OrderService: $orderService")
                    setupView()
                    showCancelButton(orderService.status == OrderStatus.ORDER_PLACED)
                    showActionButton(false)
                    showCompleteStatus(orderService.status in OrderStatus.entries.filter { it.type == OrderStatusType.CLOSED })
                }


            UserRole.PARTNER ->
                observeUiState(mainViewModel.partnerOrderServiceState) { data ->
                    orderService = data
                    Log.d(TAG, "OrderService: $orderService")
                    setupView()
                    showCancelButton(orderService.status == OrderStatus.ORDER_PLACED)
                    showActionButton(orderService.status in OrderStatus.entries.filter { it.type != OrderStatusType.CLOSED })
                    showCompleteStatus(orderService.status in OrderStatus.entries.filter { it.type == OrderStatusType.CLOSED })
                }


            else -> {}
        }
    }

    private fun setupView() {
        binding.apply {
            root.setProtections(
                listOf(
                    GradientProtection(
                        WindowInsetsCompat.Side.TOP,
                        MaterialColors.getColor(
                            requireContext(),
                            com.google.android.material.R.attr.colorSurfaceContainer,
                            resources.getColor(
                                R.color.md_theme_surfaceContainer,
                                requireContext().theme
                            )
                        )
                    )
                )
            )

            val materialFade = MaterialFade().apply {
                duration = 150L
            }
            TransitionManager.beginDelayedTransition(binding.root, materialFade)
            fragmentServiceProcessTextViewTitle.text =
                orderService.status?.getLabel(requireContext())?.capitalizeWords() ?: "-"
            fragmentServiceProcessTextViewSubtitle.text =
                orderService.updatedAt.toDateTimeDisplayString(
                    dateStyle = DateFormat.FULL,
                    timeStyle = DateFormat.LONG
                )
            fragmentServiceProcessOrderId.text = orderService.id ?: "-"
            fragmentServiceProcessLocation.text =
                "${orderService.selectedLocationLat}, ${orderService.selectedLocationLng}"
            fragmentServiceProcessAddress.text =
                if (orderService.userAddress?.isNotBlank() == true) orderService.userAddress else "-"
            fragmentServiceProcessPartner.text = orderService.partnerId ?: "-"
            fragmentServiceProcessVehicle.text = orderService.vehicle?.name ?: "-"
            fragmentServiceProcessIssue.text = orderService.issue ?: "-"
            fragmentServiceProcessIssueDescription.text =
                if (orderService.issueDescription?.isNotBlank() == true) orderService.issueDescription else "-"

        }
    }

    private fun setupListeners() {
        binding.fragmentServiceProcessButtonCancel.setOnClickListener {
            // TODO: Cancel service
            findNavController().popBackStack()
        }

        binding.fragmentServiceProcessButtonComplete.setOnClickListener {
            findNavController().navigate(
                ServiceProcessFragmentDirections.actionServiceProcessFragmentToOrderServiceDetailFragment(
                    orderService
                )
            )
        }

        binding.fragmentServiceProcessActButton.onSlideCompleteListener =
            object : OnSlideCompleteListener {
                override fun onSlideComplete(view: SlideToActView) {

                    observeUiStateOneShot(
                        viewModel.updateOrderService(
                            orderService.copy(
                                updatedAt = Timestamp.now(),
                                status = orderService.status?.serviceNextProcess()
                            )
                        )
                    ) {
                        view.setCompleted(completed = false, withAnimation = true)
                    }
                }
            }
    }

    private fun showCancelButton(show: Boolean) {
        val materialFade = MaterialFade().apply {
            duration = 150L
        }
        TransitionManager.beginDelayedTransition(binding.root, materialFade)
        binding.fragmentServiceProcessButtonCancel.visibility =
            if (show) View.VISIBLE else View.GONE
    }

    private fun showActionButton(show: Boolean) {
        val materialFade = MaterialFade().apply {
            duration = 150L
        }
        TransitionManager.beginDelayedTransition(binding.root, materialFade)
        binding.fragmentServiceProcessActButton.visibility =
            if (show) View.VISIBLE else View.GONE
        binding.fragmentServiceProcessActButton.text =
            orderService.status?.serviceNextProcess()?.getLabel(requireContext())?.capitalizeWords()
                ?: "-"
    }

    private fun showCompleteStatus(show: Boolean) {
        val materialFade = MaterialFade().apply {
            duration = 150L
        }
        TransitionManager.beginDelayedTransition(binding.root, materialFade)
        binding.fragmentServiceProcessButtonComplete.visibility =
            if (show) View.VISIBLE else View.GONE
        if (show) {
            binding.fragmentServiceProcessProgressIndicator.isIndeterminate = false
            ValueAnimator.ofInt(0, 100).apply {
                duration = 1000
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener {
                    val progress = it.animatedValue as Int
                    binding.fragmentServiceProcessProgressIndicator.progress = progress
                }
                start()
            }
        }
        else binding.fragmentServiceProcessProgressIndicator.isIndeterminate = true
    }

    override fun onDestroy() {
        super.onDestroy()
        mainViewModel.stopObserve()
    }

    override fun showLoading(isLoading: Boolean) {
        // Already handled by showCompleteStatus()
    }
}