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
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.material.transition.MaterialSharedAxis
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.mapbox.geojson.Point
import dagger.hilt.android.AndroidEntryPoint
import id.monpres.app.MapsActivity
import id.monpres.app.R
import id.monpres.app.databinding.FragmentProfileEditBinding
import id.monpres.app.model.MapsActivityExtraData
import id.monpres.app.model.MontirPresisiUser
import id.monpres.app.repository.UserRepository
import id.monpres.app.usecase.GetColorFromAttrUseCase
import kotlinx.coroutines.launch
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
    private lateinit var binding: FragmentProfileEditBinding

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
        binding = FragmentProfileEditBinding.inflate(inflater, container, false)

        setupUI()

        /* Observers */
        // Location observer
        viewModel.selectedPrimaryLocationPoint.observe(viewLifecycleOwner) { point ->
            point?.let {  selectedPrimaryLocationPoint = it }
            if (point == null) return@observe
        }
        viewModel.updateProfileResult.observe(viewLifecycleOwner) { result ->
            result?.onSuccess {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.profile_updated_successfully),
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
            lifecycleScope.launch {
                viewModel.updateProfileNew(
                    binding.editProfileInputEditFullName.text.toString(),
                    binding.editProfileInputEmailAddress.text.toString(),
                    binding.editProfileInputWhatsApp.text.toString(),
                    selectedPrimaryLocationPoint
                )
            }
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
        Log.d(TAG, "Points: $points from ${arrayListOf(selectedPrimaryLocationPoint)} from $selectedPrimaryLocationPoint")

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

        // Fill input fields
        binding.editProfileInputEditFullName.setText(user?.displayName)
        binding.editProfileInputEmailAddress.setText(user?.email)
        binding.editProfileInputWhatsApp.setText(userProfile?.phoneNumber)

        if (userProfile?.locationLat != null && userProfile?.locationLng != null) {
            binding.editProfileButtonSelectPrimaryLocationButton.setText(getString(R.string.re_select_a_location))
        }

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