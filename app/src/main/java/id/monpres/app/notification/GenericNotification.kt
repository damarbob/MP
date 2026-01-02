package id.monpres.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import id.monpres.app.MainActivity
import id.monpres.app.R

object GenericNotification {

    const val DESTINATION_FRAGMENT_ID_KEY = "destinationFragmentId"
    const val REQUEST_CODE = 100
    private const val GENERIC_CHANNEL_ID = "generic_channel"
    private const val GENERIC_NOTIFICATION_ID = 9001 // A unique ID for this type

    fun showNotification(context: Context, title: String?, body: String?, destinationFragmentId: Int? = null) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create notification channel for Android O and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                GENERIC_CHANNEL_ID,
                "General Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for general app information"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP // These flags bring the existing task to the front without destroying it
            addCategory(Intent.CATEGORY_LAUNCHER)
            putExtra(DESTINATION_FRAGMENT_ID_KEY, destinationFragmentId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = NotificationCompat.Builder(context, GENERIC_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mp_notification) // Use your app's notification icon
            .setContentTitle(title ?: "Monpres")
            .setContentText(body ?: context.getString(R.string.notification))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true) // Dismiss notification on tap
            .setContentIntent(pendingIntent)

        notificationManager.notify(GENERIC_NOTIFICATION_ID, notificationBuilder.build())
    }
}
