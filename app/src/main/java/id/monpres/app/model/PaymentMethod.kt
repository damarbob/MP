package id.monpres.app.model

import android.content.Context
import android.os.Parcelable
import androidx.annotation.DrawableRes
import androidx.annotation.RawRes
import id.monpres.app.R
import kotlinx.parcelize.Parcelize

@Parcelize
data class PaymentMethod(
    val id: String,
    val name: String,
    val description: String? = null,
    @param:DrawableRes val iconRes: Int,
    @param:RawRes val guideRes: Int? = null,
) : Parcelable {
    companion object {
        const val CASH_ID = "cash"
        const val QRIS_ID = "qris"

        fun getDefaultPaymentMethods(context: Context): List<PaymentMethod> {
            return listOf(
                PaymentMethod(CASH_ID, context.getString(R.string.cash), "Pay directly to the partner", R.drawable.universal_currency_24px, R.raw.payment_guide_cash),
                PaymentMethod(QRIS_ID, context.getString(R.string.qris), "Scan QR code to pay", R.drawable.qr_code_scanner_24px, R.raw.payment_guide_qris),
            )
        }

        fun getDefaultPaymentMethodById(context: Context, id: String): PaymentMethod? {
            return getDefaultPaymentMethods(context).find { it.id == id }
        }
    }
}
