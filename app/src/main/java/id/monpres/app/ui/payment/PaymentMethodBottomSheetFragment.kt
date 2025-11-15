package id.monpres.app.ui.payment

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dev.androidbroadcast.vbpd.viewBinding
import id.monpres.app.R
import id.monpres.app.databinding.BottomSheetPaymentMethodBinding
import id.monpres.app.model.PaymentMethod
import id.monpres.app.ui.adapter.PaymentMethodAdapter
import id.monpres.app.ui.itemdecoration.SpacingItemDecoration

class PaymentMethodBottomSheetFragment : BottomSheetDialogFragment(R.layout.bottom_sheet_payment_method) {

    private val binding by viewBinding(BottomSheetPaymentMethodBinding::bind)
    private var selectedPaymentMethod: PaymentMethod? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val paymentMethods = createSamplePaymentMethods() // TODO: Replace with actual data or enum
        val initialSelectedId = arguments?.getString(KEY_SELECTED_ID) ?: paymentMethods.first().id

        selectedPaymentMethod = paymentMethods.find { it.id == initialSelectedId }

        val adapter = PaymentMethodAdapter(paymentMethods, initialSelectedId) { clickedMethod ->
            selectedPaymentMethod = clickedMethod
        }

        binding.bottomSheetPaymentMethodRecyclerViewPaymentMethods.layoutManager = LinearLayoutManager(requireContext())
        binding.bottomSheetPaymentMethodRecyclerViewPaymentMethods.adapter = adapter
        binding.bottomSheetPaymentMethodRecyclerViewPaymentMethods.addItemDecoration(SpacingItemDecoration(4))

        binding.bottomSheetPaymentMethodButtonCancel.setOnClickListener {
            dismiss()
        }

        binding.bottomSheetPaymentMethodButtonSave.setOnClickListener {
            selectedPaymentMethod?.let {
                setFragmentResult(
                    REQUEST_KEY,
                    bundleOf(KEY_PAYMENT_METHOD to it)
                )
            }
            dismiss()
        }
    }

    // TODO: In a real app, you would get this from a repository/API
    private fun createSamplePaymentMethods(): List<PaymentMethod> {
        return PaymentMethod.getDefaultPaymentMethods(requireContext())
    }

    companion object {
        const val TAG = "PaymentMethodBottomSheet"
        const val REQUEST_KEY = "payment_method_request"
        const val KEY_PAYMENT_METHOD = "selected_payment_method"
        const val KEY_SELECTED_ID = "selected_id"

        fun newInstance(selectedId: String): PaymentMethodBottomSheetFragment {
            return PaymentMethodBottomSheetFragment().apply {
                arguments = bundleOf(KEY_SELECTED_ID to selectedId)
            }
        }
    }
}
