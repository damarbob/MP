package id.monpres.app.ui.adminnewusers

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import id.monpres.app.databinding.FragmentAdminNewUsersBinding
import id.monpres.app.model.MontirPresisiUser
import id.monpres.app.ui.adapter.UserAdapter
import id.monpres.app.ui.adminnewuser.AdminNewUserFragment
import id.monpres.app.ui.itemdecoration.SpacingItemDecoration
import id.monpres.app.usecase.GetNewUsersUseCase
import javax.inject.Inject

@AndroidEntryPoint
class AdminNewUsersFragment : Fragment() {

    companion object {
        fun newInstance() = AdminNewUsersFragment()
    }

    private val viewModel: AdminNewUsersViewModel by viewModels()

    /* Dependencies */
    @Inject
    lateinit var getNewUsersUseCase: GetNewUsersUseCase

    /* UI */
    private lateinit var binding: FragmentAdminNewUsersBinding
    private lateinit var adapter: UserAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // TODO: Use the ViewModel
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentAdminNewUsersBinding.inflate(inflater, container, false)

        adapter = UserAdapter(emptyList())
        binding.fragmentAdminNewUsersRecyclerViewNewUsers.adapter = adapter

        getNewUsersUseCase { result ->
            result.onSuccess { users ->
                binding.fragmentAdminNewUsersRecyclerViewNewUsers.apply {
                    addItemDecoration(SpacingItemDecoration(2))
                    layoutManager = LinearLayoutManager(requireContext())
                }
                adapter = UserAdapter(users)
                binding.fragmentAdminNewUsersRecyclerViewNewUsers.adapter = adapter

                adapter.setOnItemClickListener(object : UserAdapter.OnItemClickListener {
                    override fun onMenuClicked(user: MontirPresisiUser?) {
                        val dialog = user?.let { AdminNewUserFragment.newInstance(it) }
                        dialog?.show(parentFragmentManager, AdminNewUserFragment.TAG)
                    }
                })
            }.onFailure { exception ->
                // Handle error
            }
        }

        return binding.root
    }
}