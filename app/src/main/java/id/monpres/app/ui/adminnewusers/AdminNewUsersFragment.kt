package id.monpres.app.ui.adminnewusers

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import id.monpres.app.R
import id.monpres.app.databinding.FragmentAdminNewUsersBinding
import id.monpres.app.enums.UserVerificationStatus
import id.monpres.app.model.MontirPresisiUser
import id.monpres.app.state.UiState
import id.monpres.app.ui.adapter.UserAdapter
import id.monpres.app.ui.adminnewuser.AdminNewUserFragment
import id.monpres.app.ui.itemdecoration.SpacingItemDecoration
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AdminNewUsersFragment : Fragment() {

    companion object {
        fun newInstance() = AdminNewUsersFragment()
        private val TAG = AdminNewUsersFragment::class.simpleName
    }

    private val viewModel: AdminNewUsersViewModel by viewModels()
    private var _binding: FragmentAdminNewUsersBinding? = null
    private val binding get() = _binding!!

    private val userAdapter: UserAdapter by lazy {
        UserAdapter().apply {
            setOnItemClickListener(object : UserAdapter.OnItemClickListener {
                override fun onMenuClicked(user: MontirPresisiUser?) {
                    user?.let { validUser ->
                        AdminNewUserFragment.newInstance(validUser)
                            .show(parentFragmentManager, AdminNewUserFragment.TAG)
                    }
                }
            })
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminNewUsersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupInputs()
        setupScrollListener()
        observeViewModel()
    }

    private fun setupInputs() {
        // 1. Search Listener (Trigger ONLY on Enter)
        binding.fragmentAdminNewUsersEditTextSearch.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = v.text.toString().trim()
                viewModel.setSearchQuery(query)

                // Hide keyboard for better UX
                hideKeyboard()
                return@setOnEditorActionListener true
            }
            false
        }

        // Optional: Handle the "Clear" (X) button explicitly if you want it
        // to reset the list immediately without pressing Enter.
        binding.fragmentAdminNewUsersInputLayoutSearch.setEndIconOnClickListener {
            binding.fragmentAdminNewUsersEditTextSearch.text?.clear()
            viewModel.setSearchQuery("") // Reset list immediately
            // If you strictly want NO requests until Enter, remove the line above.
        }

        // Chips
        binding.fragmentAdminNewUsersChipGroupFilter.setOnCheckedStateChangeListener { _, checkedIds ->
            val status = when (checkedIds.firstOrNull()) {
                R.id.chipPending -> UserVerificationStatus.PENDING
                R.id.chipVerified -> UserVerificationStatus.VERIFIED
                R.id.chipRejected -> UserVerificationStatus.REJECTED
                R.id.chipAll -> null // Sends NULL to ViewModel, triggering "All" logic
                else -> UserVerificationStatus.PENDING
            }
            viewModel.setFilter(status)
        }
    }

    private fun setupRecyclerView() {
        binding.fragmentAdminNewUsersRecyclerViewNewUsers.apply {
            adapter = userAdapter
            layoutManager = LinearLayoutManager(requireContext())
            if (itemDecorationCount == 0) {
                addItemDecoration(SpacingItemDecoration(8)) // Gap of 8dp
            }
        }
    }

    private fun setupScrollListener() {
        binding.fragmentAdminNewUsersNestedScrollView.setOnScrollChangeListener(
            NestedScrollView.OnScrollChangeListener { v, _, scrollY, _, _ ->
                // Calculate if we have scrolled to the bottom
                val diff = (v.getChildAt(0).measuredHeight - v.measuredHeight) - scrollY

                // If diff is 0 (or very small), we are at the bottom
                if (diff <= 0) {
                    viewModel.loadMore()
                }
            }
        )
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {

                // Observe Main Data State
                launch {
                    viewModel.uiState.collect { state ->
                        binding.apply {
                            // Reset visibility
                            fragmentAdminNewUsersLinearProgressIndicator.visibility = View.GONE
                            fragmentAdminNewUsersRecyclerViewNewUsers.visibility = View.GONE
                            fragmentAdminNewUsersTextViewError.visibility = View.GONE
                            fragmentAdminNewUsersLinearLayoutInfo.visibility = View.GONE

                            when (state) {
                                is UiState.Loading -> {
                                    fragmentAdminNewUsersLinearProgressIndicator.visibility =
                                        View.VISIBLE
                                    // Optional: Keep list visible while reloading if you prefer
                                }

                                is UiState.Success -> {
                                    fragmentAdminNewUsersRecyclerViewNewUsers.visibility =
                                        View.VISIBLE
                                    userAdapter.submitList(state.data)
                                }

                                is UiState.Error -> {
                                    fragmentAdminNewUsersTextViewError.visibility = View.VISIBLE
                                    fragmentAdminNewUsersTextViewError.text = state.message
                                }

                                is UiState.Empty -> {
                                    fragmentAdminNewUsersLinearLayoutInfo.visibility = View.VISIBLE
                                    // Update empty text based on whether search is active
                                    val isSearching =
                                        !binding.fragmentAdminNewUsersEditTextSearch.text.isNullOrBlank()
                                    fragmentAdminNewUsersTextViewInfo.text = if (isSearching)
                                        getString(R.string.no_new_users_found)
                                    else
                                        getString(R.string.no_new_users_found)
                                }
                            }
                        }
                    }
                }

                // Observe Load More Loading State
                launch {
                    viewModel.isLoadingMore.collect { isLoading ->
                        binding.fragmentAdminNewUsersProgressBarLoadMore.visibility =
                            if (isLoading) View.VISIBLE else View.GONE
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun hideKeyboard() {
        val imm =
            requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
        binding.fragmentAdminNewUsersEditTextSearch.clearFocus()
    }
}