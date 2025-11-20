package id.monpres.app.notification

import android.Manifest
import android.app.Notification
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
import com.google.firebase.Timestamp
import id.monpres.app.MainActivity // Assuming your main activity
import id.monpres.app.R
import id.monpres.app.enums.OrderStatus // Assuming your OrderStatus enum
import id.monpres.app.enums.OrderStatusType
import id.monpres.app.enums.UserRole
import java.util.Calendar
import java.util.Date
import kotlin.math.absoluteValue

object OrderServiceNotification {

    private const val TAG = "OrderServiceNotification" // For android.util.Log

    // Notification Channel (Android 8.0+)
    private const val ORDER_UPDATES_CHANNEL_ID = "order_service_live_updates_channel_id"
    private const val ORDER_UPDATES_CHANNEL_NAME = "Order Service Updates" // User visible
    private const val ORDER_UPDATES_CHANNEL_DESC =
        "Real-time updates for your ongoing service orders" // User visible

    const val BASE_CHANNEL_ID = "base_channel_id"
    const val BASE_CHANNEL_NAME = "Base Channel" // User visible

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

    // Time to remove closed orders
    private const val HOURS_TO_REMOVE_CLOSED_TYPE_ORDERS = 1

    // Args key
    const val ORDER_ID_KEY = "order_id"

    /**
     * Initializes the notification channel. Call this from Application.onCreate().
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun createNotificationChannel(
        context: Context,
        channelId: String = ORDER_UPDATES_CHANNEL_ID,
        channelName: String = ORDER_UPDATES_CHANNEL_NAME,
        channelDesc: String = ORDER_UPDATES_CHANNEL_DESC,
        importance: Int = NotificationManager.IMPORTANCE_HIGH
    ) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Check if channel exists before creating

        val channel = NotificationChannel(
            channelId,
            channelName,
            importance // Use HIGH for important real-time updates
        ).apply {
            description = channelDesc
            // Configure other channel properties if needed (sound, vibration, etc.)
            // enableVibration(true)
            // setSound(defaultSoundUri, audioAttributes)
        }
        notificationManager.createNotificationChannel(channel)
        Log.d(TAG, "Notification channel created: $channelId")

    }

    /**
     * Shows or updates the ongoing order notification.
     *
     * @param context Context
     * @param orderServiceId The ID of the order.
     * @param orderServiceStatus The status of the order.
     * @param orderServiceUpdatedAt The timestamp when the order was last updated.
     * @param userRole The user's role (customer or partner).
     * @param currentProgress The current progress of the order (optional).
     * @return The notification object.
     */
    fun showOrUpdateNotification(
        context: Context,
        orderServiceId: String?,
        orderServiceStatus: OrderStatus?,
        orderServiceUpdatedAt: Timestamp?,
        userRole: UserRole = UserRole.CUSTOMER,
        currentProgress: Int? = null,
        shortCriticalText: String? = null
    ): Notification? {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }

        if (orderServiceId == null || orderServiceStatus == null || orderServiceUpdatedAt == null) {
            return null
        }

        if (shouldRemoveClosedOrder(orderServiceStatus, orderServiceUpdatedAt)) {
            cancelNotification(context, orderServiceId)
            return null
        }

        val orderState = OrderState.fromOrderStatus(orderServiceStatus) ?: return null
        val notificationId = getNotificationId(orderServiceId)

        val notification = buildNotification(
            context,
            orderState,
            orderServiceId,
            orderServiceUpdatedAt,
            userRole,
            notificationId,
            currentProgress,
            shortCriticalText
        )
        NotificationManagerCompat.from(context).notify(notificationId, notification)
        updateGroupSummary(context)
        return notification
    }

    fun isPostPromotionEnabled(context: Context): Boolean {
        return NotificationManagerCompat.from(context).canPostPromotedNotifications()
    }

    private fun shouldRemoveClosedOrder(
        orderServiceStatus: OrderStatus?,
        orderServiceUpdatedAt: Timestamp?
    ): Boolean {
        if (orderServiceStatus?.type != OrderStatusType.CLOSED) return false

        orderServiceUpdatedAt?.toDate()?.let { updatedAt ->
            val calendar = Calendar.getInstance().apply {
                time = updatedAt
                add(Calendar.HOUR, HOURS_TO_REMOVE_CLOSED_TYPE_ORDERS)
            }
            return Date().after(calendar.time)
        }
        return false
    }

    private fun getNotificationId(orderId: String): Int {
        return orderId.hashCode().absoluteValue
    }

    /**
     * Cancels/dismisses the ongoing order notification.
     * Call this when the order is fully completed (and user acknowledged) or cancelled.
     */
    fun cancelNotification(context: Context, orderId: String) {
        NotificationManagerCompat.from(context)
            .cancel(getNotificationId(orderId))
        Log.d(TAG, "Notification cancelled for order: $orderId (ID: $orderId)")
        updateGroupSummary(context)
    }

    fun cancelAll(context: Context) {
        NotificationManagerCompat.from(context).cancelAll()
    }

    private fun buildNotification(
        context: Context,
        orderState: OrderState,
        orderServiceId: String,
        orderServiceUpdatedAt: Timestamp,
        userRole: UserRole,
        notificationId: Int,
        currentProgress: Int? = null,
        shortCriticalText: String? = null
    ): Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP // These flags bring the existing task to the front without destroying it
            addCategory(Intent.CATEGORY_LAUNCHER)
            putExtra(ORDER_ID_KEY, orderServiceId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE_OPEN_ORDER + notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return orderState.buildNotification(
            context,
            userRole,
            orderServiceUpdatedAt.toDate(),
            pendingIntent,
            currentProgress,
            shortCriticalText
        ).build()
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
                .setContentTitle(context.getString(R.string.multiple_order_updates)) // Customize as needed
                .setContentText("$activeNotifications orders")
                .setSmallIcon(R.drawable.ic_mp_notification) // Replace with your app icon
                .setGroup(ORDER_NOTIFICATION_GROUP_KEY)
                .setGroupSummary(true) // This makes it the summary for the group
                .setStyle(
                    NotificationCompat.InboxStyle()
                        .setSummaryText("$activeNotifications orders")
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

    fun createBaseNotification(context: Context): Notification {
        return NotificationCompat.Builder(context, BASE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mp_notification)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.app_running_in_background))
            .setGroup(ORDER_NOTIFICATION_GROUP_KEY)
//            .setContentIntent(contentIntent)
//            .setOngoing(ongoing)
//            .setAutoCancel(autoCancel)
            .setOnlyAlertOnce(true) // Subsequent updates to the same notification won't make sound/vibrate
//            .setWhen(updatedAt.time)
            .setShowWhen(false)
            .build()
    }

    // --- Enum for defining notification content based on OrderStatus ---
    private enum class OrderState(
        val status: OrderStatus,
        private val titleResId: Int,
        private val customerTextResId: Int,
        private val partnerTextResId: Int,
        private val smallIconResId: Int = R.drawable.ic_mp_notification, // Default small icon
        private val isProgressIndeterminate: Boolean = false,
        private var progressValue: Int? = null, // Null for indeterminate or no progress
        private val progressTrackerIconResId: Int? = null,
        private val largeIconResId: Int? = null,
        private val ongoing: Boolean = false, // Most order updates are ongoing
        private val autoCancel: Boolean = false // Only for final states if needed
    ) {
        ORDER_PLACED(
            OrderStatus.ORDER_PLACED,
            R.string.notification_title_order_placed,
            R.string.notification_text_order_placed,
            R.string.notification_text_order_placed_partner,
            isProgressIndeterminate = true
        ),
        ACCEPTED(
            OrderStatus.ACCEPTED,
            R.string.notification_title_accepted,
            R.string.notification_text_accepted,
            R.string.notification_text_accepted_partner,
            isProgressIndeterminate = true
        ),
        ON_THE_WAY(
            OrderStatus.ON_THE_WAY,
            R.string.notification_title_on_the_way,
            R.string.notification_text_on_the_way,
            R.string.notification_text_on_the_way_partner,
            ongoing = true,
            progressValue = 0, // Example progress
            progressTrackerIconResId = R.drawable.nav_rot_90,
            largeIconResId = R.drawable.navigation_24px
        ),
        REPAIRING(
            OrderStatus.REPAIRING,
            R.string.notification_title_repairing,
            R.string.notification_text_repairing,
            R.string.notification_text_in_progress_partner,
            isProgressIndeterminate = true,
        ),
        REPAIR_COMPLETED( // This might be a temporary state before final completion or payment
            OrderStatus.REPAIRED,
            R.string.notification_title_repaired,
            R.string.notification_text_repaired,
            R.string.notification_text_repaired_partner,
            progressValue = 100,
            progressTrackerIconResId = R.drawable.check_24px,
            largeIconResId = R.drawable.check_24px
        ),
        WAITING_FOR_PAYMENT(
            OrderStatus.WAITING_FOR_PAYMENT,
            R.string.notification_title_waiting_for_payment,
            R.string.notification_text_waiting_for_payment,
            R.string.notification_text_waiting_for_payment_partner,
            isProgressIndeterminate = true // Or specific progress if applicable
        ),
        ORDER_COMPLETED(
            OrderStatus.COMPLETED,
            R.string.notification_title_completed,
            R.string.notification_text_completed,
            R.string.notification_text_completed_partner,
            autoCancel = true  // Consider making it auto-cancel when clicked
        ),
        ORDER_CANCELLED( // Added a cancelled state
            OrderStatus.CANCELLED,
            R.string.notification_title_cancelled,
            R.string.notification_text_cancelled,
            R.string.notification_text_cancelled_partner,
            autoCancel = true
        );

        // Add more states as needed (e.g., PAYMENT_RECEIVED, FAILED)

        fun buildNotification(
            context: Context,
            userRole: UserRole,
            updatedAt: Date,
            contentIntent: PendingIntent,
            progress: Int? = null,
            shortCriticalText: String? = null
        ): NotificationCompat.Builder {
            val textResId =
                if (userRole == UserRole.CUSTOMER) customerTextResId else partnerTextResId

            Log.d(TAG, "Current Progress: $progress")
            progressValue = progress
            val builder = NotificationCompat.Builder(context, ORDER_UPDATES_CHANNEL_ID)
                .setSmallIcon(smallIconResId)
                .setContentTitle(context.getString(titleResId))
                .setContentText(context.getString(textResId))
                .setPriority(NotificationCompat.PRIORITY_HIGH) // Important for ongoing tasks
                .setGroup(ORDER_NOTIFICATION_GROUP_KEY)
                .setContentIntent(contentIntent)
                .setOngoing(ongoing)
                .setAutoCancel(autoCancel)
                .setOnlyAlertOnce(true) // Subsequent updates to the same notification won't make sound/vibrate
                .setWhen(updatedAt.time)
                .setShowWhen(this != ON_THE_WAY)
                .setShortCriticalText(shortCriticalText)
                .setRequestPromotedOngoing(true)
                .apply {
                    setupProgressStyle(context)
                    largeIconResId?.let {
                        setLargeIcon(IconCompat.createWithResource(context, it).toIcon(context))
                    }
                }

            // --- Add Actions based on state ---
            when (this) {
                ORDER_PLACED, ACCEPTED -> {
                    // val cancelIntent = ... create PendingIntent for cancelling
                    // builder.addAction(R.drawable.ic_cancel, context.getString(R.string.action_cancel), cancelPendingIntent)
                }

                WAITING_FOR_PAYMENT -> {
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

        private fun NotificationCompat.Builder.setupProgressStyle(context: Context) {
            Log.d(TAG, "Progress: $progressValue")
            if (progressValue != null || isProgressIndeterminate) {
                val style = NotificationCompat.ProgressStyle()
                style.setProgressSegments(
                    listOf(
                        NotificationCompat.ProgressStyle.Segment(100)
                            .setColor(context.getColor(R.color.md_theme_tertiary))
                    )
                )
                if (isProgressIndeterminate) {
                    style.setProgressIndeterminate(true)
                } else {
                    progressValue?.let { style.setProgress(it) }
                }
                progressTrackerIconResId?.let {
                    style.setProgressTrackerIcon(IconCompat.createWithResource(context, it))
                }

                setStyle(style)
            }
        }

        companion object {
            fun fromOrderStatus(status: OrderStatus?): OrderState? {
                return entries.find { it.status == status }
            }
        }
    }
}
