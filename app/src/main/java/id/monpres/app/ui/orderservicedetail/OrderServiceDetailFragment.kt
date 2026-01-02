package id.monpres.app.ui.orderservicedetail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
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
import com.google.android.material.transition.MaterialContainerTransform
import dagger.hilt.android.AndroidEntryPoint
import dev.androidbroadcast.vbpd.viewBinding
import id.monpres.app.MainApplication
import id.monpres.app.R
import id.monpres.app.databinding.FragmentOrderServiceDetailBinding
import id.monpres.app.enums.OrderStatus
import id.monpres.app.enums.PartnerCategory
import id.monpres.app.enums.UserRole
import id.monpres.app.enums.VehiclePowerSource
import id.monpres.app.enums.VehicleTransmission
import id.monpres.app.model.MontirPresisiUser
import id.monpres.app.model.OrderService
import id.monpres.app.model.PaymentMethod
import id.monpres.app.model.VehicleType
import id.monpres.app.ui.adapter.OrderItemAdapter
import id.monpres.app.ui.common.interfaces.IOrderServiceProvider
import id.monpres.app.ui.itemdecoration.SpacingItemDecoration
import id.monpres.app.usecase.IndonesianCurrencyFormatter
import id.monpres.app.usecase.SaveImageToGalleryUseCase
import id.monpres.app.usecase.SavePdfToDownloadsUseCase
import id.monpres.app.utils.dpToPx
import id.monpres.app.utils.enumByNameIgnoreCaseOrNull
import id.monpres.app.utils.setMargins
import id.monpres.app.utils.toDateTimeDisplayString
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.DateFormat
import javax.inject.Inject

@AndroidEntryPoint
class OrderServiceDetailFragment : Fragment(R.layout.fragment_order_service_detail),
    IOrderServiceProvider {

    companion object {
        fun newInstance() = OrderServiceDetailFragment()
        val TAG = OrderServiceDetailFragment::class.simpleName
    }

    private val viewModel: OrderServiceDetailViewModel by viewModels()

    private val args: OrderServiceDetailFragmentArgs by navArgs()

    private val binding by viewBinding(FragmentOrderServiceDetailBinding::bind)

    private lateinit var orderService: OrderService
    private var currentUser: MontirPresisiUser? = null

    @Inject
    lateinit var saveImageToGalleryUseCase: SaveImageToGalleryUseCase

    @Inject
    lateinit var savePdfToDownloadsUseCase: SavePdfToDownloadsUseCase

    private val indonesianCurrencyFormatter = IndonesianCurrencyFormatter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set the transition for this fragment
        sharedElementEnterTransition = MaterialContainerTransform().apply {
            drawingViewId = R.id.nav_host_fragment_activity_main
            scrimColor = Color.TRANSPARENT
            setAllContainerColors(
                MaterialColors.getColor(
                    requireContext(),
                    com.google.android.material.R.attr.colorSurfaceContainer,
                    resources.getColor(
                        R.color.md_theme_surfaceContainer,
                        requireContext().theme
                    )
                )
            )
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
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
//            WindowInsetsCompat.CONSUMED
            windowInsets
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.fragmentOrderServiceDetailFloatingToolbarLayout) { v, windowInsets ->
            val insets =
                windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())

            v.setMargins(
                bottom = insets.bottom + 16.dpToPx(requireActivity()),
                left = insets.left + 16.dpToPx(requireActivity()),
                right = insets.right + 16.dpToPx(requireActivity())
            )

            // Return CONSUMED if you don't want the window insets to keep passing
            // down to descendant views.
            WindowInsetsCompat.CONSUMED
        }

        orderService = args.orderService
        currentUser = args.currentUser

        setupView()
        setupListeners()
    }

    private fun saveInvoiceAsImage() {
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

    private fun shareInvoiceAsImage() {

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
        var screenshot: Bitmap?
        try {
            // Use the view's actual width and height, not the measured ones ---
            // measuredWidth can sometimes be 0 if the view is not yet laid out.
            screenshot = createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)

            // Now draw this bitmap on a canvas
            val canvas = Canvas(screenshot)

            // Draw the window's background color first ---
            // Get the theme's surface color to use as a base to avoid pure white/black.
            val bgColor = MaterialColors.getColor(
                v.context,
                com.google.android.material.R.attr.colorSurface,
                0
            )
            canvas.drawColor(bgColor)

            // Draw the view's own background (e.g., rounded corners shape) ---
            v.background?.draw(canvas)

            // Draw the view's content (text, children, etc.) on top ---
            v.draw(canvas)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to capture screenshot because: " + e.message)
            // Return null explicitly on failure
            return null
        }
        // return the bitmap
        return screenshot
    }

    /**
     * Captures the view as a bitmap and saves it as a PDF file to the device's public Downloads directory.
     */
    private fun saveInvoiceAsPdf() {
        val bitmap =
            getScreenShotFromView(binding.fragmentOrderServiceDetailLinearLayoutDetailContainer)
        if (bitmap == null) {
            Toast.makeText(requireContext(), "Failed to capture invoice.", Toast.LENGTH_SHORT)
                .show()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val filename = "Invoice-${orderService.id?.take(8)}.pdf"
            val result = savePdfToDownloadsUseCase(bitmap, filename)

            result.onSuccess {
                Toast.makeText(
                    requireContext(),
                    "PDF saved to Downloads folder",
                    Toast.LENGTH_SHORT
                ).show()
            }.onFailure { error ->
                Log.e(TAG, "Failed to save PDF", error)
                Toast.makeText(requireContext(), "Error: Failed to save PDF.", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    /**
     * Captures the view as a bitmap, saves it as a temporary PDF in the app's cache,
     * and triggers the system's share sheet.
     */
    private fun shareInvoiceAsPdf() {
        val bitmap =
            getScreenShotFromView(binding.fragmentOrderServiceDetailLinearLayoutDetailContainer)
        if (bitmap == null) {
            Toast.makeText(requireContext(), "Failed to capture invoice.", Toast.LENGTH_SHORT)
                .show()
            return
        }

        // 1. Create a PDF document
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        page.canvas.drawBitmap(bitmap, 0f, 0f, null)
        pdfDocument.finishPage(page)

        // 2. Save the PDF to a temporary file in the app's cache directory
        val pdfFile = File(requireContext().cacheDir, "invoice_to_share.pdf")
        try {
            FileOutputStream(pdfFile).use {
                pdfDocument.writeTo(it)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error writing temporary PDF for sharing", e)
            Toast.makeText(
                requireContext(),
                "Could not prepare PDF for sharing.",
                Toast.LENGTH_SHORT
            ).show()
            pdfDocument.close()
            return
        }
        pdfDocument.close() // Always close the document

        // 3. Get a content URI using FileProvider
        val uri: Uri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.provider",
            pdfFile
        )

        // 4. Create the share intent
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf" // Set the MIME type to PDF
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Invoice for Order #${orderService.id?.take(8)}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        // 5. Launch the chooser
        requireContext().startActivity(
            Intent.createChooser(shareIntent, getString(R.string.share))
        )
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
                if (orderService.status == OrderStatus.COMPLETED) getString(R.string.invoice) else orderService.status?.labelResId?.let {
                    getString(it)
                }
            fragmentOrderServiceDetailServiceName.text =
                MainApplication.services?.find { it.id == orderService.serviceId }?.name
            fragmentOrderServiceDetailDate.text =
                orderService.updatedAt.toDateTimeDisplayString(
                    dateStyle = DateFormat.MEDIUM,
                    timeStyle = DateFormat.LONG
                )

            fragmentOrderServiceDetailPrice.text =
                if (orderService.price != null) indonesianCurrencyFormatter(orderService.price!!) else ""

            // Logic Update: Admin sees what the Customer sees (The Partner/Provider details)
            fragmentOrderServiceDetailTextViewUserName.text =
                when (currentUser?.role) {
                    UserRole.PARTNER -> orderService.user?.displayName ?: "-"
                    // For Customer and Admin, show the Partner (Service Provider)
                    UserRole.CUSTOMER, UserRole.ADMIN -> orderService.partner?.displayName ?: "-"
                    else -> "-"
                }

            fragmentOrderServiceDetailTextViewUserDetail.text =
                when (currentUser?.role) {
                    UserRole.PARTNER -> getString(R.string.customer)
                    // Admin sees "Partner" label, matching the Invoice format
                    UserRole.CUSTOMER, UserRole.ADMIN -> getString(R.string.partner)
                    else -> ""
                }

            // Admin sees both Customer and Partner
            if (currentUser?.role == UserRole.ADMIN) {
                fragmentOrderServiceDetailLinearLayoutUserOrPartnerContainer2.visibility =
                    View.VISIBLE
                fragmentOrderServiceDetailTextViewUserName2.text =
                    orderService.user?.displayName ?: "-"
                fragmentOrderServiceDetailTextViewUserDetail2.text = getString(R.string.customer)
            } else {
                fragmentOrderServiceDetailLinearLayoutUserOrPartnerContainer2.visibility = View.GONE
            }

            // General info
            fragmentOrderServiceDetailInvoiceNumber.text = orderService.id ?: ""
            fragmentOrderServiceDetailPaymentMethod.text =
                PaymentMethod.getDefaultPaymentMethodById(
                    requireContext(),
                    orderService.paymentMethod ?: ""
                )?.name ?: ""
            fragmentOrderServiceDetailPartner.text = orderService.partnerId ?: ""
            fragmentOrderServiceDetailVehicle.text = orderService.vehicle?.name ?: ""
            val issueEnum = orderService.issue?.let { PartnerCategory.fromName(it) }
            val issueString = issueEnum?.let { getString(it.label) } ?: orderService.issue
            ?: getString(R.string.unknown_issue)
            fragmentOrderServiceDetailIssue.text = issueString
            fragmentOrderServiceDetailIssueDescription.text = orderService.issueDescription ?: ""

            fragmentOrderServiceDetailRegistrationNumber.text =
                orderService.vehicle?.registrationNumber ?: "-"
            fragmentOrderServiceDetailLicensePlateNumber.text =
                orderService.vehicle?.licensePlateNumber ?: "-"
            fragmentOrderServiceDetailType.text = VehicleType.getSampleList(requireContext())
                .find { it.id == orderService.vehicle?.typeId }?.name ?: "-"
            orderService.vehicle?.powerSource
                ?.let { enumByNameIgnoreCaseOrNull<VehiclePowerSource>(it) }
                ?.let { fragmentOrderServiceDetailPowerSource.text = getString(it.label) }
            orderService.vehicle?.transmission
                ?.let { enumByNameIgnoreCaseOrNull<VehicleTransmission>(it) }
                ?.let { fragmentOrderServiceDetailTransmission.text = getString(it.label) }
            fragmentOrderServiceDetailWheelDrive.text = orderService.vehicle?.wheelDrive ?: "-"
            fragmentOrderServiceDetailProductionYear.text = orderService.vehicle?.year ?: "-"
            fragmentOrderServiceDetailSeat.text = orderService.vehicle?.seat ?: "-"
            fragmentOrderServiceDetailPowerOutput.text = orderService.vehicle?.powerOutput ?: "-"

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
        binding.apply {
            fragmentOrderServiceDetailButtonShareAsImage.setOnClickListener {
                shareInvoiceAsImage()
            }
            fragmentOrderServiceDetailButtonSaveAsImage.setOnClickListener {
                saveInvoiceAsImage()
            }

            fragmentOrderServiceDetailButtonShareAsPdf.setOnClickListener {
                shareInvoiceAsPdf()
            }
            fragmentOrderServiceDetailButtonSaveAsPdf.setOnClickListener {
                saveInvoiceAsPdf()
            }

            // Copy Invoice Number to Clipboard on Long Click
            fragmentOrderServiceDetailInvoiceNumber.setOnLongClickListener {
                val textToCopy = fragmentOrderServiceDetailInvoiceNumber.text.toString()
                if (textToCopy.isNotBlank()) {
                    val clipboard =
                        requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Invoice Number", textToCopy)
                    clipboard.setPrimaryClip(clip)

                    // Only show toast for Android 12 and below.
                    // Android 13+ (Tiramisu) shows a system UI confirmation automatically.
                    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.copied),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                true
            }
        }
    }

    override fun getCurrentOrderService(): OrderService? {
        // Return the local variable we already have populated
        return if (::orderService.isInitialized) orderService else null
    }
}
