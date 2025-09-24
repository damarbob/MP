package id.monpres.app.ui.orderitemeditor

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import id.monpres.app.MainViewModel
import id.monpres.app.R
import id.monpres.app.databinding.FragmentOrderItemEditorBinding
import id.monpres.app.enums.UserRole
import id.monpres.app.model.OrderItem
import id.monpres.app.model.OrderService
import id.monpres.app.ui.adapter.OrderItemEditorAdapter
import id.monpres.app.ui.itemdecoration.SpacingItemDecoration
import id.monpres.app.usecase.CalculateAerialDistanceUseCase
import id.monpres.app.usecase.CurrencyFormatterUseCase
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.ceil

class OrderItemEditorFragment : Fragment() {

    companion object {
        fun newInstance() = OrderItemEditorFragment()
        const val TAG = "OrderItemEditorFragment"

        const val REQUEST_KEY_ORDER_ITEM_EDITOR = "orderItemEditorRequestKey"
        const val KEY_ORDER_ITEMS = "orderItems"
    }

    private val viewModel: OrderItemEditorViewModel by viewModels()
    private val mainViewModel: MainViewModel by activityViewModels()

    private lateinit var binding: FragmentOrderItemEditorBinding

    private val args: OrderItemEditorFragmentArgs by navArgs()

    private lateinit var orderItemEditorAdapter: OrderItemEditorAdapter
    private val items: MutableList<OrderItem> = mutableListOf()

    private val calculateAerialDistance = CalculateAerialDistanceUseCase()
    private val currencyFormatterUseCase = CurrencyFormatterUseCase()

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
        binding = FragmentOrderItemEditorBinding.inflate(inflater, container, false)

        // Set insets
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, windowInsets ->
            val insets =
                windowInsets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(insets.left, 0, insets.right, insets.bottom)
            windowInsets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.fragmentOrderItemEditorNestedScrollView) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(insets.left, 0, insets.right, insets.bottom)
            WindowInsetsCompat.CONSUMED
        }
        items.clear()
        args.orderItems?.let { item ->
            items.addAll(item)
        }
        Log.d(TAG, "args Items: ${args.orderItems}")
        Log.d(TAG, "Items: $items")
        addAdditionalItems()

        setupRecyclerView()
        setupListeners()
        return binding.root
    }

    private fun addAdditionalItems() {
        val user = mainViewModel.getCurrentUser()
        var distanceFee = 0f
        if (user?.role == UserRole.PARTNER) {
            val orderDistance = calculateAerialDistance(
                user.locationLat?.toDouble() ?: 0.0,
                user.locationLng?.toDouble() ?: 0.0,
                args.orderService.selectedLocationLat ?: 0.0,
                args.orderService.selectedLocationLng ?: 0.0
            )
            distanceFee = ceil(orderDistance / 1000) * OrderItem.DISTANCE_FEE
        }
        items.apply {
            if (items.none { it.id == OrderItem.PLATFORM_FEE_ID })
                add(
                    items.size - 1,
                    OrderItem(
                        id = OrderItem.PLATFORM_FEE_ID,
                        name = OrderItem.PLATFORM_FEE_NAME,
                        price = OrderItem.PLATFORM_FEE,
                        quantity = 1f,
                        isFixed = true
                    )
                )

            if (items.none { it.id == OrderItem.DISTANCE_FEE_ID })
                add(
                    items.size - 1,
                    OrderItem(
                        id = OrderItem.DISTANCE_FEE_ID,
                        name = OrderItem.DISTANCE_FEE_NAME,
                        price = distanceFee,
                        quantity = 1f,
                        isFixed = true
                    )
                )
        }
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
            "${currencyFormatterUseCase(OrderService.getPriceFromOrderItems(items))}"
    }

    private fun setupListeners() {
        binding.fragmentOrderItemEditorButtonSave.setOnClickListener {
            setFragmentResult(REQUEST_KEY_ORDER_ITEM_EDITOR, bundleOf(KEY_ORDER_ITEMS to items))
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
            textInputLayoutName.editText?.setText(it.name)
            textInputLayoutQuantity.editText?.setText(it.quantity.toString())
            textInputLayoutPrice.editText?.setText(
                currencyFormatterUseCase(it.price)
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
                val quantity = textInputLayoutQuantity.editText?.text.toString().toFloat()
                val price = NumberFormat.getCurrencyInstance(
                    Locale.Builder().setRegion("ID").setLanguage("id").build()
                ).apply {
                    maximumFractionDigits = 0
                }.parse(textInputLayoutPrice.editText?.text.toString())?.toFloat() ?: 0f

                if (item == null) {
                    // Add new item
                    val newItem = OrderItem(
                        name = name,
                        quantity = quantity,
                        price = price
                    )
                    items.add(0, newItem)
                } else {
                    // Edit existing item
                    items.indexOf(item).let {
                        items.set(
                            it, OrderItem(
                                name = name,
                                quantity = quantity,
                                price = price
                            )
                        )
                    }
                }
                Log.d(TAG, "Items: $items")
                Log.d(TAG, "adapter item count: ${orderItemEditorAdapter.itemCount}")
                orderItemEditorAdapter.submitList(items.toList())
                updateTotalPriceView()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun validateForm(view: TextInputLayout): Boolean {
        // Validate name (required)
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

    /**
     * Adds a currency formatter to an EditText.
     *
     * This function adds a TextWatcher to the EditText that formats the input as currency.
     * The formatter uses the default locale's currency symbol and formatting rules.
     *
     * For example, if the user types "12345", the EditText will display "$123.45" (assuming the default locale is US).
     *
     * The formatter also handles cases where the input is blank or invalid. If the input is blank,
     * the EditText will display "$0.00". If the input is invalid (e.g., contains non-numeric characters),
     * the formatter will attempt to remove the invalid characters and format the remaining numeric input.
     *
     * Note: This function modifies the EditText in place.
     */
    fun EditText.addCurrencyFormatter() {
        this.addTextChangedListener(object : TextWatcher {
            private var current = ""

            override fun afterTextChanged(s: Editable?) {}

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (s.toString() != current) {
                    this@addCurrencyFormatter.removeTextChangedListener(this)
                    val cleanString = s.toString().replace("\\D".toRegex(), "")
                    val parsed = if (cleanString.isBlank()) 0.0 else cleanString.toDouble()
                    val formatted = currencyFormatterUseCase(parsed)
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