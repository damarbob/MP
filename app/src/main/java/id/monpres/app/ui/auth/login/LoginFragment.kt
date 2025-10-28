package id.monpres.app.ui.auth.login

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.transition.MaterialSharedAxis
import id.monpres.app.LoginActivity
import id.monpres.app.MainActivity
import id.monpres.app.R
import id.monpres.app.databinding.FragmentLoginBinding
import id.monpres.app.ui.insets.InsetsWithKeyboardCallback

class LoginFragment : Fragment() {

    private lateinit var binding: FragmentLoginBinding

    private val viewModel: LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set the transition for this fragment
        enterTransition = MaterialSharedAxis(MaterialSharedAxis.Z, /* forward= */ true)
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.Z, /* forward= */ false)
        exitTransition = MaterialSharedAxis(MaterialSharedAxis.Z, /* forward= */ true)
        reenterTransition = MaterialSharedAxis(MaterialSharedAxis.Z, /* forward= */ false)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        binding = FragmentLoginBinding.inflate(layoutInflater, container, false)

        // Set insets with keyboard
        val insetsWithKeyboardCallback =
            InsetsWithKeyboardCallback(requireActivity().window, 0, null)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root, insetsWithKeyboardCallback)

        /* Set up UI */
        binding.loginLayoutForm.visibility = View.GONE

        /* Observers */
        // Auth result
        viewModel.authResult.observe(viewLifecycleOwner) { result ->
            result?.onSuccess {
                // Navigate to the next screen or update UI
                Toast.makeText(
                    requireContext(),
                    getString(R.string.sign_in_successful),
                    Toast.LENGTH_SHORT
                ).show()

                val intent = Intent(activity, MainActivity::class.java)
                startActivity(intent)
                activity?.finish()  // Finish the current activity so the user can't navigate back to the login screen

            }?.onFailure { exception ->
                // Show error message
                Toast.makeText(requireContext(), exception.localizedMessage, Toast.LENGTH_LONG).show()
            }
        }
        // Form Layout State
        viewModel.loginFormVisibilityState.observe(viewLifecycleOwner) {
            TransitionManager.beginDelayedTransition(binding.root, AutoTransition())
            val form = binding.loginLayoutForm
            form.visibility = if (it) View.VISIBLE else View.GONE
            binding.loginEmailButton.visibility = if (it) View.GONE else View.VISIBLE
        }
        // Loading indicator visibility
        viewModel.progressVisibility.observe(viewLifecycleOwner) { isVisible ->
            binding.loginProgressIndicatorLoading.visibility = if (isVisible) View.VISIBLE else View.GONE
        }

        /* Listeners */
        binding.loginTextForgotPassword.setOnClickListener {

            val email = binding.loginInputEmailAddress.text.toString()

            if (validateEmail()) {
                // Show confirmation dialog
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(getString(R.string.forgot_password))
                    .setMessage(getString(R.string.continue_please_make_sure))
                    .setPositiveButton(getString(R.string.send)) { dialog, which ->
                        viewModel.sendPasswordResetEmail(email) { success, errorMessage ->
                            if (success) {
                                Toast.makeText(requireContext(), getString(R.string.an_email_with_a_password_reset_link), Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show()
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
            if (isValidated()) {

                val email = binding.loginInputEmailAddress.text.toString()
                val password = binding.loginInputPassword.text.toString()

                viewModel.loginWithEmailPassword(email, password)
            } else {
                binding.loginInputEmailAddress.doAfterTextChanged { validateEmail() }
                binding.loginInputPassword.doAfterTextChanged { validatePassword() }
            }
        }
        binding.loginEmailButton.setOnClickListener {
            viewModel.toggleFormLayoutState()
        }
        binding.loginGoogleButton.setOnClickListener {
            (activity as LoginActivity).signIn()
        }
        binding.loginSignUpText.setOnClickListener {
            findNavController().navigate(R.id.action_loginFragment_to_registerFragment)
        }

        return binding.root
    }

    private fun isValidated(): Boolean {
        val isEmailValid = validateEmail()
        val isPasswordValid = validatePassword()
        return isEmailValid && isPasswordValid
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