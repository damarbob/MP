package id.monpres.app.ui.partnerselection

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import id.monpres.app.databinding.FragmentPartnerSelectionBinding
import id.monpres.app.ui.adapter.PartnerAdapter
import id.monpres.app.ui.itemdecoration.SpacingItemDecoration
import id.monpres.app.usecase.GetPartnersUseCase
import javax.inject.Inject

@AndroidEntryPoint
class PartnerSelectionFragment : Fragment() {

    companion object {
        private val TAG = PartnerSelectionFragment::class.java.simpleName
        fun newInstance() = PartnerSelectionFragment()

        const val REQUEST_KEY_PARTNER_SELECTION = "partnerSelectionRequestKey"
        const val KEY_SELECTED_USER_ID = "selectedPartnerUserId"

    }

    private val viewModel: PartnerSelectionViewModel by viewModels()

    /* Use cases */
    @Inject
    lateinit var getPartnersUseCase: GetPartnersUseCase

    /* UI */
    private lateinit var binding: FragmentPartnerSelectionBinding
    private lateinit var partnerAdapter: PartnerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // TODO: Use the ViewModel
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentPartnerSelectionBinding.inflate(inflater, container, false)

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
                partnerAdapter.submitList(it)
            }
            result.onFailure {
                Log.e(TAG, it.message, it)
                Toast.makeText(requireContext(), it.message, Toast.LENGTH_SHORT).show()
            }
        }

        return binding.root
    }
}