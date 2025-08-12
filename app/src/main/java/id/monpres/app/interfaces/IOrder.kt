package id.monpres.app.interfaces

import com.google.firebase.Timestamp
import id.monpres.app.enums.OrderStatus

/**
 * Represents an order in the application.
 *
 * This interface defines the properties of an order, including its details,
 * user information, and search-related data.
 */
interface IOrder {
    var id: String?

    // Order snapshot
    var type: String?
    var name: String?
    var description: String?
    var category: String?
    var price: Double?  // Changed to Double for Firestore compatibility
    var status: OrderStatus?
    var imageUris: List<String>?
    val paymentMethod: String?

    // User
    var userId: String?
    var userLocationLat: Double?
    var userLocationLng: Double?
    var userAddress: String?

    var searchTokens: List<String>?

    // Timestamp
    val createdAt: Timestamp?
    val updatedAt: Timestamp?
}