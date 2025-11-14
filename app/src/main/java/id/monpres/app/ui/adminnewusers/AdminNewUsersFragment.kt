package id.monpres.app.ui.adminnewusers

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import id.monpres.app.R
import id.monpres.app.databinding.FragmentAdminNewUsersBinding
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
    }

    private val viewModel: AdminNewUsersViewModel by viewModels()

    /* UI */
    private var _binding: FragmentAdminNewUsersBinding? = null
    private val binding get() = _binding!!

    // Use 'lazy' to create the adapter and its listener only ONCE.
    // This survives view recreation and avoids listener setup issues.
    private val userAdapter: UserAdapter by lazy {
        UserAdapter().apply {
            setOnItemClickListener(object : UserAdapter.OnItemClickListener {
                override fun onMenuClicked(user: MontirPresisiUser?) {
                    // Use 'let' for cleaner null-checking
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
        observeViewModel()
    }

    private fun setupRecyclerView() {
        binding.fragmentAdminNewUsersRecyclerViewNewUsers.apply {
            // 2. OPTIMIZATION: Use the single, lazy-initialized adapter
            adapter = userAdapter
            layoutManager = LinearLayoutManager(requireContext())

            // 3. OPTIMIZATION: Only add item decoration if it hasn't been added before.
            // This prevents stacking decorations on view re-creation.
            if (itemDecorationCount == 0) {
                addItemDecoration(SpacingItemDecoration(2))
            }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    // 4. OPTIMIZATION: Use 'binding.apply' for a much cleaner
                    // "hide all, show one" state management pattern.
                    binding.apply {
                        // Default state: Hide all mutually exclusive views
                        fragmentAdminNewUsersLinearProgressIndicator.visibility = View.GONE
                        fragmentAdminNewUsersRecyclerViewNewUsers.visibility = View.GONE
                        fragmentAdminNewUsersTextViewError.visibility = View.GONE
                        fragmentAdminNewUsersLinearLayoutInfo.visibility = View.GONE

                        // Now, show only the view for the current state
                        when (state) {
                            is UiState.Loading -> {
                                fragmentAdminNewUsersLinearProgressIndicator.visibility =
                                    View.VISIBLE
                            }

                            is UiState.Success -> {
                                fragmentAdminNewUsersRecyclerViewNewUsers.visibility = View.VISIBLE
                                // Submit the list to our single adapter instance
                                userAdapter.submitList(state.data)
                            }

                            is UiState.Error -> {
                                fragmentAdminNewUsersTextViewError.visibility = View.VISIBLE
                                fragmentAdminNewUsersTextViewError.text = state.message
                            }

                            is UiState.Empty -> {
                                fragmentAdminNewUsersLinearLayoutInfo.visibility = View.VISIBLE
                                fragmentAdminNewUsersTextViewInfo.text =
                                    getString(R.string.no_new_users_found)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Good practice: null out binding to prevent memory leaks
        _binding = null
    }
}