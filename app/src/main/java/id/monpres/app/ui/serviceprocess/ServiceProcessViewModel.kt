package id.monpres.app.ui.serviceprocess

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.monpres.app.model.PaymentMethod
import id.monpres.app.repository.AppPreferences
import id.monpres.app.repository.LivePartnerLocationRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ServiceProcessViewModel @Inject constructor(
    private val livePartnerLocationRepository: LivePartnerLocationRepository,
    private val appPreferences: AppPreferences,
    private val application: Application
) : ViewModel() {
    companion object {
        private const val TAG = "ServiceProcessViewModel"
    }

    // This flow simply reflects the user's last chosen preference from DataStore.
    val preferredPaymentMethod: StateFlow<PaymentMethod> = appPreferences.paymentMethodId
        .map { paymentId ->
            PaymentMethod.getDefaultPaymentMethodById(application, paymentId)!!
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = PaymentMethod.getDefaultPaymentMethodById(application, PaymentMethod.CASH_ID)!!
        )

    fun onPaymentMethodSelected(paymentMethod: PaymentMethod) {
        viewModelScope.launch {
            // Update local preferences
            appPreferences.setPaymentMethodId(paymentMethod.id)
        }
    }

    fun observePartnerLocation(orderId: String) =
        livePartnerLocationRepository.observeLiveLocation(orderId)

}