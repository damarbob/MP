package id.monpres.app.ui.vehiclelist

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnPreDraw
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.transition.MaterialElevationScale
import com.google.android.material.transition.MaterialSharedAxis
import dagger.hilt.android.AndroidEntryPoint
import dev.androidbroadcast.vbpd.viewBinding
import id.monpres.app.MainActivity
import id.monpres.app.MainGraphViewModel
import id.monpres.app.R
import id.monpres.app.databinding.FragmentVehicleListBinding
import id.monpres.app.ui.BaseFragment
import id.monpres.app.ui.adapter.VehicleAdapter
import id.monpres.app.ui.itemdecoration.SpacingItemDecoration
import id.monpres.app.utils.dpToPx
import id.monpres.app.utils.setMargins

@AndroidEntryPoint
class VehicleListFragment : BaseFragment(R.layout.fragment_vehicle_list) {

    companion object {
        fun newInstance() = VehicleListFragment()
    }

    /* View Models */
    private val viewModel: VehicleListViewModel by viewModels()
    private val mainGraphViewModel: MainGraphViewModel by activityViewModels()

    /* Bindings */
    private val binding by viewBinding(FragmentVehicleListBinding::bind)

    /* Variables */
    private lateinit var vehicleAdapter: VehicleAdapter
    private var actionMode: android.view.ActionMode? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        postponeEnterTransition()
        view.doOnPreDraw { startPostponedEnterTransition() }

        enterTransition = MaterialSharedAxis(MaterialSharedAxis.X, /* forward= */ true)
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.X, /* forward= */ false)

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
            windowInsets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.fragmentListVehicleFloatingActionButtonAddVehicle) { v, windowInsets ->
            val insets =
                windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            // Apply the insets as a margin to the view. This solution sets
            // only the bottom, left, and right dimensions, but you can apply whichever
            // insets are appropriate to your layout. You can also update the view padding
            // if that's more appropriate.
            v.setMargins(
                left = insets.left + 16.dpToPx(requireActivity()),
                top = 0,
                right = insets.right + 16.dpToPx(requireActivity()),
                bottom = insets.bottom + 16.dpToPx(requireActivity())
            )

            // Return CONSUMED if you don't want the window insets to keep passing
            // down to descendant views.
            WindowInsetsCompat.CONSUMED
        }

        setupVehicleRecyclerView()
        setupVehiclesObservers()
        setupListeners()
    }

    private fun setupVehicleRecyclerView() {
        // Create adapter with current state
        vehicleAdapter = VehicleAdapter(
            onItemClick = { vehicle, root ->
                exitTransition = MaterialElevationScale(false)
                reenterTransition = MaterialElevationScale(true)
                val editVehicleTransitionName = getString(R.string.edit_vehicle_transition_name)
                val extras = FragmentNavigatorExtras(root to editVehicleTransitionName)
                val direction =
                    VehicleListFragmentDirections.actionVehicleListFragmentToEditVehicleFragment(
                        vehicle
                    )
                findNavController().navigate(
                    direction, extras
                )
            },
            onSelectionModeChanged = { isInSelectionMode ->
                if (isInSelectionMode) {
                    if (actionMode == null) {
                        actionMode =
                            (requireActivity() as MainActivity).startActionMode(
                                actionModeCallback, android.view.ActionMode.TYPE_PRIMARY
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
        observeUiState(mainGraphViewModel.userVehiclesState) {
            vehicleAdapter.submitList(it)
        }
    }

    fun setupListeners() {
        binding.fragmentListVehicleFloatingActionButtonAddVehicle.setOnClickListener {
            findNavController().navigate(VehicleListFragmentDirections.actionVehicleListFragmentToInsertVehicleFragment())
        }
    }

    private val actionModeCallback: android.view.ActionMode.Callback2 =
        object : android.view.ActionMode.Callback2() {
//            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
//                mode.menuInflater.inflate(R.menu.contextual_vehicle_menu, menu)
//                mode.title = getString(R.string.select_items) // Initial title
//                (requireActivity() as MainActivity).supportActionBar?.hide()
//                return true // Return true to show the CAB
//            }
//
//            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
//                // Can be used to update the CAB's menu items if needed
//                return false // Return false if nothing is changed
//            }
//
//            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
//                return when (item.itemId) {
//                    R.id.contextual_vehicle_menu_action_delete_selected -> {
//                        val selectedIds = vehicleAdapter.getSelectedVehicleIds()
//                        if (selectedIds.isNotEmpty()) {
//                            MaterialAlertDialogBuilder(requireContext())
//                                .setTitle(getString(R.string.delete_vehicles))
//                                .setMessage(getString(R.string.delete_selected_vehicles_confirmation))
//                                .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
//                                    dialog.dismiss()
//                                }
//                                .setPositiveButton(getString(R.string.delete)) { dialog, _ ->
//                                    // Call ViewModel to delete these items
//                                    observeUiStateOneShot(viewModel.deleteVehicles(selectedIds)) {
//                                        Toast.makeText(
//                                            requireContext(),
//                                            resources.getQuantityString(
//                                                R.plurals.deleted_x_items,
//                                                selectedIds.size,
//                                                selectedIds.size
//                                            ),
//                                            Toast.LENGTH_SHORT
//                                        ).show()
//                                        dialog.dismiss()
//                                    }
//                                }.show()
//                            // The list will update via the ViewModel observer after deletion
//                        }
//                        mode.finish() // Close the CAB
//                        true
//                    }
//                    // Handle other actions
//                    else -> false
//                }
//            }
//
//            override fun onDestroyActionMode(mode: ActionMode) {
//                // Called when the CAB is removed (e.g., by pressing back or mode.finish())
//                vehicleAdapter.finishSelectionMode() // Ensure adapter is also out of selection mode
//                actionMode = null
//                (requireActivity() as MainActivity).supportActionBar?.show()
//            }

            override fun onActionItemClicked(
                mode: android.view.ActionMode?,
                item: MenuItem?
            ): Boolean {
                return when (item?.itemId) {
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
                        mode?.finish() // Close the CAB
                        true
                    }
                    // Handle other actions
                    else -> false
                }
            }

            override fun onCreateActionMode(
                mode: android.view.ActionMode?,
                menu: Menu?
            ): Boolean {
//                (requireActivity() as MainActivity).supportActionBar?.hide()
                mode?.menuInflater?.inflate(R.menu.contextual_vehicle_menu, menu)
                mode?.title = getString(R.string.select_items) // Initial title
                return true // Return true to show the CAB
            }

            override fun onDestroyActionMode(p0: android.view.ActionMode?) {
                // Called when the CAB is removed (e.g., by pressing back or mode.finish())
                vehicleAdapter.finishSelectionMode() // Ensure adapter is also out of selection mode
                actionMode = null
//                (requireActivity() as MainActivity).supportActionBar?.show()
            }

            override fun onPrepareActionMode(
                p0: android.view.ActionMode?,
                p1: Menu?
            ): Boolean {
                // Can be used to update the CAB's menu items if needed
                return false // Return false if nothing is changed
            }
        }

    private fun updateActionModeTitle() {
        val selectedCount = vehicleAdapter.getSelectedItemCount()
        actionMode?.title =
            resources.getQuantityString(R.plurals.x_items_selected, selectedCount, selectedCount)
    }

    override fun onDestroyView() {
        // Crucial: Force the Transition Manager to stop tracking the root layout
        // This removes the reference from the ThreadLocal map causing the leak.
        (view as? ViewGroup)?.let { rootView ->
            androidx.transition.TransitionManager.endTransitions(rootView)
        }

        // Clean up the RecyclerView specifically
        // This prevents the Adapter from holding onto ViewHolders that might still
        // have transition tags on them.
        binding.fragmentListVehicleRecyclerViewListVehicle.adapter = null
        super.onDestroyView()
    }

    override val progressIndicator: LinearProgressIndicator
        get() = binding.fragmentListVehicleProgressIndicator
}
