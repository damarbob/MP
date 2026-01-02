package id.monpres.app.ui.payment

import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.annotation.RawRes
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import dev.androidbroadcast.vbpd.viewBinding
import id.monpres.app.R
import id.monpres.app.databinding.BottomSheetPaymentGuideBinding
import id.monpres.app.usecase.SaveImageToGalleryUseCase
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PaymentGuideBottomSheetFragment : BottomSheetDialogFragment(R.layout.bottom_sheet_payment_guide) {
    private val binding by viewBinding(BottomSheetPaymentGuideBinding::bind)

    @Inject
    lateinit var saveImageToGalleryUseCase: SaveImageToGalleryUseCase

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val guideResId = arguments?.getInt(KEY_GUIDE_RES_ID, 0) ?: 0
        val guideTitle = arguments?.getString(KEY_GUIDE_TITLE)

        binding.bottomSheetPaymentGuideTextViewGuideTitle.text =
            guideTitle ?: getString(R.string.payment_guide)

        if (guideResId != 0) {
            setupWebView()
            // Load the HTML from the raw resource
            val htmlString = readHtmlFromRawResource(guideResId)
            binding.bottomSheetPaymentGuideWebViewPaymentGuide.loadDataWithBaseURL(
                null,
                htmlString,
                "text/html",
                "UTF-8",
                null
            )
        } else {
            // You could also load a raw HTML string if you passed that instead
            binding.bottomSheetPaymentGuideWebViewPaymentGuide.loadData(
                getString(R.string.html_body_guide_not_available),
                "text/html",
                "UTF-8"
            )
        }
    }

    private fun readHtmlFromRawResource(@RawRes resId: Int): String {
        return try {
            requireContext().resources.openRawResource(resId)
                .bufferedReader()
                .use { it.readText() }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading raw resource: $resId", e)
            getString(R.string.html_body_error_loading_guide)
        }
    }

    private fun setupWebView() {
        binding.bottomSheetPaymentGuideWebViewPaymentGuide.apply {
            settings.javaScriptEnabled = false
            setBackgroundColor(0)

            // *** THIS IS THE CRITICAL NEW PART ***
            // Set a custom WebViewClient to intercept URL clicks
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    if (url != null && url.startsWith("app:")) {
                        // We've clicked one of our custom links.
                        handleAppAction(url)
                        return true // Return true to say "we've handled this, don't navigate"
                    }
                    // For all other links (http, https, etc.), let the WebView handle it.
                    return super.shouldOverrideUrlLoading(view, url)
                }
            }
        }
    }

    private fun handleAppAction(url: String) {
        when (url) {
            "app:save_image_qris" -> {
                // Get the bitmap from the drawable resource
                val drawable = ContextCompat.getDrawable(requireContext(), R.drawable.qris_mp)
                if (drawable is BitmapDrawable) {
                    val bitmap = drawable.bitmap
                    // Use the UseCase
                    viewLifecycleOwner.lifecycleScope.launch {
                        val result = saveImageToGalleryUseCase(bitmap, "Monpres QRIS")
                        result.onSuccess {
                            Toast.makeText(
                                requireContext(),
                                getString(R.string.image_saved_to_gallery),
                                Toast.LENGTH_SHORT
                            ).show()
                        }.onFailure {
                            Toast.makeText(
                                requireContext(),
                                getString(R.string.error_failed_to_save_x, getString(R.string.qris)),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
            // You could add other actions here, e.g., "app:copy_account_number"
        }
    }

    companion object {
        const val TAG = "PaymentGuideBottomSheet"
        const val KEY_GUIDE_RES_ID = "guide_res_id"
        const val KEY_GUIDE_TITLE = "guide_title"

        fun newInstance(@RawRes guideResId: Int?, title: String? = null): PaymentGuideBottomSheetFragment {
            return PaymentGuideBottomSheetFragment().apply {
                arguments = bundleOf(
                    KEY_GUIDE_RES_ID to guideResId,
                    KEY_GUIDE_TITLE to title
                )
            }
        }
    }
}
