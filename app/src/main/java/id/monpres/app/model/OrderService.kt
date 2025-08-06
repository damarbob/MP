package id.monpres.app.model

import id.monpres.app.enums.OrderStatus
import id.monpres.app.interfaces.IOrder

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

    var partnerId: String? = null,
    var serviceId: String? = null,
    var recurring: Boolean? = null,

    // Scheduled service
    var selectedDateMillis: Double? = null,

    // User
    var selectedLocationLat: Double? = null,
    var selectedLocationLng: Double? = null,
    var vehicle: Vehicle? = null,
    var issue: String? = null,
    var issueDescription: String? = null,
    var imageAttachmentUris: List<String>? = null,
) : IOrder