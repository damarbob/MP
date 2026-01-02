package id.monpres.app.ui.auth.login

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
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
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.transition.MaterialSharedAxis
import dev.androidbroadcast.vbpd.viewBinding
import id.monpres.app.AuthViewModel
import id.monpres.app.LoginActivity
import id.monpres.app.R
import id.monpres.app.databinding.FragmentLoginBinding
import id.monpres.app.utils.hideKeyboard
import id.monpres.app.utils.requestFocusAndShowKeyboard
import kotlinx.coroutines.launch

class LoginFragment : Fragment(R.layout.fragment_login) {

    private val binding by viewBinding(FragmentLoginBinding::bind)

    private val viewModel: LoginViewModel by viewModels()
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
        ViewCompat.setOnApplyWindowInsetsListener(binding.loginNestedScrollView) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(insets.left, 0, insets.right, insets.bottom)
            WindowInsetsCompat.CONSUMED
        }

        /* Set up UI */
        binding.loginLayoutForm.visibility = View.GONE

        setupObservers()
        setupListeners()
    }

    private fun setupObservers() {
        // Form Layout State
        viewModel.loginFormVisibilityState.observe(viewLifecycleOwner) {
            TransitionManager.beginDelayedTransition(binding.root, AutoTransition().apply {
                duration = 150L
            })
            binding.loginEmailButton.visibility = if (it) View.GONE else View.VISIBLE
            binding.loginLayoutForm.visibility = if (it) View.VISIBLE else View.GONE
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                authViewModel.loginState.collect { loginState ->
                    when (loginState) {
                        is AuthViewModel.LoginState.Loading -> {
                            binding.loginProgressIndicatorLoading.visibility = View.VISIBLE
                            binding.loginButton.isEnabled = false
                        }

                        is AuthViewModel.LoginState.Success -> {
                            binding.loginProgressIndicatorLoading.visibility = View.GONE
                            binding.loginButton.isEnabled = true
                            // Navigation is handled by AuthState in Activity
                        }

                        is AuthViewModel.LoginState.Error -> {
                            binding.loginProgressIndicatorLoading.visibility = View.GONE
                            binding.loginButton.isEnabled = true
//                            Toast.makeText(requireContext(), loginState.message, Toast.LENGTH_SHORT)
//                                .show()
                        }

                        else -> {
                            binding.loginProgressIndicatorLoading.visibility = View.GONE
                            binding.loginButton.isEnabled = true
                        }
                    }
                }
            }
        }
    }

    private fun setupListeners() {
        /* Listeners */
        binding.loginTextForgotPassword.setOnClickListener {

            val email = binding.loginInputEmailAddress.text.toString()

            if (validateEmail()) {
                it.hideKeyboard()
                // Show confirmation dialog
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(getString(R.string.forgot_password))
                    .setMessage(getString(R.string.continue_please_make_sure))
                    .setPositiveButton(getString(R.string.send)) { dialog, which ->
                        authViewModel.sendPasswordResetEmail(email) { success, errorMessage ->
                            if (success) {
                                Toast.makeText(
                                    requireContext(),
                                    getString(R.string.an_email_with_a_password_reset_link),
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT)
                                    .show()
                            }
                        }
                    }
                    .setNeutralButton(getString(R.string.close)) { dialog, which ->
                        dialog.dismiss()
                    }
                    .show()
            }

        }
        binding.loginButton.setOnClickListener {
            login(it)
        }
        binding.loginInputPassword.setOnEditorActionListener { editText, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                login(editText)
                true
            } else false
        }
        binding.loginEmailButton.setOnClickListener {
            viewModel.toggleFormLayoutState()
        }
        binding.loginGoogleButton.setOnClickListener {
            (activity as LoginActivity).signInWithGoogle()
        }
        binding.loginSignUpText.setOnClickListener {
            findNavController().navigate(R.id.action_loginFragment_to_registerFragment)
        }

        setupHybridValidation()
    }

    private fun setupHybridValidation() {
        // 1. EMAIL SETUP
        binding.loginInputEmailAddress.setOnFocusChangeListener { _, hasFocus ->
            // Validate on Blur (when user leaves)
            if (!hasFocus) {
                validateEmail()
            }
        }

        binding.loginInputEmailAddress.doAfterTextChanged {
            // Clear on Change: Only validate input while typing IF there is already an error.
            // This makes the red text disappear the moment they fix it.
            if (binding.loginTextInputLayoutEmail.isErrorEnabled) {
                validateEmail()
            }
        }

        // 2. PASSWORD SETUP
        binding.loginInputPassword.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                validatePassword()
            }
        }

        binding.loginInputPassword.doAfterTextChanged {
            if (binding.loginTextInputLayoutPassword.isErrorEnabled) {
                validatePassword()
            }
        }
    }

    private fun login(view: View) {
        // Hide keyboard first
        view.hideKeyboard()

        if (isValidated()) {
            val email = binding.loginInputEmailAddress.text.toString()
            val password = binding.loginInputPassword.text.toString()

            authViewModel.loginWithEmailPassword(email, password)
        } else {
            with(binding) {
                when {
                    loginTextInputLayoutEmail.isErrorEnabled -> loginInputEmailAddress.requestFocusAndShowKeyboard()
                    loginTextInputLayoutPassword.isErrorEnabled -> loginInputPassword.requestFocusAndShowKeyboard()
                }
            }
        }
    }

    private fun isValidated(): Boolean {
        val isEmailValidated = validateEmail()
        val isPasswordValidated = validatePassword()

        return isEmailValidated && isPasswordValidated
    }

    private fun validateEmail(): Boolean {
        val email = binding.loginInputEmailAddress.text.toString()
        return when {
            email.isBlank() -> {
                binding.loginTextInputLayoutEmail.error =
                    resources.getString(R.string.email_is_required)
                false
            }

            !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                binding.loginTextInputLayoutEmail.error =
                    resources.getString(R.string.invalid_email_format)
                false
            }

            else -> {
                binding.loginTextInputLayoutEmail.isErrorEnabled = false
                true
            }
        }
    }

    private fun validatePassword(): Boolean {
        val password = binding.loginInputPassword.text.toString()
        return when {
            password.isBlank() -> {
                binding.loginTextInputLayoutPassword.error =
                    resources.getString(R.string.password_is_required)
                false
            }

            password.length < 6 -> {
                binding.loginTextInputLayoutPassword.error =
                    resources.getString(R.string.password_must_be_at_least_6_characters_long)
                false
            }

            else -> {
                binding.loginTextInputLayoutPassword.isErrorEnabled = false
                true
            }
        }
    }
}
