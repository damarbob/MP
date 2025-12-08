package id.monpres.app.ui.orderserviceedit

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateMarginsRelative
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.plugin.gestures.gestures
import dagger.hilt.android.AndroidEntryPoint
import dev.androidbroadcast.vbpd.viewBinding
import id.monpres.app.MainGraphViewModel
import id.monpres.app.MapsActivity
import id.monpres.app.R
import id.monpres.app.databinding.FragmentOrderServiceEditBinding
import id.monpres.app.enums.OrderStatus
import id.monpres.app.enums.PartnerCategory
import id.monpres.app.model.MapsActivityExtraData
import id.monpres.app.model.OrderItem
import id.monpres.app.model.OrderService
import id.monpres.app.ui.BaseFragment
import id.monpres.app.ui.adapter.OrderItemAdapter
import id.monpres.app.ui.itemdecoration.SpacingItemDecoration
import id.monpres.app.ui.orderitemeditor.OrderItemEditorFragment
import id.monpres.app.usecase.IndonesianCurrencyFormatter
import id.monpres.app.utils.dpToPx
import id.monpres.app.utils.enumByNameIgnoreCaseOrNull
import kotlinx.coroutines.launch

@AndroidEntryPoint
class OrderServiceEditFragment : BaseFragment(R.layout.fragment_order_service_edit) {

    companion object {
        private val TAG = OrderServiceEditFragment::class.simpleName
    }

    private val binding by viewBinding(FragmentOrderServiceEditBinding::bind)
    private val viewModel: OrderServiceEditViewModel by viewModels()
    private val mainGraphViewModel: MainGraphViewModel by activityViewModels()
    private val args: OrderServiceEditFragmentArgs by navArgs()

    private lateinit var orderItemAdapter: OrderItemAdapter
    private val currencyFormatter = IndonesianCurrencyFormatter()

    private val pickLocationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { data ->
                val selectedLocation =
                    data.getStringExtra(MapsActivityExtraData.SELECTED_LOCATION) ?: ""

                if (selectedLocation.isNotEmpty()) {
                    try {
                        val point = Point.fromJson(selectedLocation)
                        Log.d(TAG, "Selected location: $point")
                        viewModel.updateLocation(point)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing location json", e)
                    }
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.initialize(args.orderService)

        setupUI()
        setupMapDisplayOnly()
        setupFragmentResultListener()
        setupObservers()
    }

    private fun setupUI() {

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, windowInsets ->
            val insets =
                windowInsets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(insets.left, 0, insets.right, insets.bottom)
            windowInsets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.fragmentOrderServiceEditFabSave) { v, windowInsets ->
            val insets =
                windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            (v.layoutParams as ViewGroup.MarginLayoutParams).updateMarginsRelative(
                insets.left + 16.dpToPx(requireActivity()),
                0,
                insets.right + 16.dpToPx(requireActivity()),
                insets.bottom + 16.dpToPx(requireActivity())
            )
            WindowInsetsCompat.CONSUMED
        }

        // --- 1. Order Status Dropdown (NEW) ---
        val statuses = OrderStatus.entries
        val statusLabels = statuses.map { it.getLabel(requireContext()) }
        val statusAdapter =
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, statusLabels)

        binding.fragmentOrderServiceEditAutoCompleteTextViewStatus.apply {
            setAdapter(statusAdapter)

            // Handle Selection
            setOnItemClickListener { _, _, position, _ ->
                val selectedStatus = statuses[position]
                viewModel.updateStatus(selectedStatus)
            }

            // Dropdown Interaction Fixes
            setOnClickListener {
                if (adapter != null) {
                    setAdapter(statusAdapter)
                    // Reset filter to show the full list
                    (adapter as ArrayAdapter<*>).filter.filter(null)
                    showDropDown()
                }
            }
            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus && adapter != null) {
                    setAdapter(statusAdapter)
                    showDropDown()
                }
            }
        }


        // --- 2. Two-Way Binding for Inputs ---

        // Address
        binding.fragmentOrderServiceEditTextInputLayoutAddress.editText?.doAfterTextChanged { text ->
            val newValue = text.toString()
            if (viewModel.orderService.value?.userAddress != newValue) {
                updateViewModelFromUI()
            }
        }

        // Description
        binding.fragmentOrderServiceEditTextInputLayoutDescription.editText?.doAfterTextChanged { text ->
            val newValue = text.toString()
            if (viewModel.orderService.value?.issueDescription != newValue) {
                updateViewModelFromUI()
            }
        }

        // Category
        val categories = PartnerCategory.toListString(requireContext())
        val categoryAdapter =
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, categories)

        binding.fragmentOrderServiceEditAutoCompleteTextViewIssueCategory.apply {
            setAdapter(categoryAdapter)

            doAfterTextChanged { text ->
                val newValue = text.toString()
                if (viewModel.orderService.value?.issue != newValue) {
                    updateViewModelFromUI()
                }
            }

            setOnClickListener {
                if (adapter != null) {
                    setAdapter(categoryAdapter)
                    // Reset filter to show the full list
                    (adapter as ArrayAdapter<*>).filter.filter(null)
                    showDropDown()
                }
            }

            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus && adapter != null) {
                    setAdapter(categoryAdapter)
                    showDropDown()
                }
            }
        }

        // Order Items Recycler
        orderItemAdapter = OrderItemAdapter()
        binding.fragmentOrderServiceEditRecyclerViewOrderItemsSummary.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = orderItemAdapter
            addItemDecoration(SpacingItemDecoration(8))
        }

        // Edit Items Button
        binding.fragmentOrderServiceEditButtonEditItems.setOnClickListener {
            val currentOrder = viewModel.orderService.value ?: return@setOnClickListener

            findNavController().navigate(
                OrderServiceEditFragmentDirections.actionOrderServiceEditFragmentToOrderItemEditorFragment(
                    orderItems = currentOrder.orderItems?.toTypedArray(),
                    orderService = currentOrder
                )
            )
        }

        // Change Location Button
        binding.fragmentOrderServiceEditButtonChangeLocation.setOnClickListener {
            openMapSelection()
        }

        // Save Button
        binding.fragmentOrderServiceEditFabSave.setOnClickListener {
            saveChanges()
        }
    }

    private fun updateViewModelFromUI() {
        val address =
            binding.fragmentOrderServiceEditTextInputLayoutAddress.editText?.text.toString()
        val category =
            binding.fragmentOrderServiceEditAutoCompleteTextViewIssueCategory.text.toString()
        val description =
            binding.fragmentOrderServiceEditTextInputLayoutDescription.editText?.text.toString()
        viewModel.updateDetails(
            address,
            PartnerCategory.fromLabel(requireContext(), category)?.name ?: "",
            description
        )
    }

    private fun setupMapDisplayOnly() {
        binding.fragmentOrderServiceEditMapView.gestures.apply {
            scrollEnabled = false
            rotateEnabled = false
            pitchEnabled = false
            doubleTapToZoomInEnabled = false
        }
    }

    private fun openMapSelection() {
        val currentOrder = viewModel.orderService.value
        val points = ArrayList<String>()

        if (currentOrder?.selectedLocationLat != null && currentOrder.selectedLocationLng != null) {
            val currentPoint = Point.fromLngLat(
                currentOrder.selectedLocationLng!!,
                currentOrder.selectedLocationLat!!
            )
            points.add(currentPoint.toJson())
        }

        val intent = Intent(requireContext(), MapsActivity::class.java).apply {
            putExtra(MapsActivityExtraData.EXTRA_PICK_MODE, true)
            putStringArrayListExtra("points", points)
        }
        pickLocationLauncher.launch(intent)
    }

    private fun setupFragmentResultListener() {
        parentFragmentManager.setFragmentResultListener(
            OrderItemEditorFragment.REQUEST_KEY_ORDER_ITEM_EDITOR,
            viewLifecycleOwner
        ) { _, bundle ->
            val updatedItems = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                bundle.getParcelableArrayList(
                    OrderItemEditorFragment.KEY_ORDER_ITEMS,
                    OrderItem::class.java
                )
            } else {
                bundle.getParcelableArrayList(OrderItemEditorFragment.KEY_ORDER_ITEMS)
            }
            updatedItems?.let { viewModel.updateOrderItems(it) }
        }
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.orderService.collect { order ->
                    if (order == null) return@collect
                    bindDataToView(order)
                }
            }
        }
    }

    private fun bindDataToView(order: OrderService) {
        // Status
        val statusLabel = order.status?.getLabel(requireContext())
        val currentStatusText =
            binding.fragmentOrderServiceEditAutoCompleteTextViewStatus.text.toString()
        if (statusLabel != null && statusLabel != currentStatusText) {
            binding.fragmentOrderServiceEditAutoCompleteTextViewStatus.setText(statusLabel, false)
        }

        // Address
        val currentAddress =
            binding.fragmentOrderServiceEditTextInputLayoutAddress.editText?.text.toString()
        if (currentAddress != order.userAddress) {
            binding.fragmentOrderServiceEditTextInputLayoutAddress.editText?.setText(order.userAddress)
        }

        // Description
        val currentDesc =
            binding.fragmentOrderServiceEditTextInputLayoutDescription.editText?.text.toString()
        if (currentDesc != order.issueDescription) {
            binding.fragmentOrderServiceEditTextInputLayoutDescription.editText?.setText(order.issueDescription)
        }

        // Category
        val currentCategory =
            binding.fragmentOrderServiceEditAutoCompleteTextViewIssueCategory.text.toString()
        if (currentCategory != order.issue) {

            enumByNameIgnoreCaseOrNull<PartnerCategory>(order.issue!!)?.label.let {
                it?.let { resId ->
                    binding.fragmentOrderServiceEditAutoCompleteTextViewIssueCategory.setText(
                        getString(resId),
                        false
                    )
                }
            }
        }

        // Map Logic
        val lat = order.selectedLocationLat
        val lng = order.selectedLocationLng
        binding.fragmentOrderServiceEditTextViewCoordinates.text =
            if (lat != null && lng != null) "$lat, $lng" else "- , -"

        if (lat != null && lng != null && (lat != 0.0 || lng != 0.0)) {
            if (lat > -90 && lat < 90 && lng > -180 && lng < 180) {
                binding.fragmentOrderServiceEditMapView.mapboxMap.setCamera(
                    CameraOptions.Builder()
                        .center(Point.fromLngLat(lng, lat))
                        .zoom(15.0)
                        .build()
                )
            }
        }

        // Summary
        orderItemAdapter.submitList(order.orderItems)
        binding.fragmentOrderServiceEditTextViewTotalItemsPrice.text =
            order.price?.let { currencyFormatter(it) } ?: "-"
    }

    private fun saveChanges() {
        updateViewModelFromUI()

        val finalOrder = viewModel.orderService.value ?: return

        observeUiStateOneShot(
            mainGraphViewModel.updateOrderService(finalOrder)
        ) {
            Toast.makeText(
                requireContext(),
                getString(R.string.order_updated_successfully), Toast.LENGTH_SHORT
            )
                .show()
            findNavController().popBackStack()
        }
    }

    override val progressIndicator: LinearProgressIndicator
        get() = binding.fragmentOrderServiceEditLinearProgressIndicator
}