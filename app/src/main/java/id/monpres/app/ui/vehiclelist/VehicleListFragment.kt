package id.monpres.app.ui.vehiclelist

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.view.ActionMode
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateMarginsRelative
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.transition.MaterialSharedAxis
import dagger.hilt.android.AndroidEntryPoint
import id.monpres.app.MainActivity
import id.monpres.app.MainViewModel
import id.monpres.app.R
import id.monpres.app.databinding.FragmentVehicleListBinding
import id.monpres.app.ui.BaseFragment
import id.monpres.app.ui.adapter.VehicleAdapter
import id.monpres.app.ui.itemdecoration.SpacingItemDecoration

@AndroidEntryPoint
class VehicleListFragment : BaseFragment() {

    companion object {
        fun newInstance() = VehicleListFragment()
    }

    /* View Models */
    private val viewModel: VehicleListViewModel by viewModels()
    private val mainViewModel: MainViewModel by activityViewModels()

    /* Bindings */
    private lateinit var binding: FragmentVehicleListBinding

    /* Variables */
    private lateinit var vehicleAdapter: VehicleAdapter
    private var actionMode: ActionMode? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

        binding = FragmentVehicleListBinding.inflate(inflater, container, false)

        // Set insets
        ViewCompat.setOnApplyWindowInsetsListener(binding.fragmentListVehicleNestedScrollView) { v, windowInsets ->
            val insets =
                windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                insets.left,
                0,
                insets.right,
                insets.bottom
            )
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.fragmentListVehicleFloatingActionButtonAddVehicle) { v, windowInsets ->
            val insets =
                windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            // Apply the insets as a margin to the view. This solution sets
            // only the bottom, left, and right dimensions, but you can apply whichever
            // insets are appropriate to your layout. You can also update the view padding
            // if that's more appropriate.
            (v.layoutParams as ViewGroup.MarginLayoutParams).updateMarginsRelative(
                insets.left + 16.dpToPx(),
                0,
                insets.right + 16.dpToPx(),
                insets.bottom + 16.dpToPx()
            )

            // Return CONSUMED if you don't want the window insets to keep passing
            // down to descendant views.
            WindowInsetsCompat.CONSUMED
        }

        setupVehicleRecyclerView()
        setupVehiclesObservers()
        setupListeners()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as MainActivity).binding.activityMainAppBarLayout.background =
            binding.root.background
//        val navController = findNavController()
//        val drawerLayout = (requireActivity() as MainActivity).drawerLayout
//        val appBarConfiguration =
//            AppBarConfiguration(navController.graph, drawerLayout = drawerLayout)
//
//        binding.fragmentListVehicleToolbar.setupWithNavController(
//            navController,
//            appBarConfiguration
//        )
    }

    private fun setupVehicleRecyclerView() {
        // Create adapter with current state
        vehicleAdapter = VehicleAdapter(
            onItemClick = { vehicle ->
                val direction =
                    VehicleListFragmentDirections.actionVehicleListFragmentToEditVehicleFragment(
                        vehicle
                    )
                findNavController().navigate(
                    direction
                )
            },
            onSelectionModeChanged = { isInSelectionMode ->
                if (isInSelectionMode) {
                    if (actionMode == null) {
                        actionMode =
                            (requireActivity() as MainActivity).startSupportActionMode(
                                actionModeCallback
                            )
                    }
                    updateActionModeTitle()
                } else {
                    actionMode?.finish()
                    actionMode = null
                }

            },
            onItemSelected = {
                updateActionModeTitle()
            }
        )

        binding.fragmentListVehicleRecyclerViewListVehicle.apply {
            addItemDecoration(SpacingItemDecoration(2))
            adapter = vehicleAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun setupVehiclesObservers() {
        // Submit vehicles to adapter
        observeUiState(mainViewModel.userVehiclesState) {
            vehicleAdapter.submitList(it)
        }
    }

    fun setupListeners() {
        binding.fragmentListVehicleFloatingActionButtonAddVehicle.setOnClickListener {
            findNavController().navigate(VehicleListFragmentDirections.actionVehicleListFragmentToInsertVehicleFragment())
        }
    }

    private val actionModeCallback: ActionMode.Callback =
        object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                mode.menuInflater.inflate(R.menu.contextual_vehicle_menu, menu)
                mode.title = getString(R.string.select_items) // Initial title
                return true // Return true to show the CAB
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
                // Can be used to update the CAB's menu items if needed
                return false // Return false if nothing is changed
            }

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                return when (item.itemId) {
                    R.id.contextual_vehicle_menu_action_delete_selected -> {
                        val selectedIds = vehicleAdapter.getSelectedVehicleIds()
                        if (selectedIds.isNotEmpty()) {
                            MaterialAlertDialogBuilder(requireContext())
                                .setTitle(getString(R.string.delete_vehicles))
                                .setMessage(getString(R.string.delete_selected_vehicles_confirmation))
                                .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                                    dialog.dismiss()
                                }
                                .setPositiveButton(getString(R.string.delete)) { dialog, _ ->
                                    // Call ViewModel to delete these items
                                    observeUiStateOneShot(viewModel.deleteVehicles(selectedIds)) {
                                        Toast.makeText(
                                            requireContext(),
                                            resources.getQuantityString(
                                                R.plurals.deleted_x_items,
                                                selectedIds.size,
                                                selectedIds.size
                                            ),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        dialog.dismiss()
                                    }
                                }.show()
                            // The list will update via the ViewModel observer after deletion
                        }
                        mode.finish() // Close the CAB
                        true
                    }
                    // Handle other actions
                    else -> false
                }
            }

            override fun onDestroyActionMode(mode: ActionMode) {
                // Called when the CAB is removed (e.g., by pressing back or mode.finish())
                vehicleAdapter.finishSelectionMode() // Ensure adapter is also out of selection mode
                actionMode = null
                (requireActivity() as MainActivity).supportActionBar?.show()
            }

//            override fun onActionItemClicked(
//                mode: android.view.ActionMode?,
//                item: MenuItem?
//            ): Boolean {
//                return when (item?.itemId) {
//                    R.id.contextual_vehicle_menu_action_delete_selected -> {
//                        val selectedIds = vehicleAdapter.getSelectedVehicleIds()
//                        if (selectedIds.isNotEmpty()) {
//                            // Call ViewModel to delete these items
//                            observeUiStateOneShot(viewModel.deleteVehicles(selectedIds)) {
//                                Toast.makeText(
//                                    requireContext(),
//                                    getString(R.string.deleted_x_items, selectedIds.size),
//                                    Toast.LENGTH_SHORT
//                                ).show()
//                            }
//                            // The list will update via the ViewModel observer after deletion
//                        }
//                        mode?.finish() // Close the CAB
//                        true
//                    }
//                    // Handle other actions
//                    else -> false
//                }
//            }
//
//            override fun onCreateActionMode(
//                mode: android.view.ActionMode?,
//                menu: Menu?
//            ): Boolean {
//                mode?.menuInflater?.inflate(R.menu.contextual_vehicle_menu, menu)
//                mode?.title = getString(R.string.select_items) // Initial title
//                return true // Return true to show the CAB
//            }
//
//            override fun onDestroyActionMode(p0: android.view.ActionMode?) {
//                // Called when the CAB is removed (e.g., by pressing back or mode.finish())
//                vehicleAdapter.finishSelectionMode() // Ensure adapter is also out of selection mode
//                actionMode = null
//            }
//
//            override fun onPrepareActionMode(
//                p0: android.view.ActionMode?,
//                p1: Menu?
//            ): Boolean {
//                // Can be used to update the CAB's menu items if needed
//                return false // Return false if nothing is changed
//            }
        }

    private fun updateActionModeTitle() {
        actionMode?.title = resources.getQuantityString(
            R.plurals.x_items_selected,
            vehicleAdapter.getSelectedItemCount(), vehicleAdapter.getSelectedItemCount()
        )
    }

    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    // It's good practice to finish action mode if the fragment is being destroyed
    // or if the user navigates away while CAB is active.
    override fun onPause() {
        super.onPause()
        actionMode?.finish()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        actionMode?.finish() // Clean up CAB
    }

    override val progressIndicator: LinearProgressIndicator
        get() = binding.fragmentListVehicleProgressIndicator
}