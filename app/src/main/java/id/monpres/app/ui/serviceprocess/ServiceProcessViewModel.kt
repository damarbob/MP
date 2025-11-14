package id.monpres.app.ui.serviceprocess

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import id.monpres.app.model.PaymentMethod
import id.monpres.app.repository.LivePartnerLocationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class ServiceProcessViewModel @Inject constructor(
    private val livePartnerLocationRepository: LivePartnerLocationRepository
) : ViewModel() {
    companion object {
        private const val TAG = "ServiceProcessViewModel"
    }

    private val _selectedPaymentMethod = MutableStateFlow<PaymentMethod?>(null)
    val selectedPaymentMethod = _selectedPaymentMethod.asStateFlow()

    fun onPaymentMethodSelected(paymentMethod: PaymentMethod) {
        _selectedPaymentMethod.value = paymentMethod
    }

    fun observePartnerLocation(orderId: String) = livePartnerLocationRepository.observeLiveLocation(orderId)

}