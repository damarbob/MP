package id.monpres.app.ui.auth.adminverification

import android.os.Bundle
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.transition.MaterialSharedAxis
import dagger.hilt.android.AndroidEntryPoint
import dev.androidbroadcast.vbpd.viewBinding
import id.monpres.app.AuthViewModel
import id.monpres.app.R
import id.monpres.app.databinding.FragmentAdminVerificationBinding
import id.monpres.app.usecase.OpenWhatsAppUseCase
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AdminVerificationFragment : Fragment(R.layout.fragment_admin_verification) {

    companion object {
        fun newInstance() = AdminVerificationFragment()
    }

    private val authViewModel: AuthViewModel by activityViewModels()
    private val binding by viewBinding(FragmentAdminVerificationBinding::bind)

    private val openWhatsAppUseCase = OpenWhatsAppUseCase()

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
                authViewModel.adminVerificationState.collect { state ->
                    when (state) {
                        is AuthViewModel.AdminVerificationState.Pending -> {
                            binding.fragmentAdminVerificationTextViewTitle.text =
                                getString(R.string.admin_verification)
                            binding.fragmentAdminVerificationTextViewDescription.text =
                                getString(R.string.please_wait_for_admin_verification)
                            binding.fragmentAdminVerificationLottieAnimationView.setAnimation("lotties/car_json.json")
                        }
                        is AuthViewModel.AdminVerificationState.Rejected -> {
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
    }

    private fun setupListeners() {
        binding.fragmentAdminVerificationButtonChatAdmin.setOnClickListener {
            openWhatsAppUseCase(requireContext(), "62895346018055") // TODO: change to admin number
        }

        binding.fragmentAdminVerificationButtonLogout.setOnClickListener {
            authViewModel.logout()
        }
    }
}