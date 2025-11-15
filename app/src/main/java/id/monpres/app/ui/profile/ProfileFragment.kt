package id.monpres.app.ui.profile

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.telephony.PhoneNumberFormattingTextWatcher
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateMarginsRelative
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.transition.MaterialSharedAxis
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.mapbox.geojson.Point
import dagger.hilt.android.AndroidEntryPoint
import dev.androidbroadcast.vbpd.viewBinding
import id.monpres.app.MainApplication
import id.monpres.app.MapsActivity
import id.monpres.app.R
import id.monpres.app.databinding.FragmentEditProfileBinding
import id.monpres.app.enums.UserRole
import id.monpres.app.model.MapsActivityExtraData
import id.monpres.app.model.MontirPresisiUser
import id.monpres.app.repository.UserRepository
import id.monpres.app.usecase.GetColorFromAttrUseCase
import id.monpres.app.utils.dpToPx
import id.monpres.app.utils.markRequiredInRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class ProfileFragment : Fragment(R.layout.fragment_edit_profile) {

    companion object {
        private val TAG = ProfileFragment::class.java.simpleName
        fun newInstance() = ProfileFragment()
    }

    /* Dependencies */
    @Inject
    lateinit var userRepository: UserRepository

    /* Properties */
    private val viewModel: ProfileViewModel by viewModels()
    private val auth = Firebase.auth
    private var user: FirebaseUser? = null
    private var userProfile: MontirPresisiUser? = null

    /* Use cases */
    private val getColorFromAttrUseCase = GetColorFromAttrUseCase()

    /* Location */
    private var selectedPrimaryLocationPoint: Point? = null

    /* UI */
    private val binding by viewBinding(FragmentEditProfileBinding::bind)

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

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Set the transition for this fragment
        enterTransition = MaterialSharedAxis(MaterialSharedAxis.X, /* forward= */ true)
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.X, /* forward= */ false)
        exitTransition = MaterialSharedAxis(MaterialSharedAxis.X, /* forward= */ true)
        reenterTransition = MaterialSharedAxis(MaterialSharedAxis.X, /* forward= */ false)

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
        ViewCompat.setOnApplyWindowInsetsListener(binding.editProfileButton) { v, windowInsets ->
            val insets =
                windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            (v.layoutParams as ViewGroup.MarginLayoutParams).updateMarginsRelative(
                insets.left + 16.dpToPx(requireActivity()),
                0,
                insets.right + 16.dpToPx(requireActivity()),
                insets.bottom + 16.dpToPx(requireActivity())
            )
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

            // Get input values
            val whatsAppNumber = binding.editProfileInputWhatsApp.text.toString()
            val fullName = binding.editProfileInputEditFullName.text.toString()
            val emailAddress = binding.editProfileInputEmailAddress.text.toString()
            val active = !binding.editProfileCheckBoxHoliday.isChecked
            val location = selectedPrimaryLocationPoint
            val address = binding.editProfileInputEditAddress.text.toString()
            val instagramId = binding.editProfileInputEditInstagramId.text.toString()
            val facebookId = binding.editProfileInputEditFacebookId.text.toString()

            // Validate inputs (only necessary ones)
            if (!validateWhatsAppNumber()) {
                return@setOnClickListener
            }

            // Show loading indicator
            binding.editProfileProgressIndicator.visibility = View.VISIBLE

            // Make button disabled
            it.isEnabled = false

            // Use the international format for the WhatsApp number
            val phoneUtil = PhoneNumberUtil.getInstance()
            val formattedWhatsApp = phoneUtil.format(
                phoneUtil.parse(whatsAppNumber, MainApplication.APP_REGION),
                PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL
            )
            binding.editProfileInputWhatsApp.setText(formattedWhatsApp) // Update the input field to show formatted number

            // Update profile
            viewModel.updateProfile(
                fullName,
                emailAddress,
                formattedWhatsApp,
                active,
                location,
                address,
                instagramId,
                facebookId,
            )
        }
        binding.editProfileButtonSelectPrimaryLocationButton.setOnClickListener {
            openMap()
        }
        binding.editProfileCheckBoxHoliday.setOnCheckedChangeListener { _, isChecked ->
            // Show alert dialog if checked and user is partner
            if (userProfile?.role == UserRole.PARTNER) {
                if (isChecked) {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle(getString(R.string.i_am_on_holiday))
                        .setMessage(getString(R.string.once_activated_customers_will_no_longer_see_you))
                        .setPositiveButton(getString(R.string.okay)) { dialog, _ ->
                            dialog.dismiss()
                        }
                        .show()
                }
            }
        }
    }

    private fun validateWhatsAppNumber(): Boolean {
        val whatsAppNumber = binding.editProfileInputWhatsApp.text.toString()
        val phoneUtil = PhoneNumberUtil.getInstance()

        try {
            val numberProto = phoneUtil.parse(whatsAppNumber, MainApplication.APP_REGION)
            if (!phoneUtil.isValidNumber(numberProto)) {
                binding.editProfileTextInputLayoutWhatsApp.error =
                    getString(R.string.please_enter_a_valid_phone_number)
                return false
            }
            // Clear previous error if any
            binding.editProfileTextInputLayoutWhatsApp.error = null
        } catch (e: NumberParseException) {
            binding.editProfileTextInputLayoutWhatsApp.error =
                getString(R.string.please_enter_a_valid_phone_number)
            Log.w(TAG, "NumberParseException was thrown for number: $whatsAppNumber", e)
            return false
        }

        return true
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

    private fun openMap() {
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
        binding.editProfileLayoutSocialMedia.visibility =
            if (userProfile?.role !== UserRole.CUSTOMER) View.GONE else View.VISIBLE

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
        binding.editProfileInputEditInstagramId.setText(userProfile?.instagramId)
        binding.editProfileInputEditFacebookId.setText(userProfile?.facebookId)

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

        /* Listeners */

        // Instagram ID input end icon listener
        binding.editProfileInputLayoutInstagramId.setEndIconOnClickListener {
            val instagramId = binding.editProfileInputEditInstagramId.text.toString().trim()
            if (instagramId.isBlank()) return@setEndIconOnClickListener

            // Open instagram in browser
            val browserIntent =
                Intent(
                    Intent.ACTION_VIEW,
                    "https://instagram.com/$instagramId".toUri()
                )
            startActivity(browserIntent)
        }

        // Facebook ID input end icon listener
        binding.editProfileInputLayoutFacebookId.setEndIconOnClickListener {
            val facebookId = binding.editProfileInputEditFacebookId.text.toString().trim()
            if (facebookId.isBlank()) return@setEndIconOnClickListener

            // Open facebook in browser
            val browserIntent =
                Intent(
                    Intent.ACTION_VIEW,
                    "https://facebook.com/$facebookId".toUri()
                )
            startActivity(browserIntent)
        }

        // As-you-type formatter for WhatsApp number
        lifecycleScope.launch(Dispatchers.IO) {
            // Create util and watcher on IO thread
            val watcher = PhoneNumberFormattingTextWatcher(MainApplication.APP_REGION)
            val phoneUtil = PhoneNumberUtil.getInstance()

            // Get the current text (must be done on Main thread)
            val currentNumber = withContext(Dispatchers.Main) {
                binding.editProfileInputWhatsApp.text.toString()
            }

            var initialFormattedNumber: String? = null
            if (currentNumber.isNotBlank()) {
                try {
                    // Parse and format the existing number on IO thread
                    val numberProto = phoneUtil.parse(currentNumber, MainApplication.APP_REGION)
                    initialFormattedNumber = phoneUtil.format(
                        numberProto,
                        PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL
                    )
                } catch (e: NumberParseException) {
                    Log.w(
                        TAG,
                        "Initial number $currentNumber is not parseable. ${e.localizedMessage}"
                    )
                    // Number is invalid, so we'll just leave it as-is
                }
            }

            // Switch back to Main thread to update UI
            withContext(Dispatchers.Main) {
                // Set the formatted text (if we have it)
                // This runs *before* the watcher is added, so no loops.
                if (initialFormattedNumber != null) {
                    binding.editProfileInputWhatsApp.setText(initialFormattedNumber)
                }

                // NOW add the watcher to format future user typing
                binding.editProfileInputWhatsApp.addTextChangedListener(watcher)
            }
        }
    }
}