package com.aewsn.alkhair.data.remote.fcm

import android.util.Log
import com.aewsn.alkhair.utils.NotificationHelper
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Firebase Cloud Messaging Service for AlKhair.
 * Handles:
 * 1. onNewToken → Register/refresh token with Supabase
 * 2. onMessageReceived → Show push notification via NotificationHelper
 */
@AndroidEntryPoint
class AlKhairMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "AlKhairFCM"
    }

    @Inject
    lateinit var fcmTokenManager: FcmTokenManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Called when a new FCM token is generated (app install, token rotation).
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token: ${token.take(10)}...")

        serviceScope.launch {
            fcmTokenManager.refreshToken(token)
        }
    }

    /**
     * Called when a push notification is received.
     * Data messages are always delivered here.
     * Notification messages are delivered here only when app is in foreground.
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "Message received from: ${remoteMessage.from}")

        // Extract notification data
        val data = remoteMessage.data
        val type = data["type"] ?: "GENERAL"

        // Get title/body from notification payload OR data payload
        val title = remoteMessage.notification?.title
            ?: data["title"]
            ?: "AlKhair Update"

        val body = remoteMessage.notification?.body
            ?: data["body"]
            ?: "You have a new update"

        Log.d(TAG, "Notification: type=$type, title=$title")

        // Show notification using NotificationHelper
        NotificationHelper.showNotification(
            context = applicationContext,
            type = type,
            title = title,
            body = body,
            data = data
        )
    }
}
