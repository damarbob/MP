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
import id.monpres.app.databinding.FragmentAdminNewUsersBinding
import id.monpres.app.model.MontirPresisiUser
import id.monpres.app.state.UiState
import id.monpres.app.ui.adapter.UserAdapter
import id.monpres.app.ui.adminnewuser.AdminNewUserFragment
import id.monpres.app.ui.itemdecoration.SpacingItemDecoration
// No longer need to inject UseCase here
// import id.monpres.app.usecase.GetNewUsersUseCase
// import javax.inject.Inject
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
    private lateinit var adapter: UserAdapter // Assume this is a ListAdapter

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
        // Create the adapter ONCE
        adapter = UserAdapter()

        adapter.setOnItemClickListener(object : UserAdapter.OnItemClickListener {
            override fun onMenuClicked(user: MontirPresisiUser?) {
                val dialog = user?.let { AdminNewUserFragment.newInstance(it) }
                dialog?.show(parentFragmentManager, AdminNewUserFragment.TAG)
            }
        })

        binding.fragmentAdminNewUsersRecyclerViewNewUsers.apply {
            this.adapter = this@AdminNewUsersFragment.adapter
            layoutManager = LinearLayoutManager(requireContext())
            // Add decoration only once
            addItemDecoration(SpacingItemDecoration(2))
        }
    }

    private fun observeViewModel() {
        // Use viewLifecycleOwner.lifecycleScope to tie collection to the Fragment's view
        viewLifecycleOwner.lifecycleScope.launch {
            // repeatOnLifecycle ensures we only collect when the fragment is STARTED
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    // Handle the different states
                    when (state) {
                        is UiState.Loading -> {
                            // Show loading
                            binding.fragmentAdminNewUsersLinearProgressIndicator.visibility =
                                View.VISIBLE

                            // Hide RecyclerView and error message
                            binding.fragmentAdminNewUsersRecyclerViewNewUsers.visibility = View.GONE
                            binding.fragmentAdminNewUsersTextViewError.visibility = View.GONE
                        }

                        is UiState.Success -> {
                            // Hide loading, show RecyclerView
                            binding.fragmentAdminNewUsersLinearProgressIndicator.visibility =
                                View.GONE
                            binding.fragmentAdminNewUsersRecyclerViewNewUsers.visibility =
                                View.VISIBLE

                            // Submit the new list. The adapter handles the diffing.
                            adapter.submitList(state.data)
                        }

                        is UiState.Error -> {
                            // Hide loading and RecyclerView
                            binding.fragmentAdminNewUsersLinearProgressIndicator.visibility =
                                View.GONE
                            binding.fragmentAdminNewUsersRecyclerViewNewUsers.visibility = View.GONE

                            // Show error message
                            binding.fragmentAdminNewUsersTextViewError.visibility = View.VISIBLE
                            binding.fragmentAdminNewUsersTextViewError.text = state.message
                        }

                        is UiState.Empty -> {}
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Null out the binding to prevent memory leaks
        _binding = null
    }
}