package id.monpres.app.notification

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import id.monpres.app.enums.OrderStatus
import id.monpres.app.enums.UserRole
import id.monpres.app.model.OrderService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "MyFirebaseMsgService"
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        Log.d(TAG, "From: ${remoteMessage.from}")

        val data = remoteMessage.data
        // Ensure the message is an order update with the required fields
        if (data.isNotEmpty() && data.containsKey("orderId")) {
            Log.d(TAG, "Message data payload: $data")
            handleOrderNotification(data)
        }
    }

    private fun handleOrderNotification(data: Map<String, String>) {
        // Safely extract all required data from the payload
        val orderId = data["orderId"] ?: return
        val orderStatusStr = data["orderStatus"] ?: return
        val orderUpdatedAtStr = data["orderUpdatedAt"] ?: Date().toString()
        val customerId = data["customerId"]
        val partnerId = data["partnerId"]

        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        if (currentUserId == null) {
            Log.w(TAG, "Cannot process notification, user is not logged in.")
            return
        }

        // Determine the user's role for this specific order by comparing IDs
        val userRole = when (currentUserId) {
            customerId -> UserRole.CUSTOMER
            partnerId -> UserRole.PARTNER
            else -> {
                Log.w(TAG, "User $currentUserId is not involved in order $orderId")
                null // Do not show notification if the user is not part of this order
            }
        }

        // If the user doesn't have a role for this order, stop.
        if (userRole == null) {
            return
        }

        // Build the partial OrderService object from the payload data
        val orderService = OrderService(
            id = orderId,
            status = OrderStatus.valueOf(orderStatusStr),
            updatedAt = convertStringToFirebaseTimestamp(orderUpdatedAtStr)
        )

        Log.d(TAG, "Showing notification for order $orderId with role $userRole")
        // Use the fully-fledged notification handler
        OrderServiceNotification.showOrUpdateNotification(
            applicationContext,
            orderService,
            userRole
        )
    }

    /**
     * Called when a new token for the default Firebase project is generated.
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Refreshed token: $token")
        sendRegistrationToServer(token)
    }

    private fun sendRegistrationToServer(token: String?) {
        if (token == null) {
            Log.w(TAG, "sendRegistrationToServer: token is null.")
            return
        }

        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            Log.d(TAG, "User not logged in, can't save FCM token array.")
            return
        }

        val userDocRef = FirebaseFirestore.getInstance().collection("users").document(userId)

        userDocRef.update("fcmTokens", FieldValue.arrayUnion(token))
            .addOnSuccessListener {
                Log.d(TAG, "FCM token successfully added to array for user: $userId")
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Failed to add token with arrayUnion, trying set...", e)
                val data = hashMapOf("fcmTokens" to listOf(token))
                userDocRef.set(data, com.google.firebase.firestore.SetOptions.merge())
                    .addOnSuccessListener {
                        Log.d(TAG, "FCM token array created for user: $userId")
                    }
                    .addOnFailureListener { e2 ->
                        Log.e(TAG, "Error creating/updating FCM token array for user: $userId", e2)
                    }
            }
    }

    private fun convertStringToFirebaseTimestamp(timestampString: String): Timestamp? {
        return try {
            // This format must match the ISO string sent from the Cloud Function
            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())

            // Parse the string into a Date object
            val date = dateFormat.parse(timestampString)

            // Convert the Date object to a Firebase Timestamp
            date?.let { Timestamp(it) }
        } catch (e: Exception) {
            e.printStackTrace()
            null // Return null if parsing fails
        }
    }
}
