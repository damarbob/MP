package id.monpres.app.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.bumptech.glide.Glide
import com.google.android.material.transition.MaterialSharedAxis
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import id.monpres.app.databinding.FragmentProfileEditBinding
import id.monpres.app.usecase.GetColorFromAttrUseCase

class ProfileFragment : Fragment() {

    companion object {
        fun newInstance() = ProfileFragment()
    }

    /* Dependencies */
    private val viewModel: ProfileViewModel by viewModels()
    private val auth = Firebase.auth
    private var user: FirebaseUser? = null

    /* Use cases */
    private val getColorFromAttrUseCase = GetColorFromAttrUseCase()

    /* Views */
    private lateinit var binding: FragmentProfileEditBinding

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
        binding = FragmentProfileEditBinding.inflate(inflater, container, false)

        user = auth.currentUser

        setupUI()

        /* Observers */
        viewModel.updateProfileResult.observe(viewLifecycleOwner) { result ->
            result?.onSuccess {
                Toast.makeText(
                    requireContext(),
                    "Profile updated successfully",
                    Toast.LENGTH_SHORT
                ).show()
            }?.onFailure { exception ->
                Toast.makeText(
                    requireContext(),
                    exception.message,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        /* Listeners */
        binding.editProfileButton.setOnClickListener {
            viewModel.updateProfile(
                binding.editProfileInputEditFullName.text.toString(),
                binding.editProfileInputEmailAddress.text.toString()
            )
        }

        return binding.root
    }

    private fun setupUI() {
        // Hide unfinished features
        binding.editProfileTextInputLayoutEmail.visibility = View.GONE

        // Fill input fields
        binding.editProfileInputEditFullName.setText(user?.displayName)
        binding.editProfileInputEmailAddress.setText(user?.email)

        // Load initial avatar
        Glide
            .with(requireContext())
            .load(
                "https://ui-avatars.com/api/?size=512&name=${
                    user?.displayName?.replace(
                        " ",
                        "-"
                    )
                }&rounded=true&" +
                        "background=${
                            getColorFromAttrUseCase.getColorHex(
                                com.google.android.material.R.attr.colorPrimarySurface,
                                requireContext()
                            )
                        }&" +
                        "color=${
                            getColorFromAttrUseCase.getColorHex(
                                com.google.android.material.R.attr.colorOnPrimarySurface,
                                requireContext()
                            )
                        }&bold=true"
            )
            .into(binding.editProfileAvatar)
            .clearOnDetach()
    }
}