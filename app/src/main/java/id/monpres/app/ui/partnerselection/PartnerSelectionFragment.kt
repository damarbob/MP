package id.monpres.app.ui.partnerselection

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.mapbox.geojson.Point
import dagger.hilt.android.AndroidEntryPoint
import dev.androidbroadcast.vbpd.viewBinding
import id.monpres.app.R
import id.monpres.app.databinding.FragmentPartnerSelectionBinding
import id.monpres.app.repository.PartnerRepository
import id.monpres.app.repository.UserRepository
import id.monpres.app.ui.adapter.PartnerAdapter
import id.monpres.app.ui.itemdecoration.SpacingItemDecoration
import id.monpres.app.usecase.GetPartnersUseCase
import javax.inject.Inject

@AndroidEntryPoint
class PartnerSelectionFragment : Fragment(R.layout.fragment_partner_selection) {

    companion object {
        private val TAG = PartnerSelectionFragment::class.java.simpleName
        fun newInstance() = PartnerSelectionFragment()

        const val REQUEST_KEY_PARTNER_SELECTION = "partnerSelectionRequestKey"
        const val KEY_SELECTED_USER_ID = "selectedPartnerUserId"
        const val KEY_SELECTED_LOCATION_POINT = "selectedLocationPoint"

    }

    private val viewModel: PartnerSelectionViewModel by viewModels()

    @Inject
    lateinit var userRepository: UserRepository

    @Inject
    lateinit var partnerRepository: PartnerRepository

    /* Use cases */
    @Inject
    lateinit var getPartnersUseCase: GetPartnersUseCase

    /* UI */
    private val binding by viewBinding(FragmentPartnerSelectionBinding::bind)
    private lateinit var partnerAdapter: PartnerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // TODO: Use the ViewModel
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ViewCompat.setOnApplyWindowInsetsListener(binding.quickServiceScrollView) { v, windowInsets ->
            val insets =
                windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(insets.left, 0, insets.right, insets.bottom)
            windowInsets
        }

        // Current user is mandatory
        val currentUser = userRepository.getCurrentUserRecord()

        if (currentUser == null) {
            Toast.makeText(
                requireContext(), getString(
                    R.string.x_is_required,
                    getString(R.string.user)
                ), Toast.LENGTH_SHORT
            ).show()
        }

        // Arguments
        val selectedLocationPointArg = arguments?.getString(KEY_SELECTED_LOCATION_POINT)
        val selectedLocationPoint = selectedLocationPointArg?.let { Point.fromJson(it) }

        // Set current user location. Use selected location if available, otherwise use current user location.
        partnerRepository.setCurrentUserLocation(
            selectedLocationPoint?.latitude() ?: (currentUser?.locationLat?.toDoubleOrNull()
                ?: 0.0),
            selectedLocationPoint?.longitude() ?: (currentUser?.locationLng?.toDoubleOrNull() ?: 0.0)
        )

        partnerAdapter = PartnerAdapter { partner ->
            // TODO: When the partner is clicked, navigate back and pass the selected partner.userId
            // ① package the selected ID
            setFragmentResult(
                REQUEST_KEY_PARTNER_SELECTION,
                bundleOf(KEY_SELECTED_USER_ID to partner.userId)
            )
            // ② pop this fragment off the back stack
            findNavController().popBackStack()
        }
        binding.partnerSelectionRecyclerView.apply {
            addItemDecoration(SpacingItemDecoration(2))
            adapter = partnerAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }

        getPartnersUseCase { result ->
            result.onSuccess {
                partnerAdapter.submitPartnersWithDistance(partnerRepository.getPartnersWithDistance())
            }
            result.onFailure {
                Log.e(TAG, it.message, it)
                Toast.makeText(requireContext(), it.message, Toast.LENGTH_SHORT).show()
            }
        }
    }
}