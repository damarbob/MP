package id.monpres.app.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.IconCompat
import id.monpres.app.MainActivity // Assuming your main activity
import id.monpres.app.R
import id.monpres.app.enums.OrderStatus // Assuming your OrderStatus enum

object OrderServiceNotification {

    private const val TAG = "OrderServiceNotif" // For android.util.Log

    // Notification Channel (Android 8.0+)
    const val ORDER_UPDATES_CHANNEL_ID = "order_service_live_updates_channel_id"
    private const val ORDER_UPDATES_CHANNEL_NAME = "Order Service Updates" // User visible
    private const val ORDER_UPDATES_CHANNEL_DESC = "Real-time updates for your ongoing service orders" // User visible

    // Unique ID for this specific notification. If you have multiple ongoing order notifications,
    // you'll need a dynamic ID (e.g., based on orderId). For a single "QuickService" ongoing notification,
    // a fixed ID is fine.
//    const val ONGOING_ORDER_NOTIFICATION_ID = 12345 // More descriptive
    private const val ORDER_NOTIFICATION_GROUP_KEY = "id.monpres.app.ORDER_SERVICE_GROUP"
    private const val GROUP_SUMMARY_NOTIFICATION_ID = 5000 // Fixed ID for the group summary

    // Action request codes (ensure they are unique if you have many actions)
    private const val REQUEST_CODE_OPEN_ORDER = 1
    private const val REQUEST_CODE_CANCEL_ORDER = 2
    private const val REQUEST_CODE_PAY_ORDER = 3

    // Args key
    const val ORDER_ID_KEY = "order_id"


    /**
     * Initializes the notification channel. Call this from Application.onCreate().
     */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Check if channel exists before creating
            if (notificationManager.getNotificationChannel(ORDER_UPDATES_CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    ORDER_UPDATES_CHANNEL_ID,
                    ORDER_UPDATES_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH // Use HIGH for important real-time updates
                ).apply {
                    description = ORDER_UPDATES_CHANNEL_DESC
                    // Configure other channel properties if needed (sound, vibration, etc.)
                    // enableVibration(true)
                    // setSound(defaultSoundUri, audioAttributes)
                }
                notificationManager.createNotificationChannel(channel)
                Log.d(TAG, "Notification channel created: $ORDER_UPDATES_CHANNEL_ID")
            }
        }
    }

    /**
     * Shows or updates the ongoing order notification.
     *
     * @param context Context
     * @param currentOrderStatus The current status of the order.
     * @param orderId Optional: If you need to deep link or pass order-specific data in actions.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun showOrUpdateNotification(
        context: Context,
        orderId: String,
        currentOrderStatus: OrderStatus,
    ) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "POST_NOTIFICATIONS permission not granted. Cannot show notification.")
            // On Android 13+, you need to request this permission at runtime.
            // Consider guiding the user to enable notifications.
            return
        }

        val orderState = OrderState.fromOrderStatus(currentOrderStatus)
        if (orderState == null) {
            Log.e(TAG, "No OrderState mapping found for status: $currentOrderStatus. Cannot show notification.")
            return
        }

        // Generate a unique notification ID for this specific order
        // Using hashCode is simple but ensure it provides sufficient uniqueness for your orderId strings
        // and handle potential collisions if orderIds are very similar or numerous.
        // A more robust approach for a persistent int ID might involve a mapping or a stable hashing algorithm.
        val uniqueNotificationId = orderId.hashCode()

        val notificationBuilder = orderState.buildNotificationLogic(context, orderId)

        // Add a content intent to open the app (e.g., to the order details screen)
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(ORDER_ID_KEY, orderId) // So MainActivity can navigate
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE_OPEN_ORDER + uniqueNotificationId,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        notificationBuilder.setContentIntent(contentPendingIntent)


        // Use NotificationManagerCompat for broader compatibility
        with(NotificationManagerCompat.from(context)) {
            // ONGOING_ORDER_NOTIFICATION_ID is used to update the same notification
            notify(uniqueNotificationId, notificationBuilder.build())
            Log.d(TAG, "Notification shown/updated for status: $currentOrderStatus (ID: $uniqueNotificationId)")
//            updateGroupSummary(context)
        }
    }

    /**
     * Cancels/dismisses the ongoing order notification.
     * Call this when the order is fully completed (and user acknowledged) or cancelled.
     */
    fun cancelNotification(context: Context, orderId: String) {
        val uniqueNotificationId = orderId.hashCode()
        with(NotificationManagerCompat.from(context)) {
            cancel(uniqueNotificationId)
            Log.d(TAG, "Notification cancelled for order: $orderId (ID: $uniqueNotificationId)")
//            updateGroupSummary(context)
        }
    }

    /**
     * Updates or removes the group summary notification.
     * This needs to know which notifications are currently active.
     */
    private fun updateGroupSummary(context: Context) {
        val notificationManager = NotificationManagerCompat.from(context)
        val activeNotifications =
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .activeNotifications
                .filter { it.isGroup && it.groupKey == ORDER_NOTIFICATION_GROUP_KEY && it.id != GROUP_SUMMARY_NOTIFICATION_ID }
                .size


        if (activeNotifications > 1) {
            val summaryNotification = NotificationCompat.Builder(context, ORDER_UPDATES_CHANNEL_ID)
                .setContentTitle("Multiple Order Updates") // Customize as needed
                .setContentText("$activeNotifications active orders")
                .setSmallIcon(R.mipmap.ic_launcher_mp) // Replace with your app icon
                .setGroup(ORDER_NOTIFICATION_GROUP_KEY)
                .setGroupSummary(true) // This makes it the summary for the group
                .setStyle(NotificationCompat.InboxStyle()
                    .setSummaryText("$activeNotifications active orders")
                    // You could add lines for each active order here if you fetch their details
                )
                .setAutoCancel(true)
                .build()
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return
            }
            notificationManager.notify(GROUP_SUMMARY_NOTIFICATION_ID, summaryNotification)
        } else {
            notificationManager.cancel(GROUP_SUMMARY_NOTIFICATION_ID)
        }
    }

    // --- Enum for defining notification content based on OrderStatus ---
    private enum class OrderState(
        val status: OrderStatus,
        val titleResId: Int,
        val textResId: Int,
        val smallIconResId: Int = R.mipmap.ic_launcher_mp, // Default small icon
        val isProgressIndeterminate: Boolean = false,
        val progressValue: Int? = null, // Null for indeterminate or no progress
        val progressMax: Int = 100,
        val progressTrackerIconResId: Int? = null,
        val largeIconResId: Int? = null,
        val ongoing: Boolean = true, // Most order updates are ongoing
        val autoCancel: Boolean = false // Only for final states if needed
    ) {
        ORDER_PLACED(
            OrderStatus.ORDER_PLACED,
            R.string.notification_title_order_placed,
            R.string.notification_text_order_placed,
            isProgressIndeterminate = true
        ),
        ACCEPTED(
            OrderStatus.ACCEPTED,
            R.string.notification_title_order_accepted,
            R.string.notification_text_order_accepted,
            isProgressIndeterminate = true
        ),
        ON_THE_WAY(
            OrderStatus.ON_THE_WAY,
            R.string.notification_title_on_the_way,
            R.string.notification_text_on_the_way,
            progressValue = 25, // Example progress
            progressTrackerIconResId = R.drawable.directions_car_24px,
            largeIconResId = R.drawable.map_24px
        ),
        REPAIRING(
            OrderStatus.IN_PROGRESS,
            R.string.notification_title_repairing,
            R.string.notification_text_repairing,
            progressValue = 75, // Example progress
            progressTrackerIconResId = R.drawable.home_24px,
            largeIconResId = R.drawable.check_24px // Example, maybe a wrench icon
        ),
        REPAIR_COMPLETED( // This might be a temporary state before final completion or payment
            OrderStatus.SHIPPED, // Assuming SHIPPED maps to a repair complete type state
            R.string.notification_title_repair_completed,
            R.string.notification_text_repair_completed,
            progressValue = 100,
            progressTrackerIconResId = R.drawable.check_24px,
            largeIconResId = R.drawable.add_location_alt_24px // Example
        ),
        WAITING_FOR_PAYMENT(
            OrderStatus.ON_HOLD,
            R.string.notification_title_waiting_for_payment,
            R.string.notification_text_waiting_for_payment,
            isProgressIndeterminate = true // Or specific progress if applicable
        ),
        ORDER_COMPLETED(
            OrderStatus.COMPLETED,
            R.string.notification_title_order_completed,
            R.string.notification_text_order_completed,
            progressValue = 100,
            ongoing = false, // Consider making final notification not ongoing
            autoCancel = true  // Consider making it auto-cancel when clicked
        ),
        ORDER_CANCELLED( // Added a cancelled state
            OrderStatus.CANCELLED,
            R.string.notification_title_order_cancelled,
            R.string.notification_text_order_cancelled,
            ongoing = false,
            autoCancel = true
        );
        // Add more states as needed (e.g., PAYMENT_RECEIVED, FAILED)

        @RequiresApi(Build.VERSION_CODES.O) // For ProgressStyle features, adjust if necessary
        fun buildNotificationLogic(context: Context, orderId: String?): NotificationCompat.Builder {
            val builder = NotificationCompat.Builder(context, ORDER_UPDATES_CHANNEL_ID)
                .setSmallIcon(this.smallIconResId)
                .setContentTitle(context.getString(this.titleResId))
                .setContentText(context.getString(this.textResId))
                .setPriority(NotificationCompat.PRIORITY_HIGH) // Important for ongoing tasks
                .setGroup(ORDER_NOTIFICATION_GROUP_KEY)
                .setOngoing(this.ongoing)
                .setAutoCancel(this.autoCancel)
                .setOnlyAlertOnce(true) // Subsequent updates to the same notification won't make sound/vibrate

            // Progress Bar
            if (this.progressValue != null || this.isProgressIndeterminate) {
                // ProgressStyle requires newer APIs
                val progressStyle = NotificationCompat.ProgressStyle()
                progressStyle.setProgressSegments(
                    listOf(NotificationCompat.ProgressStyle.Segment(100)
                        .setColor(context.getColor(R.color.md_theme_secondaryContainer))) // Example color
                )
                if (this.isProgressIndeterminate) {
                    progressStyle.setProgressIndeterminate(true)
                } else if (this.progressValue != null) {
                    progressStyle.setProgress(this.progressValue)
                }
                this.progressTrackerIconResId?.let { iconRes ->
                    progressStyle.setProgressTrackerIcon(IconCompat.createWithResource(context, iconRes))
                }
                builder.setStyle(progressStyle)
            }

            this.largeIconResId?.let {
                builder.setLargeIcon(IconCompat.createWithResource(context, it).toIcon(context))
            }

            // --- Add Actions based on state ---
            when (this) {
                ORDER_PLACED, ACCEPTED -> {
                    // Example: Cancel Action
                    // val cancelIntent = ... create PendingIntent for cancelling
                    // builder.addAction(R.drawable.ic_cancel, context.getString(R.string.action_cancel), cancelPendingIntent)
                }
                WAITING_FOR_PAYMENT -> {
                    // Example: Pay Action
                    // val payIntent = ... create PendingIntent for payment
                    // builder.addAction(R.drawable.ic_payment, context.getString(R.string.action_pay), payIntent)
                }
                ORDER_COMPLETED, ORDER_CANCELLED -> {
                    // Maybe no actions, or a "View Details" action
                }
                // Add actions for other states as needed
                else -> {}
            }
            return builder
        }

        companion object {
            fun fromOrderStatus(status: OrderStatus): OrderState? {
                return entries.find { it.status == status }
            }
        }
    }
}
