package id.monpres.app.ui.orderservicedetail

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.insets.GradientProtection
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.transition.MaterialSharedAxis
import id.monpres.app.R
import id.monpres.app.databinding.FragmentOrderServiceDetailBinding
import id.monpres.app.model.OrderService
import id.monpres.app.model.Summary
import id.monpres.app.ui.adapter.SummaryAdapter
import id.monpres.app.ui.itemdecoration.SpacingItemDecoration
import id.monpres.app.utils.toDateTimeDisplayString
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Locale

class OrderServiceDetailFragment : Fragment() {

    companion object {
        fun newInstance() = OrderServiceDetailFragment()
        val TAG = OrderServiceDetailFragment::class.simpleName
    }

    private val viewModel: OrderServiceDetailViewModel by viewModels()

    private val args: OrderServiceDetailFragmentArgs by navArgs()

    private lateinit var binding: FragmentOrderServiceDetailBinding

    private lateinit var orderService: OrderService

    private val permissionQueue: MutableList<String> =
        mutableListOf() // Queue for individual permissions
    private lateinit var currentPermission: String

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            when {
                isGranted -> {
                    processNextPermission()
                }

                shouldShowRequestPermissionRationale(currentPermission) -> {
                    if (currentPermission == Manifest.permission.READ_MEDIA_IMAGES && Build.VERSION.SDK_INT == Build.VERSION_CODES.TIRAMISU) {
                        showPermissionRationale(currentPermission)
                    } else if (currentPermission != Manifest.permission.READ_MEDIA_IMAGES) {
                        showPermissionRationale(currentPermission)
                    } else processNextPermission()
                }

                else -> {
                    if (currentPermission == Manifest.permission.READ_MEDIA_IMAGES && Build.VERSION.SDK_INT == Build.VERSION_CODES.TIRAMISU) {
                        showPermissionSettingsDialog(currentPermission)
                    } else if (currentPermission != Manifest.permission.READ_MEDIA_IMAGES) {
                        showPermissionSettingsDialog(currentPermission)
                    } else processNextPermission()
                }
            }
        }

    // Add permissions to the queue and start processing
    private fun checkPermissions(permissions: List<String>) {
        permissionQueue.clear()
        permissionQueue.addAll(permissions)
        processNextPermission()
    }

    // Process the next permission in the queue
    private fun processNextPermission() {
        if (permissionQueue.isNotEmpty()) {
            currentPermission = permissionQueue.removeAt(0)
            requestPermissionLauncher.launch(currentPermission)
        }
    }

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
                windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(
                insets.left,
                0,
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

    private fun saveInvoice() {
        if (hasReadMediaImagesPermission()) {
            // get the bitmap of the view using
            // getScreenShotFromView method it is
            // implemented below
            val bitmap =
                getScreenShotFromView(binding.fragmentOrderServiceDetailLinearLayoutDetailContainer)

            // if bitmap is not null then
            // save it to gallery
            if (bitmap != null) {
                saveMediaToStorage(bitmap)
            }
        } else {
            checkPermissions(getMediaPermissions().toList())
        }
    }

    private fun shareInvoice() {
        if (hasReadMediaImagesPermission()) {
            // get the bitmap of the view using
            // getScreenShotFromView method it is
            // implemented below
            val bitmap =
                getScreenShotFromView(binding.fragmentOrderServiceDetailLinearLayoutDetailContainer)

            // if bitmap is not null then
            // save it to gallery
            if (bitmap != null) {
                shareBitmap(bitmap)
            }
        } else {
            checkPermissions(getMediaPermissions().toList())
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


    // this method saves the image to gallery
    private fun saveMediaToStorage(bitmap: Bitmap) {
        // Generating a file name
        val filename = "${getString(R.string.app_name)}-${System.currentTimeMillis()}.jpg"

        // Output stream
        var fos: OutputStream? = null

        // For devices running android >= Q
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // getting the contentResolver
            requireActivity().contentResolver?.also { resolver ->

                // Content resolver will process the contentvalues
                val contentValues = ContentValues().apply {

                    // putting file information in content values
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                }

                // Inserting the contentValues to
                // contentResolver and getting the Uri
                val imageUri: Uri? =
                    resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

                // Opening an outputstream with the Uri that we got
                fos = imageUri?.let { resolver.openOutputStream(it) }
            }
        } else {
            // These for devices running on android < Q
            val imagesDir =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val image = File(imagesDir, filename)
            fos = FileOutputStream(image)
        }

        fos?.use {
            // Finally writing the bitmap to the output stream that we opened
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
            Toast.makeText(
                requireContext(),
                getString(R.string.invoice_saved_to_gallery),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun shareBitmap(bitmap: Bitmap) {
        val file = File(requireContext().cacheDir, "shared_image.jpg")
        file.outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
        }

        val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.provider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        requireContext().startActivity(Intent.createChooser(shareIntent,
            getString(R.string.share_image)))
    }


    private fun setupView() {
        // TODO: Set up view
        with(binding) {
            root.setProtections(
                listOf(
                    GradientProtection(
                        WindowInsetsCompat.Side.TOP, resources.getColor(
                            R.color.md_theme_surfaceContainer, null
                        )
                    )
                )
            )
            // Header
            fragmentOrderServiceDetailTitle.text =
                getString(R.string.x_x, orderService.name, orderService.status?.name)
            fragmentOrderServiceDetailDate.text =
                orderService.updatedAt.toDateTimeDisplayString(dateStyle = DateFormat.FULL, timeStyle = DateFormat.LONG)
            val idrFormat = NumberFormat.getCurrencyInstance(
                Locale.Builder().setRegion("ID").setLanguage("id").build()
            )
            idrFormat.maximumFractionDigits = 0
            fragmentOrderServiceDetailPrice.text =
                if (orderService.price != null) idrFormat.format(orderService.price) else ""

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
            fragmentOrderServiceDetailSummaryTotal.text =
                if (orderService.price != null) idrFormat.format(orderService.price) else ""
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

    private fun showPermissionRationale(permission: String) {
        val permissionName = extractPermissionName(permission)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(resources.getString(R.string.x_permission_required, permissionName))
            .setMessage(getString(R.string.please_grant_permission))
            .setPositiveButton(resources.getString(R.string.okay)) { _, _ ->
                requestPermissionLauncher.launch(permission)
            }
            .setNegativeButton(resources.getString(R.string.cancel)) { _, _ ->
                processNextPermission()
            }
            .create()
            .show()
    }

    private fun showPermissionSettingsDialog(permission: String) {
        val permissionName = extractPermissionName(permission)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.x_permission_is_not_granted, permissionName))
            .setMessage(getString(R.string.this_permission_is_disabled))
            .setPositiveButton(getString(R.string.go_to_settings)) { _, _ ->
                openAppSettings()
            }
            .setNegativeButton(resources.getString(R.string.cancel)) { _, _ ->

            }
            .create()
            .show()
    }

    private fun extractPermissionName(permission: String): String {
        return when (permission) {
            Manifest.permission.CAMERA -> {
                getString(R.string.camera)
            }

            Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED, Manifest.permission.READ_EXTERNAL_STORAGE -> {
                getString(R.string.gallery)
            }

            Manifest.permission.POST_NOTIFICATIONS -> {
                getString(R.string.notification)
            }

            else -> {
                ""
            }
        }
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        val uri: Uri = Uri.fromParts("package", requireActivity().packageName, null)
        intent.data = uri
        startActivity(intent)
    }

    fun hasReadMediaImagesPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            ) == PackageManager.PERMISSION_GRANTED
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED
        } else ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun getMediaPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
}