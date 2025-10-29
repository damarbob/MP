package id.monpres.app.ui.profile

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.bumptech.glide.Glide
import com.google.android.material.transition.MaterialSharedAxis
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.mapbox.geojson.Point
import dagger.hilt.android.AndroidEntryPoint
import id.monpres.app.MapsActivity
import id.monpres.app.R
import id.monpres.app.databinding.FragmentEditProfileBinding
import id.monpres.app.enums.UserRole
import id.monpres.app.model.MapsActivityExtraData
import id.monpres.app.model.MontirPresisiUser
import id.monpres.app.repository.UserRepository
import id.monpres.app.usecase.GetColorFromAttrUseCase
import id.monpres.app.utils.markRequiredInRed
import javax.inject.Inject

@AndroidEntryPoint
class ProfileFragment : Fragment() {

    companion object {
        private val TAG = ProfileFragment::class.java.simpleName
        fun newInstance() = ProfileFragment()
    }

    /* Dependencies */
    @Inject
    lateinit var userRepository: UserRepository
    private val viewModel: ProfileViewModel by viewModels()
    private val auth = Firebase.auth
    private var user: FirebaseUser? = null
    private var userProfile: MontirPresisiUser? = null

    /* Use cases */
    private val getColorFromAttrUseCase = GetColorFromAttrUseCase()

    /* Location */
    protected var selectedPrimaryLocationPoint: Point? = null

    /* UI */
    private lateinit var binding: FragmentEditProfileBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        user = auth.currentUser
        userProfile = user?.uid?.let { userRepository.getRecordByUserId(it) }
        selectedPrimaryLocationPoint =
            userProfile?.locationLng?.toDouble()?.let {
                userProfile?.locationLat?.toDouble()?.let { latitude ->
                    Point.fromLngLat(it, latitude)
                }
            }
        Log.d(TAG, "Selected primary location point: $selectedPrimaryLocationPoint")

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
        binding = FragmentEditProfileBinding.inflate(inflater, container, false)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, windowInsets ->
            val insets =
                windowInsets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(insets.left, 0, insets.right, insets.bottom)
            windowInsets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.editProfileNestedScrollView) { v, windowInsets ->
            val insets =
                windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(insets.left, 0, insets.right, insets.bottom)
            WindowInsetsCompat.CONSUMED
        }

        setupUI()

        /* Observers */
        // Location observer
        viewModel.selectedPrimaryLocationPoint.observe(viewLifecycleOwner) { point ->
            point?.let {
                selectedPrimaryLocationPoint = it
                binding.editProfileButtonSelectPrimaryLocationButton.setText(getString(R.string.re_select_a_location))
            }
            if (point == null) return@observe
        }
        viewModel.updateProfileResult.observe(viewLifecycleOwner) { result ->
            // Hide loading indicator
            binding.editProfileProgressIndicator.visibility = View.GONE


            result?.onSuccess {
                // Enable save button
                binding.editProfileButton.isEnabled = true

                Toast.makeText(
                    requireContext(),
                    getString(R.string.profile_updated_successfully),
                    Toast.LENGTH_SHORT
                ).show()
            }?.onFailure { exception ->
                // Enable save button
                binding.editProfileButton.isEnabled = true

                Toast.makeText(
                    requireContext(),
                    exception.message,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        /* Listeners */
        binding.editProfileButton.setOnClickListener {
            // show loading indicator
            binding.editProfileProgressIndicator.visibility = View.VISIBLE

            // Make button disabled
            it.isEnabled = false

            viewModel.updateProfile(
                binding.editProfileInputEditFullName.text.toString(),
                binding.editProfileInputEmailAddress.text.toString(),
                binding.editProfileInputWhatsApp.text.toString(),
                !binding.editProfileCheckBoxHoliday.isChecked,
                selectedPrimaryLocationPoint,
                binding.editProfileInputEditAddress.text.toString()
            )

        }
        binding.editProfileButtonSelectPrimaryLocationButton.setOnClickListener {
            openMap()
        }

        return binding.root
    }

    private val pickLocationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { data ->
                val selectedLocation =
                    data.getStringExtra(MapsActivityExtraData.SELECTED_LOCATION) ?: ""
                val userLocation = data.getStringExtra(MapsActivityExtraData.USER_LOCATION) ?: ""
                Log.d(TAG, "User's location $userLocation")
                Log.d(TAG, "Selected location $selectedLocation")
                viewModel.setSelectedPrimaryLocationPoint(Point.fromJson(selectedLocation))
            }
        }
    }

    protected fun openMap() {
        val points = arrayListOf(selectedPrimaryLocationPoint?.toJson())
        Log.d(
            TAG,
            "Points: $points from ${arrayListOf(selectedPrimaryLocationPoint)} from $selectedPrimaryLocationPoint"
        )

        val intent = Intent(requireContext(), MapsActivity::class.java).apply {
            putExtra(MapsActivityExtraData.EXTRA_PICK_MODE, true)
            putStringArrayListExtra(
                "points",
                points
            )
        }
        pickLocationLauncher.launch(intent)
    }

    private fun setupUI() {
        // Hide unfinished features
        binding.editProfileTextInputLayoutEmail.visibility = View.GONE

        // Show for specific roles
        binding.editProfileCheckBoxHoliday.visibility =
            if (userProfile?.role === UserRole.PARTNER) View.VISIBLE else View.GONE

        // Fill input fields
        binding.editProfileInputEditFullName.setText(user?.displayName)
        binding.editProfileInputEmailAddress.setText(user?.email)
        binding.editProfileInputWhatsApp.setText(userProfile?.phoneNumber)
        binding.editProfileCheckBoxHoliday.isChecked =
            if (userProfile?.role === UserRole.PARTNER)
            // If partner and inactive, check
                userProfile?.active == false
            else
            // else, always uncheck
                false
        binding.editProfileInputEditAddress.setText(userProfile?.address)

        if (userProfile?.locationLat != null && userProfile?.locationLng != null) {
            binding.editProfileButtonSelectPrimaryLocationButton.setText(getString(R.string.re_select_a_location))
        }

        // Hide loading indicator
        binding.editProfileProgressIndicator.visibility = View.GONE

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

        // Set form marks
        binding.apply {
            editProfileInputLayoutFullName.markRequiredInRed()

            if (userProfile?.role == UserRole.CUSTOMER) {
                editProfileTextInputLayoutWhatsApp.markRequiredInRed()
            } else if (userProfile?.role == UserRole.PARTNER) {
                editProfileButtonSelectPrimaryLocationButton.markRequiredInRed()
            }
        }
    }
}