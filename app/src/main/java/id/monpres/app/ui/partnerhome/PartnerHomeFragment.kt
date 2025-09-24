package id.monpres.app.ui.partnerhome

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
import com.google.android.material.carousel.CarouselLayoutManager
import com.google.android.material.carousel.HeroCarouselStrategy
import dagger.hilt.android.AndroidEntryPoint
import id.monpres.app.MainViewModel
import id.monpres.app.databinding.FragmentPartnerHomeBinding
import id.monpres.app.enums.OrderStatus
import id.monpres.app.enums.OrderStatusType
import id.monpres.app.model.Banner
import id.monpres.app.repository.UserRepository
import id.monpres.app.ui.BaseFragment
import id.monpres.app.ui.adapter.BannerAdapter
import id.monpres.app.ui.adapter.OrderServiceAdapter
import id.monpres.app.ui.itemdecoration.SpacingItemDecoration
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // TODO: Use the ViewModel
    }

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
        setupOrderServiceObservers()

        return binding.root
    }

    private fun setupUI() {
        /* Initialize UI */
        binding.fragmentPartnerHomeProgressIndicator.visibility = View.GONE
    }

    private fun setupHeroCarousel() {
        /* Setup carousel */
        val carousel = binding.fragmentPartnerHomeRecyclerViewCarousel
        carousel.adapter = BannerAdapter(
            listOf(
                Banner(
                    "https://monpres.id/wp-content/uploads/2023/09/WhatsApp-Image-2023-09-27-at-14.10.13.jpeg",
                    2
                ),
                Banner(
                    "https://monpres.id/wp-content/uploads/2023/09/WhatsApp-Image-2023-09-27-at-14.10.11-1-1536x1025.jpeg",
                    0
                ),
                Banner(
                    "https://monpres.id/wp-content/uploads/2023/09/WhatsApp-Image-2023-09-27-at-14.10.12.jpeg",
                    1
                ),
            )
        )
        carousel.layoutManager = CarouselLayoutManager(HeroCarouselStrategy())
    }

    fun setupOrderServiceRecyclerView() {
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

    fun setupOrderServiceObservers() {
        observeUiState(mainViewModel.partnerOrderServicesState) {
            orderServiceAdapter.submitList(it.take(5))

            binding.fragmentPartnerHomeButtonSeeAllOrderService.visibility =
                if (it.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    override fun showLoading(isLoading: Boolean) {
        binding.fragmentPartnerHomeProgressIndicator.visibility = if (isLoading) View.VISIBLE else View.GONE
    }
}