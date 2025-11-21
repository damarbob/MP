package id.monpres.app.ui.orderitemeditor

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.transition.MaterialSharedAxis
import dev.androidbroadcast.vbpd.viewBinding
import id.monpres.app.MainViewModel
import id.monpres.app.R
import id.monpres.app.databinding.FragmentOrderItemEditorBinding
import id.monpres.app.enums.UserRole
import id.monpres.app.model.OrderItem
import id.monpres.app.model.OrderService
import id.monpres.app.ui.adapter.OrderItemEditorAdapter
import id.monpres.app.ui.itemdecoration.SpacingItemDecoration
import id.monpres.app.usecase.CalculateAerialDistanceUseCase
import id.monpres.app.usecase.IndonesianCurrencyFormatter
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.ceil

class OrderItemEditorFragment : Fragment(R.layout.fragment_order_item_editor) {

    companion object {
        fun newInstance() = OrderItemEditorFragment()
        const val TAG = "OrderItemEditorFragment"

        const val REQUEST_KEY_ORDER_ITEM_EDITOR = "orderItemEditorRequestKey"
        const val KEY_ORDER_ITEMS = "orderItems"
    }

    private val viewModel: OrderItemEditorViewModel by viewModels()
    private val mainViewModel: MainViewModel by activityViewModels()
    private val binding by viewBinding(FragmentOrderItemEditorBinding::bind)
    private val args: OrderItemEditorFragmentArgs by navArgs()

    private lateinit var orderItemEditorAdapter: OrderItemEditorAdapter
    private val items: MutableList<OrderItem> = mutableListOf()

    private val calculateAerialDistance = CalculateAerialDistanceUseCase()
    private val indonesianCurrencyFormatter = IndonesianCurrencyFormatter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
        exitTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
        reenterTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(insets.left, 0, insets.right, insets.bottom)
            windowInsets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.fragmentOrderItemEditorNestedScrollView) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(insets.left, 0, insets.right, insets.bottom)
            WindowInsetsCompat.CONSUMED
        }

        setupData() // Combined data loading logic here
        setupRecyclerView()
        setupListeners()
    }

    private fun setupData() {
        items.clear()
        val user = mainViewModel.getCurrentUser()
        val isAdmin = user?.role == UserRole.ADMIN

        // 1. Load existing items and UNLOCK fees if Admin
        args.orderItems?.let { loadedItems ->
            val processedItems = loadedItems.map { item ->
                if (item.id == OrderItem.PLATFORM_FEE_ID || item.id == OrderItem.DISTANCE_FEE_ID) {
                    // If Admin, set isFixed = false so they can edit. Otherwise keep true.
                    item.copy(isFixed = !isAdmin)
                } else {
                    item
                }
            }
            items.addAll(processedItems)
        }

        // 2. Add missing fees (if they don't exist yet)
        addAdditionalItems(isAdmin)

        Log.d(TAG, "Items loaded: $items")
    }

    private fun addAdditionalItems(isAdmin: Boolean) {
        val user = mainViewModel.getCurrentUser()
        var distanceFee = 0.0

        // Calculate distance fee logic (Partner only automatically, or generic calc)
        if (user?.role == UserRole.PARTNER) {
            val orderDistance = calculateAerialDistance(
                user.locationLat?.toDouble() ?: 0.0,
                user.locationLng?.toDouble() ?: 0.0,
                args.orderService.selectedLocationLat ?: 0.0,
                args.orderService.selectedLocationLng ?: 0.0
            ).toDouble()
            distanceFee = ceil(orderDistance / 1000) * OrderItem.DISTANCE_FEE
        }

        // Check if items missing and add them with correct fixed state
        if (items.none { it.id == OrderItem.PLATFORM_FEE_ID }) {
            items.add(
                if (items.isEmpty()) 0 else items.size, // Add to end or specific logic
                OrderItem(
                    id = OrderItem.PLATFORM_FEE_ID,
                    name = getString(OrderItem.PLATFORM_FEE_NAME),
                    price = OrderItem.PLATFORM_FEE,
                    quantity = 1.0,
                    isFixed = !isAdmin // False if Admin, True otherwise
                )
            )
        }

        if (items.none { it.id == OrderItem.DISTANCE_FEE_ID }) {
            items.add(
                if (items.isEmpty()) 0 else items.size,
                OrderItem(
                    id = OrderItem.DISTANCE_FEE_ID,
                    name = getString(OrderItem.DISTANCE_FEE_NAME),
                    price = distanceFee,
                    quantity = 1.0,
                    isFixed = !isAdmin // False if Admin, True otherwise
                )
            )
        }

        // Ensure fees are at the top if preferred, or just let them be added.
        // (Your original code added them at index 0 or size-1. Keeping logic simple here).
    }

    private fun setupRecyclerView() {
        orderItemEditorAdapter = OrderItemEditorAdapter()
        binding.fragmentOrderItemEditorRecyclerViewOrderItemList.apply {
            addItemDecoration(SpacingItemDecoration(8))
            layoutManager = LinearLayoutManager(requireContext())
            adapter = orderItemEditorAdapter
        }
        orderItemEditorAdapter.registerItemClickListener(object :
            OrderItemEditorAdapter.ItemClickListener {
            override fun onEditClick(item: OrderItem) {
                showAddEditDialog(item)
            }

            override fun onDeleteClick(item: OrderItem) {
                items.remove(item)
                orderItemEditorAdapter.submitList(items.toList())
                updateTotalPriceView()
            }
        })
        orderItemEditorAdapter.submitList(items.toList())
        updateTotalPriceView()
    }

    private fun updateTotalPriceView() {
        binding.fragmentOrderItemEditorTextViewTotal.text =
            indonesianCurrencyFormatter(OrderService.getPriceFromOrderItems(items))
    }

    private fun setupListeners() {
        binding.fragmentOrderItemEditorButtonSave.setOnClickListener {
            // --- CRITICAL: Lock fees back to Fixed=True before saving ---
            val finalItems = items.map { item ->
                if (item.id == OrderItem.PLATFORM_FEE_ID || item.id == OrderItem.DISTANCE_FEE_ID) {
                    item.copy(isFixed = true) // Always lock them on save
                } else {
                    item
                }
            }.toMutableList()

            setFragmentResult(
                REQUEST_KEY_ORDER_ITEM_EDITOR,
                bundleOf(KEY_ORDER_ITEMS to ArrayList(finalItems))
            )
            findNavController().popBackStack()
        }

        binding.fragmentOrderItemEditorButtonAdd.setOnClickListener {
            showAddEditDialog()
        }
    }

    private fun showAddEditDialog(item: OrderItem? = null) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_order_item_editor, null, false)
        val textInputLayoutName =
            dialogView.findViewById<TextInputLayout>(R.id.dialogOrderItemEditorTextInputLayoutName)
        val textInputLayoutQuantity =
            dialogView.findViewById<TextInputLayout>(R.id.dialogOrderItemEditorTextInputLayoutQuantity)
        val textInputLayoutPrice =
            dialogView.findViewById<TextInputLayout>(R.id.dialogOrderItemEditorTextInputLayoutPrice)

        item?.let {
            val isSystemFee =
                it.id == OrderItem.PLATFORM_FEE_ID || it.id == OrderItem.DISTANCE_FEE_ID

            textInputLayoutName.editText?.setText(it.name)
            // If it is a system fee, prevent changing the NAME, but allow price/qty
            textInputLayoutName.isEnabled = !isSystemFee

            textInputLayoutQuantity.editText?.setText(it.quantity.toString())
            textInputLayoutPrice.editText?.setText(
                indonesianCurrencyFormatter(it.price)
            )
        }

        textInputLayoutPrice.editText?.addCurrencyFormatter()

        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                if (!validateForm(textInputLayoutName)) return@setPositiveButton
                if (!validateForm(textInputLayoutQuantity)) return@setPositiveButton
                if (!validateForm(textInputLayoutPrice)) return@setPositiveButton

                val name = textInputLayoutName.editText?.text.toString()
                val quantity = textInputLayoutQuantity.editText?.text.toString().toDouble()
                val price = NumberFormat.getCurrencyInstance(
                    Locale.Builder().setRegion("ID").setLanguage("id").build()
                ).apply {
                    maximumFractionDigits = 0
                }.parse(textInputLayoutPrice.editText?.text.toString())?.toDouble() ?: 0.0

                // Check current user again just to be safe for the isFixed state in memory
                val user = mainViewModel.getCurrentUser()
                val isAdmin = user?.role == UserRole.ADMIN

                if (item == null) {
                    // Add new item
                    val newItem = OrderItem(
                        name = name,
                        quantity = quantity,
                        price = price,
                        isFixed = false
                    )
                    items.add(0, newItem)
                } else {
                    // Edit existing item
                    items.indexOf(item).let { index ->
                        if (index != -1) {
                            val isSystemFee =
                                item.id == OrderItem.PLATFORM_FEE_ID || item.id == OrderItem.DISTANCE_FEE_ID
                            // If it is a system fee & Admin, keeps it unlocked (false).
                            // If it's a normal item, keep original state (likely false).
                            val fixedState = if (isSystemFee) !isAdmin else item.isFixed

                            items[index] = OrderItem(
                                id = item.id, // Preserve ID
                                name = name,
                                quantity = quantity,
                                price = price,
                                isFixed = fixedState
                            )
                        }
                    }
                }
                orderItemEditorAdapter.submitList(items.toList())
                updateTotalPriceView()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Helpers remain the same...
    private fun validateForm(view: TextInputLayout): Boolean {
        return if (view.editText?.text.isNullOrBlank()) {
            view.error =
                getString(R.string.x_is_required, getString(R.string.name))
            false
        } else {
            view.apply {
                error = null
                isErrorEnabled = false
            }
            true
        }
    }

    fun EditText.addCurrencyFormatter() {
        this.addTextChangedListener(object : TextWatcher {
            private var current = this@addCurrencyFormatter.text.toString()
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (s.toString() != current) {
                    this@addCurrencyFormatter.removeTextChangedListener(this)
                    val cleanString = s.toString().replace("\\D".toRegex(), "")
                    val parsed = if (cleanString.isBlank()) {
                        0L
                    } else {
                        val bigIntValue = cleanString.toBigInteger()
                        if (bigIntValue > Long.MAX_VALUE.toBigInteger()) Long.MAX_VALUE else bigIntValue.toLong()
                    }
                    val formatted = indonesianCurrencyFormatter(parsed)
                    current = formatted
                    this@addCurrencyFormatter.setText(formatted)
                    this@addCurrencyFormatter.setSelection(formatted.length)
                    this@addCurrencyFormatter.addTextChangedListener(this)
                }
            }
        })
    }

    fun goBack() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Discard changes?")
            .setMessage("Are you sure you want to discard your changes?")
            .setPositiveButton("Discard") { _, _ ->
                findNavController().popBackStack()
            }
            .setNeutralButton("Save & go back") { _, _ ->
                setFragmentResult(REQUEST_KEY_ORDER_ITEM_EDITOR, bundleOf(KEY_ORDER_ITEMS to items))
                findNavController().popBackStack()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}