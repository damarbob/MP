package id.monpres.app.model

import android.os.Parcelable
import id.monpres.app.R
import kotlinx.parcelize.Parcelize

@Parcelize
data class OrderItem(
    var id: String? = null,
    var name: String? = null,
    var quantity: Double = 0.0,
    var price: Double = 0.0,
    var isFixed: Boolean = false
): Parcelable {
    val subtotal: Double get() = quantity * price

    companion object {
        const val PLATFORM_FEE_ID = "Platform Fee"
        const val DISTANCE_FEE_ID = "Distance Fee"
        val PLATFORM_FEE_NAME = R.string.platform_fee
        val DISTANCE_FEE_NAME = R.string.distance_fee
        const val PLATFORM_FEE = 2000.0 // In IDR
        const val DISTANCE_FEE = 1000.0 // In IDR
    }
}
