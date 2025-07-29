package id.monpres.app.model

data class OrderService(
    var id: String? = null,
    var userId: String? = null,
    var serviceId: String? = null,

    // Service snapshot
    var type: String? = null,
    var name: String? = null,
    var description: String? = null,
    var category: String? = null,
    var price: Double? = null,  // Changed to Double for Firestore compatibility
    var recurring: Boolean? = null,
    var active: Boolean? = null,
    var imageUris: List<String>? = null,

    // Scheduled service
    var selectedDateMillis: Double? = null,

    // System
    var userLocationLat: Double? = null,
    var userLocationLng: Double? = null,
    var searchTokens: List<String>? = null,

    // User
    var selectedLocationLat: Double? = null,
    var selectedLocationLng: Double? = null,
    var address: String? = null,
    var vehicle: Vehicle? = null,
    var issue: String? = null,
    var issueDescription: String? = null,
    var imageAttachmentUris: List<String>? = null,
)