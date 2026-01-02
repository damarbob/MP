package id.monpres.app.ui.auth.register

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.transition.MaterialSharedAxis
import dev.androidbroadcast.vbpd.viewBinding
import id.monpres.app.AuthViewModel
import id.monpres.app.LoginActivity
import id.monpres.app.R
import id.monpres.app.databinding.FragmentRegisterBinding
import id.monpres.app.utils.hideKeyboard
import id.monpres.app.utils.requestFocusAndShowKeyboard
import kotlinx.coroutines.launch


class RegisterFragment : Fragment(R.layout.fragment_register) {

    companion object {
        private val TAG = RegisterFragment::class.simpleName
    }

    private val binding by viewBinding(FragmentRegisterBinding::bind)

    private val viewModel: RegisterViewModel by viewModels()
    private val authViewModel: AuthViewModel by activityViewModels()

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
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(insets.left, 0, insets.right, insets.bottom)
            windowInsets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.registerNestedScrollView) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(insets.left, 0, insets.right, insets.bottom)
            WindowInsetsCompat.CONSUMED
        }

        setupObservers()
        setupListeners()
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                authViewModel.registerState.collect { registerState ->
                    when (registerState) {
                        is AuthViewModel.RegisterState.Loading -> {
                            binding.registerProgressIndicatorLoading.visibility = View.VISIBLE
                            binding.registerButton.isEnabled = false
                        }

                        is AuthViewModel.RegisterState.Success -> {
                            binding.registerProgressIndicatorLoading.visibility = View.GONE
                            binding.registerButton.isEnabled = true
                            // Navigation is handled by AuthState in Activity
                        }

                        is AuthViewModel.RegisterState.Error -> {
                            binding.registerProgressIndicatorLoading.visibility = View.GONE
                            binding.registerButton.isEnabled = true
//                            Toast.makeText(requireContext(), registerState.message, Toast.LENGTH_SHORT)
//                                .show()
                        }

                        else -> {
                            binding.registerProgressIndicatorLoading.visibility = View.GONE
                            binding.registerButton.isEnabled = true
                        }
                    }
                }
            }
        }
    }

    private fun setupListeners() {
        /* Listeners */
        binding.registerCheckBoxTcAgreement.setOnCheckedChangeListener { buttonView, isChecked ->
            run {
                if (isChecked) {
                    // Confirm to open terms and conditions using external link
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle(getString(R.string.terms_and_conditions))
                        .setMessage(getString(R.string.you_will_open_an_external_link))
                        .setNeutralButton(resources.getString(R.string.cancel)) { dialog, _ ->
                            dialog.dismiss()
                        }
                        .setPositiveButton(getString(R.string.continue_)) { _, _ ->
                            // Open terms and conditions in browser
                            val browserIntent =
                                Intent(
                                    Intent.ACTION_VIEW,
                                    getString(R.string.terms_and_conditions_url).toUri()
                                )
                            startActivity(browserIntent)
                        }
                        .show()
                }

                validateAgreement()
            }
        }
        binding.registerButton.setOnClickListener {
            register(it)
        }
        binding.registerInputPassword.setOnEditorActionListener { editText, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                register(editText)
                true
            } else false
        }
        binding.registerGoogleButton.setOnClickListener {
            (activity as LoginActivity).signInWithGoogle()
        }
        binding.registerSignInText.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.registerInputFullName.doAfterTextChanged {
            if (binding.registerTextInputLayoutFullName.isErrorEnabled) {
                validateFullName()
            }
        }

        binding.registerInputEmailAddress.doAfterTextChanged {
            if (binding.registerTextInputLayoutEmail.isErrorEnabled) {
                validateEmail()
            }
        }

        binding.registerInputPassword.doAfterTextChanged {
            if (binding.registerTextInputLayoutPassword.isErrorEnabled) {
                validatePassword()
            }
        }

        binding.registerInputFullName.setOnFocusChangeListener { v, hasFocus ->
                if (!hasFocus) {
                    validateFullName()
                }
            }
        binding.registerInputEmailAddress.setOnFocusChangeListener { v, hasFocus ->
                if (!hasFocus) {
                    validateEmail()
                }
            }
        binding.registerInputPassword.setOnFocusChangeListener { v, hasFocus ->
                if (!hasFocus) {
                    validatePassword()
                }
            }
    }

    private fun register(view: View) {
        view.hideKeyboard()

        if (isValidated()) {
            // If we reach here, everything is valid
            val fullName = binding.registerInputFullName.text.toString()
            val email = binding.registerInputEmailAddress.text.toString()
            val password = binding.registerInputPassword.text.toString()
            val cb = binding.registerCheckBoxTcAgreement.isChecked

            authViewModel.registerWithEmailPassword(fullName, email, password)
        } else {
            with(binding) {
                when {
                    registerTextInputLayoutFullName.isErrorEnabled -> registerInputFullName.requestFocusAndShowKeyboard()
                    registerTextInputLayoutEmail.isErrorEnabled -> registerInputEmailAddress.requestFocusAndShowKeyboard()
                    registerTextInputLayoutPassword.isErrorEnabled -> registerInputPassword.requestFocusAndShowKeyboard()
                    !registerCheckBoxTcAgreement.isChecked -> registerCheckBoxTcAgreement.requestFocus()
                }
            }
        }
    }

    private fun isValidated(): Boolean {
        val isFullNameValidated = validateFullName()
        val isEmailValidated = validateEmail()
        val isPasswordValidated = validatePassword()
        val isAgreementValidated = validateAgreement()

        return isFullNameValidated && isEmailValidated && isPasswordValidated && isAgreementValidated
    }

    private fun validateFullName(): Boolean {
        return if (binding.registerInputFullName.text.isNullOrBlank()) {
            binding.registerTextInputLayoutFullName.error =
                resources.getString(R.string.full_name_is_required)
            false
        } else {
            binding.registerTextInputLayoutFullName.isErrorEnabled = false
            true
        }
    }

    private fun validateEmail(): Boolean {
        val email = binding.registerInputEmailAddress.text.toString()
        return when {
            email.isBlank() -> {
                binding.registerTextInputLayoutEmail.error =
                    resources.getString(R.string.email_is_required)
                false
            }

            !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                binding.registerTextInputLayoutEmail.error =
                    resources.getString(R.string.invalid_email_format)
                false
            }

            else -> {
                binding.registerTextInputLayoutEmail.isErrorEnabled = false
                // Do not clear focus here, it interrupts typing flow
                true
            }
        }
    }

    private fun validatePassword(): Boolean {
        val password = binding.registerInputPassword.text.toString()
        return when {
            password.isBlank() -> {
                binding.registerTextInputLayoutPassword.error =
                    resources.getString(R.string.password_is_required)
                false
            }

            password.length < 6 -> {
                binding.registerTextInputLayoutPassword.error =
                    resources.getString(R.string.password_must_be_at_least_6_characters_long)
                false
            }

            else -> {
                binding.registerTextInputLayoutPassword.isErrorEnabled = false
                true
            }
        }
    }

    private fun validateAgreement(): Boolean {
        // Suppose your CheckBox is `binding.registerCheckboxAgreement`
        val cb = binding.registerCheckBoxTcAgreement

        return if (!cb.isChecked) {
            // Show error on the CheckBox
            cb.error = resources.getString(R.string.terms_must_be_accepted)
            // Optionally request focus so the user sees it
            // cb.requestFocus()
            false
        } else {
            // Clear any previous error
            cb.error = null
            true
        }
    }
}
