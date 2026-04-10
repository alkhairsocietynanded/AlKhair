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
    private val storageManager: StorageManager,
    private val workManager: WorkManager
) {

    companion object {
        private const val TAG = "ChatRepoManager"
    }

    /* ============================================================
       📦 SSOT — ROOM (observe local DB)
       ============================================================ */

    fun observeMessagesByGroup(groupId: String, limit: Int = 50): Flow<List<ChatMessage>> =
        localRepo.observeMessagesByGroup(groupId, limit)

    /* ============================================================
       ✍️ SEND MESSAGE — Local First → Background Sync
       ============================================================ */

    suspend fun sendMessage(
        messageText: String,
        groupId: String,
        groupType: String,
        senderId: String,
        senderName: String,
        mediaBytes: ByteArray? = null,
        mimeType: String? = null,
        mediaFileName: String? = null
    ): Result<Unit> {

        // 1️⃣ Upload media via StorageManager.uploadBytes() — bytes already read by Activity
        var resolvedMediaUrl: String? = null
        var resolvedMediaType: String? = null
        var resolvedLocalUri: String? = null
        if (mediaBytes != null && mediaFileName != null) {
            val folder = "chat/$groupId"
            val uniqueName = "${System.currentTimeMillis()}_$mediaFileName"
            val resolvedMime = mimeType ?: "application/octet-stream"
            
            // ✅ Cache locally for instant preview!
            resolvedLocalUri = storageManager.saveBytesToCache(mediaBytes, uniqueName)
            
            storageManager.uploadBytes(
                bytes = mediaBytes,
                folder = folder,
                fileName = uniqueName,
                contentType = resolvedMime
            ).onSuccess { url ->
                resolvedMediaUrl = url
                resolvedMediaType = if (resolvedMime.startsWith("image/")) "image" else "document"
                Log.d(TAG, "Chat media uploaded: $url")
            }.onFailure { e ->
                Log.e(TAG, "Chat media upload failed: ${e.message}")
            }
        }

        val message = ChatMessage(
            id = UUID.randomUUID().toString(),
            senderId = senderId,
            senderName = senderName,
            groupId = groupId,
            groupType = groupType,
            messageText = messageText,
            mediaUrl = resolvedMediaUrl,
            mediaType = resolvedMediaType,
            localUri = resolvedLocalUri,
            updatedAt = System.currentTimeMillis(),
            isSynced = false
        )

        // 2️⃣ Save to Local immediately (UI auto-update via Flow)
        localRepo.insertMessage(message)
        Log.d(TAG, "Message saved locally: ${message.id}")

        // 3️⃣ Schedule Background Upload to Supabase table
        scheduleUploadWorker()

        return Result.success(Unit)
    }

    /* ============================================================
       🔁 SYNC — Fetch new messages from Supabase into local DB
       ============================================================ */

    /**
     * Initial/foreground sync — fetch messages + apply missed deletions (tombstone sync).
     * Called when ChatWindow opens, or swipe-refresh.
     * Handles offline devices catching up on missed delete events.
     */
    suspend fun syncGroupMessages(groupId: String, after: Long): Result<Unit> {
        return try {
            // 1️⃣ Sync new/updated messages (existing logic)
            val result = remoteRepo.getMessagesForGroup(groupId, after)
            result.onSuccess { messages ->
                if (messages.isNotEmpty()) {
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

                        // ✅ Auto-detect if media is already cached locally (e.g., after DB wipe)
                        var cachedUri = msg.localUri
                        if (cachedUri == null && !msg.mediaUrl.isNullOrBlank()) {
                            cachedUri = storageManager.findCachedFile(msg.mediaUrl!!)
                        }

                        msg.copy(
                            isSynced = true, 
                            senderName = user?.name ?: "Unknown",
                            localUri = cachedUri
                        )
                    }
                    localRepo.insertMessages(syncedMessages)
                    Log.d(TAG, "Synced ${syncedMessages.size} messages for group $groupId")
                }
            }

            // 2️⃣ ✅ Tombstone sync — apply missed deletions (offline devices ka fix)
            // after=0L means fetch all deletions; in future can be timestamp-based
            remoteRepo.fetchDeletedMessageIds(groupId, after).onSuccess { deletedIds ->
                if (deletedIds.isNotEmpty()) {
                    Log.d(TAG, "Tombstone: applying ${deletedIds.size} missed deletions for group $groupId")
                    deletedIds.forEach { id -> localRepo.deleteById(id) }
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
     * Handles both INSERT (new message) and DELETE (message removed by anyone) events.
     */
    suspend fun startRealtimeSubscription(groupId: String) {
        try {
            remoteRepo.listenForGroupMessages(groupId).collect { event ->
                when (event) {
                    is com.aewsn.alkhair.data.remote.supabase.ChatRealtimeEvent.MessageInserted -> {
                        val message = event.message
                        Log.d(TAG, "Realtime INSERT: ${message.id}")

                        // Resolve sender name
                        var user = userRepository.getUserByIdOneShot(message.senderId)
                        if (user == null) {
                            supabaseUserRepo.getUserById(message.senderId).onSuccess { remoteUser ->
                                if (remoteUser != null) {
                                    userRepository.insertUser(remoteUser)
                                    user = remoteUser
                                }
                            }
                        }

                        // ✅ Auto-detect if media is already cached locally
                        var cachedUri = message.localUri
                        if (cachedUri == null && !message.mediaUrl.isNullOrBlank()) {
                            cachedUri = storageManager.findCachedFile(message.mediaUrl!!)
                        }

                        val syncedMessage = message.copy(
                            isSynced = true,
                            senderName = user?.name ?: "Unknown",
                            localUri = cachedUri
                        )
                        localRepo.insertMessage(syncedMessage)
                    }

                    is com.aewsn.alkhair.data.remote.supabase.ChatRealtimeEvent.MessageDeleted -> {
                        val deletedId = event.messageId
                        Log.d(TAG, "Realtime DELETE: $deletedId — removing from local DB")
                        // ✅ Doosre devices par bhi local DB se delete karo
                        localRepo.deleteById(deletedId)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception in realtime subscription for $groupId", e)
        }
    }

    /* ============================================================
       🗑️ DELETE MESSAGE — Local First → Remote Sync
       ============================================================ */

    suspend fun deleteMessage(messageId: String): Result<Unit> {
        return try {
            // 1️⃣ Delete from local DB immediately (UI updates via Flow)
            localRepo.deleteById(messageId)
            Log.d(TAG, "Message deleted locally: $messageId")

            // 2️⃣ Delete from Supabase immediately (best effort)
            remoteRepo.deleteMessage(messageId)
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting message $messageId", e)
            Result.failure(e)
        }
    }

    suspend fun clearLocal() {
        localRepo.clearAll()
    }

    /* ============================================================
       ⬇️ DOWNLOAD MEDIA — Download attachment to local cache
       ============================================================ */

    /**
     * Download a chat media attachment (image or document) to app's local cache.
     * Saves to `cacheDir/chat_media/<fileName>` and persists the path in Room
     * so the UI can display it without re-downloading.
     */
    suspend fun downloadMedia(message: ChatMessage): Result<Unit> {
        val mediaUrl = message.mediaUrl
            ?: return Result.failure(Exception("No media URL for message ${message.id}"))

        // Build a safe filename: use the last segment (includes timestamp prefix already)
        val rawSegment = mediaUrl.substringAfterLast("/")
        val safeFileName = if (rawSegment.isNotBlank()) rawSegment else "${message.id}.bin"

        val result = storageManager.downloadPublicFile(
            storageUrl = mediaUrl,
            fileName = safeFileName
        )

        return result.fold(
            onSuccess = { localPath ->
                // Persist localUri in Room — UI observes via Flow and refreshes automatically
                localRepo.updateLocalUri(message.id, localPath)
                Log.d(TAG, "Media saved locally: $localPath")
                Result.success(Unit)
            },
            onFailure = { e ->
                Log.e(TAG, "downloadMedia failed for ${message.id}: ${e.message}")
                Result.failure(e)
            }
        )
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
