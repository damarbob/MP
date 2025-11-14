package id.monpres.app.model

import android.os.Parcelable
import com.google.firebase.Timestamp
import id.monpres.app.enums.OrderStatus
import id.monpres.app.interfaces.IOrder
import kotlinx.parcelize.Parcelize

@Parcelize
data class OrderService(
    override var id: String? = null,
    override var userId: String? = null,
    override var type: String? = null,
    override var name: String? = null,
    override var description: String? = null,
    override var category: String? = null,
    override var price: Double? = null, // Changed to Double for Firestore compatibility
    override var status: OrderStatus? = null,
    override var imageUris: List<String>? = null,
    override var userLocationLat: Double? = null,
    override var userLocationLng: Double? = null,
    override var userAddress: String? = null,
    override var searchTokens: List<String>? = null,
    override val paymentMethod: String? = null,
    override var createdAt: Timestamp? = null,
    override var updatedAt: Timestamp? = null,

    var partnerId: String? = null,
    var partner: MontirPresisiUser? = null, // Denormalized partner data
    var serviceId: String? = null,
    var recurring: Boolean? = null,

    // Scheduled service
    var selectedDateMillis: Double? = null,

    // User
    var user: MontirPresisiUser? = null, // Denormalized user data
    var selectedLocationLat: Double? = null,
    var selectedLocationLng: Double? = null,
    var vehicle: Vehicle? = null,
    var issue: String? = null,
    var issueDescription: String? = null,
    var imageAttachmentUris: List<String>? = null,

    var orderItems: List<OrderItem>? = null,
) : IOrder, Parcelable {
    companion object {
        const val COLLECTION = "orderServices"
        const val PARTNER_ID = "partnerId"

        fun List<OrderService>.filterByStatus(status: OrderStatus) = filter { it.status == status }
        fun List<OrderService>.filterByStatuses(statuses: List<OrderStatus>) = filter { statuses.contains(it.status) }
        fun List<OrderService>.filterByPartnerId(partnerId: String) = filter { it.partnerId == partnerId }

        fun getPriceFromOrderItems(orderItems: List<OrderItem>?): Double {
            var price = 0.0
            orderItems?.forEach { item ->
                price += item.subtotal
            }
            return price

        }
    }
}