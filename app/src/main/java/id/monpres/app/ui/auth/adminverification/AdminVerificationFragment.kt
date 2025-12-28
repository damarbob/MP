package id.monpres.app.ui.auth.adminverification

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.transition.MaterialSharedAxis
import dagger.hilt.android.AndroidEntryPoint
import dev.androidbroadcast.vbpd.viewBinding
import id.monpres.app.AuthViewModel
import id.monpres.app.MainApplication
import id.monpres.app.R
import id.monpres.app.databinding.FragmentAdminVerificationBinding
import id.monpres.app.libraries.ErrorLocalizer
import id.monpres.app.model.MontirPresisiUser
import id.monpres.app.ui.BaseFragment
import id.monpres.app.usecase.OpenWhatsAppUseCase
import id.monpres.app.utils.enumByNameIgnoreCaseOrNull
import id.monpres.app.utils.hideKeyboard
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AdminVerificationFragment : BaseFragment(R.layout.fragment_admin_verification) {

    companion object {
        fun newInstance() = AdminVerificationFragment()
        private val TAG = AdminVerificationFragment::class.simpleName
    }

    private val authViewModel: AuthViewModel by activityViewModels()
    private val viewModel: AdminVerificationViewModel by viewModels()

    private val binding by viewBinding(FragmentAdminVerificationBinding::bind)

    private val openWhatsAppUseCase = OpenWhatsAppUseCase()

    private var monpresUser: MontirPresisiUser? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Set the transition for this fragment
        enterTransition = MaterialSharedAxis(MaterialSharedAxis.Z, /* forward= */ true)
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.Z, /* forward= */ false)
        exitTransition = MaterialSharedAxis(MaterialSharedAxis.Z, /* forward= */ true)
        reenterTransition = MaterialSharedAxis(MaterialSharedAxis.Z, /* forward= */ false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ViewCompat.setOnApplyWindowInsetsListener(binding.fragmentAdminVerificationNestedScrollView) { v, windowInsets ->
            val insets =
                windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(insets.left, 0, insets.right, insets.bottom)
            windowInsets
        }

        setupObservers()
        setupListeners()
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    authViewModel.adminVerificationState,
                    authViewModel.monpresUser
                ) { adminVerificationState, monpresUser ->
                    Pair(adminVerificationState, monpresUser)
                }.collect { (adminVerificationState, monpresUser) ->
                    Log.d(
                        TAG,
                        "Admin verification state: $adminVerificationState, user: $monpresUser"
                    )
                    this@AdminVerificationFragment.monpresUser = monpresUser

                    when (adminVerificationState) {
                        is AuthViewModel.AdminVerificationState.Pending -> {
                            if (monpresUser?.facebookId.isNullOrBlank() && monpresUser?.instagramId.isNullOrBlank()) {
                                showEditSocMed(true)
                            }

                            binding.fragmentAdminVerificationTextViewTitle.text =
                                getString(R.string.admin_verification)
                            binding.fragmentAdminVerificationTextViewDescription.text =
                                getString(R.string.please_wait_for_admin_verification)
                            binding.fragmentAdminVerificationLottieAnimationView.setAnimation("lotties/car_json.json")
                        }

                        is AuthViewModel.AdminVerificationState.Rejected -> {
                            showEditSocMed(false)
                            binding.fragmentAdminVerificationTextViewTitle.text =
                                getString(R.string.verification_rejected)
                            binding.fragmentAdminVerificationTextViewDescription.text =
                                getString(R.string.your_account_has_been_rejected_by_admin)
                            binding.fragmentAdminVerificationLottieAnimationView.setAnimation("lotties/rejected.json")
                        }

                        else -> {}
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.errorEvent.collect { exception ->
                    Toast.makeText(
                        requireContext(),
                        ErrorLocalizer.getLocalizedErrorWithLog(requireContext(), exception),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.facebookId.collect {
                    binding.fragmentAdminVerificationInputEditFacebookId.setText(it.ifBlank { monpresUser?.facebookId })
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.instagramId.collect {
                    binding.fragmentAdminVerificationInputEditInstagramId.setText(it.ifBlank { monpresUser?.instagramId })
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect {
                    val state = enumByNameIgnoreCaseOrNull<AdminVerificationViewModel.AdminVerificationUiState>(it)
                    when(state) {
                        AdminVerificationViewModel.AdminVerificationUiState.VERIFICATION_UI, null -> {
                            showEditSocMed(false)
                        }
                        AdminVerificationViewModel.AdminVerificationUiState.EDIT_FORM_UI -> {
                            showEditSocMed(true)
                        }
                    }
                }
            }
        }
    }

    private fun setupListeners() {
        binding.fragmentAdminVerificationButtonChatAdmin.setOnClickListener {
            openWhatsAppUseCase(requireContext(), MainApplication.adminWANumber)
        }

        binding.fragmentAdminVerificationButtonLogout.setOnClickListener {
            authViewModel.logout()
        }

        binding.fragmentAdminVerificationButtonEditSocMed.setOnClickListener {
            showEditSocMed(true)
        }

        binding.fragmentAdminVerificationButtonCancelEdit.setOnClickListener {
            showEditSocMed(false)
        }

        // Instagram ID input end icon listener
        binding.fragmentAdminVerificationInputLayoutInstagramId.setEndIconOnClickListener {
            val instagramId =
                binding.fragmentAdminVerificationInputEditInstagramId.text.toString().trim()
            if (instagramId.isBlank()) {
                monpresUser?.instagramId?.let { igId -> viewModel.setInstagramId(igId) }
                return@setEndIconOnClickListener
            }
            viewModel.setInstagramId(instagramId)

            // Open instagram in browser
            val browserIntent =
                Intent(
                    Intent.ACTION_VIEW,
                    "https://instagram.com/$instagramId".toUri()
                )
            startActivity(browserIntent)
        }

        // Facebook ID input end icon listener
        binding.fragmentAdminVerificationInputLayoutFacebookId.setEndIconOnClickListener {
            val facebookId =
                binding.fragmentAdminVerificationInputEditFacebookId.text.toString().trim()
            if (facebookId.isBlank()) {
                monpresUser?.facebookId?.let { fbId -> viewModel.setFacebookId(fbId) }
                return@setEndIconOnClickListener
            }
            viewModel.setFacebookId(facebookId)

            // Open facebook in browser
            val browserIntent =
                Intent(
                    Intent.ACTION_VIEW,
                    "https://facebook.com/$facebookId".toUri()
                )
            startActivity(browserIntent)
        }

        binding.fragmentAdminVerificationButtonSave.setOnClickListener { button ->
            button.hideKeyboard()
            if (isFormValid()) {
                button.isEnabled = false
                val facebookId =
                    binding.fragmentAdminVerificationInputLayoutFacebookId.editText?.text.toString().trim()
                val instagramId =
                    binding.fragmentAdminVerificationInputLayoutInstagramId.editText?.text.toString().trim()
                val updatedData =
                    monpresUser?.copy(facebookId = facebookId, instagramId = instagramId)
                updatedData?.let { user ->
                    observeUiStateOneShot(viewModel.updateSocMed(user)) {
                        button.isEnabled = true
                        showEditSocMed(false)
                    }
                }
            } else {
                onFormTextChanged()
            }
        }
    }

    private fun onFormTextChanged() {
        binding.fragmentAdminVerificationInputLayoutFacebookId.editText?.doAfterTextChanged {
            isFacebookIdValid()
            isInstagramIdValid()
        }
        binding.fragmentAdminVerificationInputLayoutInstagramId.editText?.doAfterTextChanged {
            isInstagramIdValid()
            isFacebookIdValid()
        }
    }

    private fun isFormValid(): Boolean {
        return isFacebookIdValid() || isInstagramIdValid()
    }

    private fun isFacebookIdValid(): Boolean {
        with(binding.fragmentAdminVerificationInputLayoutFacebookId) {
            return if (editText?.text.isNullOrBlank() && binding.fragmentAdminVerificationInputEditInstagramId.text.isNullOrBlank()) {
                error =
                    getString(R.string.x_is_required, getString(R.string.social_media))
                false
            } else {
                apply {
                    error = null
                    isErrorEnabled = false
                }
                true
            }
        }
    }

    private fun isInstagramIdValid(): Boolean {
        with(binding.fragmentAdminVerificationInputLayoutInstagramId) {
            return if (editText?.text.isNullOrBlank() && binding.fragmentAdminVerificationInputEditFacebookId.text.isNullOrBlank()) {
                error =
                    getString(R.string.x_is_required, getString(R.string.social_media))
                false
            } else {
                apply {
                    error = null
                    isErrorEnabled = false
                }
                true
            }
        }
    }

    private fun showEditSocMed(show: Boolean) {
        if (show) {
            binding.fragmentAdminHomeLinearLayoutFormContent.visibility = View.VISIBLE
            binding.fragmentAdminHomeLinearLayoutVerificationContent.visibility = View.GONE
            viewModel.setUiState(AdminVerificationViewModel.AdminVerificationUiState.EDIT_FORM_UI)
        } else {
            binding.fragmentAdminHomeLinearLayoutFormContent.visibility = View.GONE
            binding.fragmentAdminHomeLinearLayoutVerificationContent.visibility = View.VISIBLE
            viewModel.setUiState(AdminVerificationViewModel.AdminVerificationUiState.VERIFICATION_UI)
        }
    }

    override val progressIndicator: LinearProgressIndicator
        get() = binding.fragmentAdminVerificationProgressIndicatorLoading
}