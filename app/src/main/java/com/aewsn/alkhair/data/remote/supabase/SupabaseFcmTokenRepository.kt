package com.aewsn.alkhair.data.remote.supabase

import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class FcmTokenRecord(
//    val id: String? = null,
    @SerialName("user_id")
    val userId: String,
    @SerialName("fcm_token")
    val fcmToken: String,
    @SerialName("device_info")
    val deviceInfo: String? = null
)

/**
 * Supabase CRUD for user_fcm_tokens table.
 * Handles multi-device token storage.
 */
@Singleton
class SupabaseFcmTokenRepository @Inject constructor(
    private val supabase: SupabaseClient
) {
    companion object {
        private const val TAG = "SupabaseFcmTokenRepo"
        private const val TABLE = "user_fcm_tokens"
    }

    /**
     * Upsert FCM token for a user (handles multi-device via unique constraint)
     */
    suspend fun upsertToken(userId: String, token: String, deviceInfo: String?): Result<Unit> {
        return try {
            val record = FcmTokenRecord(
                userId = userId,
                fcmToken = token,
                deviceInfo = deviceInfo
            )

            supabase.from(TABLE).upsert(record) {
                onConflict = "user_id,fcm_token"
            }

            Log.d(TAG, "FCM token upserted successfully for user: $userId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error upserting FCM token", e)
            Result.failure(e)
        }
    }

    /**
     * Delete a specific FCM token for a user (single device logout)
     */
    suspend fun deleteToken(userId: String, token: String): Result<Unit> {
        return try {
            supabase.from(TABLE).delete {
                filter {
                    eq("user_id", userId)
                    eq("fcm_token", token)
                }
            }
            Log.d(TAG, "FCM token deleted for user: $userId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting FCM token", e)
            Result.failure(e)
        }
    }
}
