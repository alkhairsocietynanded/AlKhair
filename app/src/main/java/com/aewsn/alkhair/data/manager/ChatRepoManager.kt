package com.aewsn.alkhair.data.manager

import android.util.Log
import com.aewsn.alkhair.data.local.local_repos.LocalChatRepository
import com.aewsn.alkhair.data.local.local_repos.LocalUserRepository
import com.aewsn.alkhair.data.models.ChatMessage
import com.aewsn.alkhair.data.remote.supabase.SupabaseChatRepository
import com.aewsn.alkhair.data.worker.ChatUploadWorker
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import java.util.UUID
import java.util.concurrent.TimeUnit

@Singleton
class ChatRepoManager @Inject constructor(
    private val localRepo: LocalChatRepository,
    private val remoteRepo: SupabaseChatRepository,
    private val userRepository: LocalUserRepository,
    private val supabaseUserRepo: com.aewsn.alkhair.data.remote.supabase.SupabaseUserRepository,
    private val workManager: WorkManager
) {

    companion object {
        private const val TAG = "ChatRepoManager"
    }

    /* ============================================================
       📦 SSOT — ROOM (observe local DB)
       ============================================================ */

    fun observeMessagesByGroup(groupId: String): Flow<List<ChatMessage>> =
        localRepo.observeMessagesByGroup(groupId)

    /* ============================================================
       ✍️ SEND MESSAGE — Local First → Background Sync
       ============================================================ */

    suspend fun sendMessage(
        messageText: String,
        groupId: String,
        groupType: String,
        senderId: String,
        senderName: String
    ): Result<Unit> {
        val message = ChatMessage(
            id = UUID.randomUUID().toString(),
            senderId = senderId,
            senderName = senderName,
            groupId = groupId,
            groupType = groupType,
            messageText = messageText,
            updatedAt = System.currentTimeMillis(),
            isSynced = false
        )

        // 1️⃣ Save to Local immediately (UI will auto-update via Flow)
        localRepo.insertMessage(message)
        Log.d(TAG, "Message saved locally: ${message.id}")

        // 2️⃣ Schedule Background Upload
        scheduleUploadWorker()

        return Result.success(Unit)
    }

    /* ============================================================
       🔁 SYNC — Fetch new messages from Supabase into local DB
       ============================================================ */

    /**
     * Initial/foreground sync — fetch messages for a group after given timestamp
     */
    suspend fun syncGroupMessages(groupId: String, after: Long): Result<Unit> {
        return try {
            val result = remoteRepo.getMessagesForGroup(groupId, after)
            result.onSuccess { messages ->
                if (messages.isNotEmpty()) {
                    // Mark as synced and resolve sender name locally
                    val syncedMessages = messages.map { msg ->
                        var user = userRepository.getUserByIdOneShot(msg.senderId)
                        if (user == null) {
                            supabaseUserRepo.getUserById(msg.senderId).onSuccess { remoteUser ->
                                if (remoteUser != null) {
                                    userRepository.insertUser(remoteUser)
                                    user = remoteUser
                                }
                            }
                        }
                        
                        msg.copy(
                            isSynced = true,
                            senderName = user?.name ?: "Unknown"
                        )
                    }
                    localRepo.insertMessages(syncedMessages)
                    Log.d(TAG, "Synced ${syncedMessages.size} messages for group $groupId")
                }
            }
            result.map { }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing messages for group $groupId", e)
            Result.failure(e)
        }
    }

    /**
     * Start a real-time subscription for the current group.
     * This Flow runs as long as the caller's Job is active.
     */
    suspend fun startRealtimeSubscription(groupId: String) {
        try {
            remoteRepo.listenForGroupMessages(groupId).collect { message ->
                Log.d(TAG, "Realtime: New message received ${message.id}")
                
                // Resolve sender name and mark as synced
                var user = userRepository.getUserByIdOneShot(message.senderId)
                if (user == null) {
                    supabaseUserRepo.getUserById(message.senderId).onSuccess { remoteUser ->
                        if (remoteUser != null) {
                            userRepository.insertUser(remoteUser)
                            user = remoteUser
                        }
                    }
                }
                
                val syncedMessage = message.copy(
                    isSynced = true,
                    senderName = user?.name ?: "Unknown"
                )
                
                localRepo.insertMessage(syncedMessage)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception in realtime subscription for $groupId", e)
        }
    }

    /* ============================================================
       🗑️ CLEAR
       ============================================================ */

    suspend fun clearLocal() {
        localRepo.clearAll()
    }

    /* ============================================================
       ⚙️ WORKER
       ============================================================ */

    private fun scheduleUploadWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val uploadWorkRequest = OneTimeWorkRequestBuilder<ChatUploadWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                androidx.work.WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        workManager.enqueueUniqueWork(
            "ChatUploadWork",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            uploadWorkRequest
        )
    }
}
