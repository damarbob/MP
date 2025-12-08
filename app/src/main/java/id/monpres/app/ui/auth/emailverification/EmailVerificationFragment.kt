package id.monpres.app.ui.auth.emailverification

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
import id.monpres.app.databinding.FragmentEmailVerificationBinding
import kotlinx.coroutines.launch

@AndroidEntryPoint
class EmailVerificationFragment : Fragment(R.layout.fragment_email_verification) {

    companion object {
        fun newInstance() = EmailVerificationFragment()
    }

    //    private val viewModel: EmailVerificationViewModel by viewModels()
    private val authViewModel: AuthViewModel by activityViewModels()
    private val binding by viewBinding(FragmentEmailVerificationBinding::bind)

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
        ViewCompat.setOnApplyWindowInsetsListener(binding.fragmentEmailVerificationNestedScrollView) { v, windowInsets ->
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
                authViewModel.countdownState.collect { state ->
                    when (state) {
                        is AuthViewModel.CountdownState.Idle -> {
                            binding.fragmentEmailVerificationButtonResendEmail.isEnabled = true
                            binding.fragmentEmailVerificationTextViewTimer.text = ""
                            binding.fragmentEmailVerificationButtonResendEmail.text =
                                getString(R.string.resend_verification_email)
                        }

                        is AuthViewModel.CountdownState.Active -> {
                            binding.fragmentEmailVerificationButtonResendEmail.isEnabled = false
                            binding.fragmentEmailVerificationTextViewTimer.text =
                                getString(
                                    R.string.resend_available_in_x_s,
                                    state.remainingSeconds
                                )
                            binding.fragmentEmailVerificationButtonResendEmail.text =
                                getString(R.string.email_sent)
                        }
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                authViewModel.emailVerificationState.collect { state ->
                    when (state) {
                        is AuthViewModel.EmailVerificationState.Sending -> {
                            binding.emailVerificationProgressIndicatorLoading.visibility =
                                View.VISIBLE
                        }

                        is AuthViewModel.EmailVerificationState.Sent -> {
                            binding.emailVerificationProgressIndicatorLoading.visibility = View.GONE
                        }

                        is AuthViewModel.EmailVerificationState.Error -> {
                            binding.emailVerificationProgressIndicatorLoading.visibility = View.GONE
                        }

                        else -> {
                            binding.emailVerificationProgressIndicatorLoading.visibility = View.GONE
                        }
                    }
                }
            }

        }
    }

    private fun setupListeners() {
        with(binding) {
            fragmentEmailVerificationButtonResendEmail.setOnClickListener {
                authViewModel.sendVerificationEmail()
            }

            fragmentEmailVerificationButtonLogout.setOnClickListener {
                authViewModel.logout()
            }

            fragmentEmailVerificationButtonCheck.setOnClickListener {
                it.isEnabled = false
                authViewModel.checkUser()
                it.postDelayed({ it.isEnabled = true }, 3000)
            }
        }
    }
}