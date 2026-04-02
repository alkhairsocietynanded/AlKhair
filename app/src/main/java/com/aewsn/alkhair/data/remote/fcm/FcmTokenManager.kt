package com.aewsn.alkhair.data.remote.fcm

import android.os.Build
import android.util.Log
import com.aewsn.alkhair.data.datastore.AppDataStore
import com.aewsn.alkhair.data.remote.supabase.SupabaseFcmTokenRepository
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages FCM token lifecycle:
 * - Register token on login / app start
 * - Unregister token on logout
 * - Caches token locally in AppDataStore
 */
@Singleton
class FcmTokenManager @Inject constructor(
    private val appDataStore: AppDataStore,
    private val fcmTokenRepository: SupabaseFcmTokenRepository
) {
    companion object {
        private const val TAG = "FcmTokenManager"
        private const val KEY_FCM_TOKEN = "cached_fcm_token"
    }

    /**
     * Get current FCM token and upsert it to Supabase.
     * Called after login and on app start (when user is authenticated).
     */
    suspend fun registerToken(userId: String) {
        try {
            val token = FirebaseMessaging.getInstance().token.await()
            Log.d(TAG, "FCM Token obtained: ${token.take(10)}...")

            // Cache locally
            appDataStore.saveString(KEY_FCM_TOKEN, token)

            // Upsert to Supabase
            val deviceInfo = "${Build.MANUFACTURER} ${Build.MODEL}"
            val result = fcmTokenRepository.upsertToken(userId, token, deviceInfo)

            result.onSuccess {
                Log.d(TAG, "✅ FCM token registered for user: $userId")
            }.onFailure { e ->
                Log.e(TAG, "⚠️ FCM token upload failed (non-fatal)", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting/registering FCM token", e)
        }
    }

    /**
     * Remove FCM token from Supabase and delete local token.
     * Called before logout.
     */
    suspend fun unregisterToken(userId: String) {
        try {
            // Get cached token
            val cachedToken = appDataStore.getString(KEY_FCM_TOKEN)

            if (cachedToken.isNotEmpty()) {
                // Delete from Supabase
                fcmTokenRepository.deleteToken(userId, cachedToken)
                Log.d(TAG, "FCM token removed from Supabase for user: $userId")
            }

            // Clear local cache
            appDataStore.clearKey(KEY_FCM_TOKEN)

            // Delete Firebase token (generates a new one on next app start)
            FirebaseMessaging.getInstance().deleteToken().await()
            Log.d(TAG, "✅ FCM token unregistered successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering FCM token (non-fatal)", e)
        }
    }

    /**
     * Re-register token with Supabase (e.g., when onNewToken fires).
     * Gets userId from AppDataStore login state.
     */
    suspend fun refreshToken(newToken: String) {
        try {
            // Save new token locally
            appDataStore.saveString(KEY_FCM_TOKEN, newToken)

            // Try to get logged-in user ID
            val userId = appDataStore.getString("current_user_uid")
            if (userId.isNotEmpty()) {
                val deviceInfo = "${Build.MANUFACTURER} ${Build.MODEL}"
                fcmTokenRepository.upsertToken(userId, newToken, deviceInfo)
                Log.d(TAG, "✅ FCM token refreshed for user: $userId")
            } else {
                Log.d(TAG, "No logged-in user, token cached locally only")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing FCM token", e)
        }
    }
}
