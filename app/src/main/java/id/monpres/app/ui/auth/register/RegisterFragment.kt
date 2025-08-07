package id.monpres.app.ui.auth.register

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import id.monpres.app.LoginActivity
import id.monpres.app.MainActivity
import id.monpres.app.R
import id.monpres.app.databinding.FragmentRegisterBinding
import id.monpres.app.ui.insets.InsetsWithKeyboardCallback


class RegisterFragment : Fragment() {

    private lateinit var binding: FragmentRegisterBinding

    private val viewModel: RegisterViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        binding = FragmentRegisterBinding.inflate(layoutInflater, container, false)

        // Set insets with keyboard
        val insetsWithKeyboardCallback =
            InsetsWithKeyboardCallback(requireActivity().window, 0, null)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root, insetsWithKeyboardCallback)

        /* Observers */
        // Auth result
        viewModel.authResult.observe(viewLifecycleOwner) { result ->
            result?.onSuccess {
                // Navigate to the next screen or update UI
                Toast.makeText(
                    requireContext(),
                    getString(R.string.sign_up_successful),
                    Toast.LENGTH_SHORT
                ).show()

                val intent = Intent(activity, MainActivity::class.java)
                startActivity(intent)
                activity?.finish()  // Finish the current activity so the user can't navigate back to the login screen

            }?.onFailure { exception ->
                // Show error message
                Toast.makeText(requireContext(), exception.message, Toast.LENGTH_SHORT).show()
            }
        }
        // Loading indicator visibility
        viewModel.progressVisibility.observe(viewLifecycleOwner) { isVisible ->
            binding.registerProgressIndicatorLoading.visibility = if (isVisible) View.VISIBLE else View.GONE
        }

        /* Listeners */
        binding.registerInputFullName.addTextChangedListener { validateFullName() }
        binding.registerInputEmailAddress.addTextChangedListener { validateEmail() }
        binding.registerInputPassword.addTextChangedListener { validatePassword() }
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
            if (validateFullName() && validateEmail() && validatePassword() && validateAgreement()) {

                val fullName = binding.registerInputFullName.text.toString()
                val email = binding.registerInputEmailAddress.text.toString()
                val password = binding.registerInputPassword.text.toString()
                val cb = binding.registerCheckBoxTcAgreement.isChecked

                viewModel.registerWithEmailPassword(fullName, email, password)
            }
        }
        binding.registerGoogleButton.setOnClickListener {
            (activity as LoginActivity).signIn()
        }
        binding.registerSignInText.setOnClickListener {
            findNavController().navigate(R.id.action_registerFragment_to_loginFragment)
        }

        return binding.root
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
            cb.requestFocus()
            false
        } else {
            // Clear any previous error
            cb.error = null
            true
        }
    }
}