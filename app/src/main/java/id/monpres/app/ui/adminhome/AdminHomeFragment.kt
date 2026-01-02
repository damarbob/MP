package id.monpres.app.ui.adminhome

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import dev.androidbroadcast.vbpd.viewBinding
import id.monpres.app.R
import id.monpres.app.databinding.FragmentAdminHomeBinding
import id.monpres.app.model.Menu
import id.monpres.app.ui.adapter.MenuAdapter
import id.monpres.app.ui.itemdecoration.SpacingItemDecoration
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AdminHomeFragment : Fragment(R.layout.fragment_admin_home) {

    companion object {
        fun newInstance() = AdminHomeFragment()
    }

    private val viewModel: AdminHomeViewModel by viewModels()

    /* Dependencies */

    /* UI */
    private val binding by viewBinding(FragmentAdminHomeBinding::bind)

    private lateinit var menuAdapter: MenuAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // TODO: Use the ViewModel
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
    }

    private fun setupUI() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {

                // Update hello text
                viewModel.currentUser.collect {
                    binding.fragmentAdminHomeTextViewHello.setText(getString(R.string.hello_x, it?.displayName))
                }
            }
        }

        menuAdapter = MenuAdapter(listOf(
            Menu(
                id = "manage_new_users",
                title = getString(R.string.manage_new_users),
                subtitle = getString(R.string.verify_and_approve_new_user_registrations),
                iconRes = R.drawable.person_add_24px
            ),
            Menu(
                id = "manage_orders",
                title = getString(R.string.orders),
                subtitle = getString(R.string.manage_orders),
                iconRes = R.drawable.orders_24px
            ),
            // TODO: Enable these menus once implemented
//            Menu(
//                id = "manage_users",
//                title = "Manage Users",
//                subtitle = "Add, edit, or remove users",
//                iconRes = R.drawable.person_24px
//            ),
        ))

        binding.fragmentAdminHomeRecyclerViewMenu.apply {
            addItemDecoration(SpacingItemDecoration(2))
            adapter = menuAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }

        menuAdapter.setOnMenuClickListener(object : MenuAdapter.OnMenuClickListener {
            override fun onMenuClicked(menuId: String?) {
//                Toast.makeText(requireContext(), "Menu clicked: $menuId", Toast.LENGTH_SHORT).show()
                when (menuId) {
                    "manage_new_users" -> {
                        // Navigate to AdminNewUsersFragment
                        findNavController().navigate(
                            AdminHomeFragmentDirections.actionAdminHomeFragmentToAdminNewUsersFragment()
                        )
                    }
                    "manage_orders" -> {
                        // Navigate to order service list
                        findNavController().navigate(
                            R.id.action_global_orderServiceListFragment
                        )
                    }
//                    "manage_users" -> {}
                }
            }
        })
    }
}
