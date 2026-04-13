package com.aewsn.alkhair.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.aewsn.alkhair.R
import com.aewsn.alkhair.ui.homework.HomeworkActivity

/**
 * Generic Notification Helper — Creates channels and shows notifications.
 * Supports multiple notification types for future extensibility.
 */
object NotificationHelper {

    // Channel IDs
    const val CHANNEL_HOMEWORK = "alkhair_homework"
    const val CHANNEL_ANNOUNCEMENTS = "alkhair_announcements"
    const val CHANNEL_FEES = "alkhair_fees"
    const val CHANNEL_CHAT = "alkhair_chat"
    const val CHANNEL_GENERAL = "alkhair_general"

    /**
     * Create all notification channels. Call this from MyApp.onCreate().
     * Safe to call multiple times — Android ignores duplicate channel creation.
     */
    fun createChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channels = listOf(
            NotificationChannel(
                CHANNEL_HOMEWORK,
                "Homework Updates",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for new homework assignments"
                enableVibration(true)
            },
            NotificationChannel(
                CHANNEL_ANNOUNCEMENTS,
                "Announcements",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "School announcements and notices"
                enableVibration(true)
            },
            NotificationChannel(
                CHANNEL_FEES,
                "Fees Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Fee payment updates and reminders"
            },
            NotificationChannel(
                CHANNEL_CHAT,
                "Chat Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "New messages in groups and classes"
                enableVibration(true)
            },
            NotificationChannel(
                CHANNEL_GENERAL,
                "General",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "General updates from AlKhair"
            }
        )

        manager.createNotificationChannels(channels)
    }

    /**
     * Show a notification with appropriate PendingIntent based on type.
     */
    fun showNotification(
        context: Context,
        type: String,
        title: String,
        body: String,
        data: Map<String, String> = emptyMap()
    ) {
        val channelId = data["channel_id"] ?: CHANNEL_GENERAL
        val notificationId = System.currentTimeMillis().toInt()

        // Use MainActivity for all push clicks to ensure User Session is hydrated before routing
        val intent = Intent(context, com.aewsn.alkhair.ui.main.MainActivity::class.java).apply {
            putExtra("type", type)
        }
        
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        // Put all data extras into the intent
        data.forEach { (key, value) ->
            intent?.putExtra(key, value)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, notification)
    }
}
