package id.monpres.app.model

import android.os.Parcelable
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
        const val PLATFORM_FEE_NAME = "Platform Fee"
        const val DISTANCE_FEE_NAME = "Distance Fee"
        const val PLATFORM_FEE = 2000.0 // In IDR
        const val DISTANCE_FEE = 1000.0 // In IDR
    }
}
