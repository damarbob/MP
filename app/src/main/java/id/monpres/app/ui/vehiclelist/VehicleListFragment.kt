package id.monpres.app.ui.vehiclelist

import androidx.fragment.app.viewModels
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.transition.MaterialSharedAxis
import id.monpres.app.MainActivity
import id.monpres.app.R
import id.monpres.app.databinding.FragmentVehicleListBinding
import id.monpres.app.model.Vehicle
import id.monpres.app.ui.adapter.VehicleAdapter

class VehicleListFragment : Fragment() {

    companion object {
        fun newInstance() = VehicleListFragment()
    }

    private val viewModel: VehicleListViewModel by viewModels()

    private lateinit var binding: FragmentVehicleListBinding

    private lateinit var vehicleAdapter: VehicleAdapter
    private lateinit var vehicles: List<Vehicle>
    private var scrollPosition: Int = 0
    private var scrollOffset: Int = 0

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

        setupVehiclesObservers()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val navController = findNavController()
        val drawerLayout = (requireActivity() as MainActivity).drawerLayout
        val appBarConfiguration =
            AppBarConfiguration(navController.graph, drawerLayout = drawerLayout)

        binding.fragmentListVehicleToolbar.setupWithNavController(
            navController,
            appBarConfiguration
        )
    }

    private fun setupVehicleRecyclerView() {

        // Create adapter with current state
        vehicleAdapter = VehicleAdapter(vehicles) { vehicle ->
            saveScrollPosition()
            findNavController().navigate(
                R.id.action_vehicleListFragment_to_editVehicleFragment,
                Bundle().apply { putParcelable("vehicle", vehicle) }
            )
        }

        binding.fragmentListVehicleRecyclerViewListVehicle.apply {
            adapter = vehicleAdapter
            layoutManager = LinearLayoutManager(requireContext())
            setHasFixedSize(true)
            // Add scroll listener to save position
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    saveScrollPosition()
                }
            })
        }
    }

    private fun setupVehiclesObservers() {
        viewModel.vehicles.observe(viewLifecycleOwner, Observer {
            vehicles = it
            setupVehicleRecyclerView()
        })
        viewModel.scrollPosition.observe(viewLifecycleOwner) {
            scrollPosition = it
        }
        viewModel.scrollOffset.observe(viewLifecycleOwner) {
            scrollOffset = it
        }
    }

    private fun saveScrollPosition() {
        val layoutManager =
            binding.fragmentListVehicleRecyclerViewListVehicle.layoutManager as? LinearLayoutManager
        layoutManager?.let {
            val firstVisiblePosition = it.findFirstVisibleItemPosition()
            if (firstVisiblePosition != RecyclerView.NO_POSITION) {
                viewModel.saveScrollPosition(firstVisiblePosition)
                val firstVisibleView = it.findViewByPosition(firstVisiblePosition)
                viewModel.saveScrollOffset(firstVisibleView?.top ?: 0)
            }
        }
    }

    private fun restoreScrollPosition() {
        binding.fragmentListVehicleRecyclerViewListVehicle.post {
            val layoutManager =
                binding.fragmentListVehicleRecyclerViewListVehicle.layoutManager as? LinearLayoutManager
            layoutManager?.scrollToPositionWithOffset(
                scrollPosition,
                scrollOffset
            )
        }
    }

    override fun onPause() {
        super.onPause()
        // Save final scroll position
        saveScrollPosition()
    }

    override fun onResume() {
        super.onResume()
        restoreScrollPosition()
    }
}