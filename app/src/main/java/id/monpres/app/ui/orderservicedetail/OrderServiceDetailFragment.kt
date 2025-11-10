package id.monpres.app.ui.orderservicedetail

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.insets.GradientProtection
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.color.MaterialColors
import com.google.android.material.transition.MaterialSharedAxis
import dagger.hilt.android.AndroidEntryPoint
import id.monpres.app.R
import id.monpres.app.databinding.FragmentOrderServiceDetailBinding
import id.monpres.app.enums.UserRole
import id.monpres.app.model.MontirPresisiUser
import id.monpres.app.model.OrderService
import id.monpres.app.ui.adapter.OrderItemAdapter
import id.monpres.app.ui.itemdecoration.SpacingItemDecoration
import id.monpres.app.usecase.IndonesianCurrencyFormatter
import id.monpres.app.usecase.SaveImageToGalleryUseCase
import id.monpres.app.utils.toDateTimeDisplayString
import kotlinx.coroutines.launch
import java.io.File
import java.text.DateFormat
import javax.inject.Inject

@AndroidEntryPoint
class OrderServiceDetailFragment : Fragment() {

    companion object {
        fun newInstance() = OrderServiceDetailFragment()
        val TAG = OrderServiceDetailFragment::class.simpleName
    }

    private val viewModel: OrderServiceDetailViewModel by viewModels()

    private val args: OrderServiceDetailFragmentArgs by navArgs()

    private lateinit var binding: FragmentOrderServiceDetailBinding

    private lateinit var orderService: OrderService
    private var currentUser: MontirPresisiUser? = null

    @Inject
    lateinit var saveImageToGalleryUseCase: SaveImageToGalleryUseCase

    private val indonesianCurrencyFormatter = IndonesianCurrencyFormatter()

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
            val insets =
                windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                insets.left,
                0,
                insets.right,
                insets.bottom
            )
            WindowInsetsCompat.CONSUMED
        }

        orderService = args.orderService
        currentUser = args.currentUser

        setupView()
        setupListeners()

        return binding.root
    }

    private fun saveInvoice() {
        val bitmap =
            getScreenShotFromView(binding.fragmentOrderServiceDetailLinearLayoutDetailContainer)

        if (bitmap != null) {
            // Use the UseCase within a coroutine scope
            viewLifecycleOwner.lifecycleScope.launch {
                // Generating a file name
                val filename = "${getString(R.string.app_name)}-${System.currentTimeMillis()}.jpg"

                val result = saveImageToGalleryUseCase(bitmap, filename)

                result.onSuccess {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.invoice_saved_to_gallery),
                        Toast.LENGTH_SHORT
                    ).show()
                }.onFailure {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.failed_to_save_invoice), Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun shareInvoice() {

        val bitmap =
            getScreenShotFromView(binding.fragmentOrderServiceDetailLinearLayoutDetailContainer)

        // if bitmap is not null then
        // save it to gallery
        if (bitmap != null) {
            shareBitmap(bitmap)
        }

    }

    private fun getScreenShotFromView(v: View): Bitmap? {
        // create a bitmap object
        var screenshot: Bitmap? = null
        try {
            // inflate screenshot object
            // with Bitmap.createBitmap it
            // requires three parameters
            // width and height of the view and
            // the background color
            screenshot = createBitmap(v.measuredWidth, v.measuredHeight, Bitmap.Config.ARGB_8888)
            // Now draw this bitmap on a canvas
            val canvas = Canvas(screenshot)
            v.draw(canvas)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to capture screenshot because:" + e.message)
        }
        // return the bitmap
        return screenshot
    }

    fun shareBitmap(bitmap: Bitmap) {
        val file = File(requireContext().cacheDir, "shared_image.jpg")
        file.outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
        }

        val uri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.provider",
            file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        requireContext().startActivity(
            Intent.createChooser(
                shareIntent,
                getString(R.string.share_image)
            )
        )
    }


    private fun setupView() {
        with(binding) {
            root.setProtections(
                listOf(
                    GradientProtection(
                        WindowInsetsCompat.Side.TOP, MaterialColors.getColor(
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
            // Header
            fragmentOrderServiceDetailTitle.text =
                getString(R.string.x_x, orderService.name, orderService.status?.name)
            fragmentOrderServiceDetailDate.text =
                orderService.updatedAt.toDateTimeDisplayString(
                    dateStyle = DateFormat.FULL,
                    timeStyle = DateFormat.LONG
                )

            fragmentOrderServiceDetailPrice.text =
                if (orderService.price != null) indonesianCurrencyFormatter(orderService.price!!) else ""

            fragmentOrderServiceDetailTextViewUserName.text =
                when (currentUser?.role) {
                    UserRole.CUSTOMER -> orderService.partner?.displayName
                        ?: "-"

                    UserRole.PARTNER -> orderService.user?.displayName
                        ?: "-"

                    else -> "-"
                }
            fragmentOrderServiceDetailTextViewUserDetail.text =
                when (currentUser?.role) {
                    UserRole.CUSTOMER -> getString(R.string.partner)
                    UserRole.PARTNER -> getString(
                        R.string.customer
                    )

                    else -> ""
                }

            // General info
            fragmentOrderServiceDetailInvoiceNumber.text = orderService.id ?: ""
            fragmentOrderServiceDetailPaymentMethod.text = orderService.paymentMethod ?: ""
            fragmentOrderServiceDetailPartner.text = orderService.partnerId ?: ""
            fragmentOrderServiceDetailVehicle.text = orderService.vehicle?.name ?: ""
            fragmentOrderServiceDetailIssue.text = orderService.issue ?: ""
            fragmentOrderServiceDetailIssueDescription.text = orderService.issueDescription ?: ""

            // Distance detail
            fragmentOrderServiceDetailPartnerLocation.text = orderService.userLocationLat.toString()
            fragmentOrderServiceDetailServiceLocation.text =
                orderService.selectedLocationLat.toString()
            fragmentOrderServiceDetailDistance.text = orderService.selectedLocationLat.toString()

            // OrderItem
            fragmentOrderServiceDetailRecyclerViewSummaryDetail.apply {
                adapter = OrderItemAdapter().apply {
                    submitList(orderService.orderItems)
                }
                layoutManager = LinearLayoutManager(requireContext())
                addItemDecoration(SpacingItemDecoration(8))
            }
            fragmentOrderServiceDetailSummaryTotal.text =
                if (orderService.price != null) indonesianCurrencyFormatter(orderService.price!!) else ""
        }
    }

    private fun setupListeners() {
        with(binding) {
            fragmentOrderServiceDetailButtonShare.setOnClickListener {
                shareInvoice()
            }
            fragmentOrderServiceDetailButtonSave.setOnClickListener {
                saveInvoice()
            }
        }
    }
}