package id.monpres.app.ui.adminnewuser

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.net.toUri
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.i18n.phonenumbers.PhoneNumberUtil
import dagger.hilt.android.AndroidEntryPoint
import id.monpres.app.R
import id.monpres.app.databinding.FragmentAdminNewUserBinding
import id.monpres.app.model.MontirPresisiUser
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class AdminNewUserFragment : DialogFragment() {

    companion object {
        private const val ARG_USER = "user" // Key must match SavedStateHandle
        val TAG = AdminNewUserFragment::class.simpleName

        fun newInstance(user: MontirPresisiUser): AdminNewUserFragment {
            val fragment = AdminNewUserFragment()
            val args = Bundle().apply {
                putParcelable(ARG_USER, user)
            }
            fragment.arguments = args
            return fragment
        }
    }

    private val viewModel: AdminNewUserViewModel by viewModels()

    /* UI */
    // Use nullable binding to handle onDestroyView
    private var _binding: FragmentAdminNewUserBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminNewUserBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Setup UI (marquee, etc.)
        setupMarquee()

        // Setup button click listeners
        setupClickListeners()

        // Setup observers to listen to the ViewModel
        setupObservers()
    }

    private fun setupClickListeners() {
        binding.fragmentAdminNewUserButtonAccept.setOnClickListener {
            showConfirmationDialog(
                title = getString(R.string.accept_user),
                message = getString(R.string.are_you_sure),
                positiveButtonText = getString(R.string.accept)
            ) {
                viewModel.onAcceptClicked()
            }
        }
        binding.fragmentAdminNewUserButtonReject.setOnClickListener {
            showConfirmationDialog(
                title = getString(R.string.reject_user),
                message = getString(R.string.are_you_sure),
                positiveButtonText = getString(R.string.reject)
            ) {
                viewModel.onRejectClicked()
            }
        }
        binding.fragmentAdminNewUserButtonDelete.setOnClickListener {
            showConfirmationDialog(
                title = getString(R.string.delete_user),
                message = getString(R.string.are_you_sure),
                positiveButtonText = getString(R.string.delete)
            ) {
                viewModel.onDeleteClicked()
            }
        }
    }


    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                // Observe the user data
                launch {
                    viewModel.user.collect { user ->
                        user?.let { bindUserData(it) }
                    }
                }

                // Observe the loading state
                launch {
                    viewModel.isLoading.collect { isLoading ->
                        // Disable buttons when loading
                        binding.fragmentAdminNewUserButtonAccept.isEnabled = !isLoading
                        binding.fragmentAdminNewUserButtonReject.isEnabled = !isLoading
                        binding.fragmentAdminNewUserButtonDelete.isEnabled = !isLoading
                    }
                }

                // Observe one-time events
                launch {
                    viewModel.eventFlow.collect { event ->
                        when (event) {
                            is AdminNewUserEvent.ActionSuccess -> {
                                Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                                dismiss() // Close the dialog on success
                            }
                            is AdminNewUserEvent.ShowToast -> {
                                Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Helper function to bind user data to the UI.
     */
    private fun bindUserData(user: MontirPresisiUser) {
        val userCreatedAtTimestamp = user.createdAt
        val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
        val createdAtDate = userCreatedAtTimestamp?.let { Date(it.toLong()) }
        val formattedDate = createdAtDate?.let { sdf.format(it) } ?: "N/A"

        // Set WhatsApp link
        val phoneUtil = PhoneNumberUtil.getInstance()
        var whatsappNumber = user.phoneNumber
        try {
            val numberProto = phoneUtil.parse(whatsappNumber, "ID")
            whatsappNumber = "" + numberProto.countryCode + numberProto.nationalNumber
        } catch (e: Exception) {
            // Keep original number if parsing fails
        }
        val whatsappLink = "https://wa.me/$whatsappNumber"

        binding.apply {
            fragmentAdminNewUserTextViewTitle.text = user.displayName
            fragmentAdminNewUserTextViewSubtitle.text = getString(R.string.joined_at_x, formattedDate)
            fragmentAdminNewUserTextViewPhone.text = if (!user.phoneNumber.isNullOrBlank()) getString(R.string.x_whatsapp, user.phoneNumber) else getString(R.string.no_whatsapp_number)
            fragmentAdminNewUserTextViewInstagramId.text = if (!user.instagramId.isNullOrBlank()) getString(R.string.x_instagram, user.instagramId) else getString(R.string.no_instagram_id)
            fragmentAdminNewUserTextViewFacebookId.text = if (!user.facebookId.isNullOrBlank()) getString(R.string.x_facebook, user.facebookId) else getString(R.string.no_facebook_id)

            setCardClickListener(fragmentAdminNewUserCardViewPhone, user.phoneNumber, whatsappLink)
            setCardClickListener(fragmentAdminNewUserCardViewInstagramId, user.instagramId, "https://www.instagram.com/${user.instagramId}")
            setCardClickListener(fragmentAdminNewUserCardViewFacebookId, user.facebookId, "https://www.facebook.com/${user.facebookId}")
        }
    }

    /**
     * Helper to set a card's click listener only if the data is not empty.
     */
    private fun setCardClickListener(card: View, data: String?, url: String) {
        if (!data.isNullOrEmpty()) {
            card.setOnClickListener {
                val intent = Intent(Intent.ACTION_VIEW)
                intent.data = url.toUri()
                startActivity(intent)
            }
        } else {
            card.setOnClickListener(null)
            card.isClickable = false
        }
    }

    private fun setupMarquee() {
        val phoneTextView = binding.fragmentAdminNewUserTextViewPhone
        val instagramTextView = binding.fragmentAdminNewUserTextViewInstagramId
        val facebookTextView = binding.fragmentAdminNewUserTextViewFacebookId

        listOf(phoneTextView, instagramTextView, facebookTextView).forEach { textView ->
            textView.isSelected = true
            textView.isSingleLine = true
            textView.ellipsize = TextUtils.TruncateAt.MARQUEE
            textView.marqueeRepeatLimit = -1 // marquee_forever
        }
    }

    private fun showConfirmationDialog(
        title: String,
        message: String,
        positiveButtonText: String,
        onConfirm: () -> Unit
    ) {
        context?.let {
            MaterialAlertDialogBuilder(it)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                    dialog.dismiss()
                }
                .setPositiveButton(positiveButtonText) { _, _ -> onConfirm() }
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // Clean up binding to avoid memory leaks
    }
}