package com.aewsn.alkhair.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aewsn.alkhair.data.local.local_repos.LocalChatRepository
import com.aewsn.alkhair.data.remote.supabase.SupabaseChatRepository
import com.aewsn.alkhair.data.manager.StorageManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class ChatUploadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val localChatRepository: LocalChatRepository,
    private val supabaseChatRepository: SupabaseChatRepository,
    private val storageManager: StorageManager
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // Upload Unsynced Messages
            val unsyncedMessages = localChatRepository.getUnsyncedMessages()
            if (unsyncedMessages.isNotEmpty()) {
                
                // 1️⃣ Upload pending media files first
                val readyMessages = mutableListOf<com.aewsn.alkhair.data.models.ChatMessage>()
                val skippedCount = mutableListOf<String>()

                for (msg in unsyncedMessages) {
                    if (msg.mediaUrl == null && msg.localUri != null && msg.mediaType != null) {
                        // This message has a pending file upload
                        try {
                            val localFile = java.io.File(msg.localUri)
                            if (localFile.exists()) {
                                val bytes = localFile.readBytes()
                                val mime = if (msg.mediaType == "image") "image/webp" else "application/octet-stream"
                                val folder = "chat/${msg.groupId}"
                                val uniqueName = localFile.name

                                val uploadResult = storageManager.uploadBytes(bytes, folder, uniqueName, mime)
                                val uploadedUrl = uploadResult.getOrNull()

                                if (uploadedUrl != null) {
                                    readyMessages.add(msg.copy(mediaUrl = uploadedUrl))
                                    android.util.Log.d("ChatUploadWorker", "✅ Media uploaded for ${msg.id}")
                                } else {
                                    // Upload failed — skip this message, retry next time
                                    skippedCount.add(msg.id)
                                    android.util.Log.w("ChatUploadWorker", "⏳ Media upload failed for ${msg.id}, will retry")
                                }
                            } else {
                                // Local file missing — can't upload, skip
                                skippedCount.add(msg.id)
                                android.util.Log.e("ChatUploadWorker", "❌ Local file missing for ${msg.id}: ${msg.localUri}")
                            }
                        } catch (e: Exception) {
                            skippedCount.add(msg.id)
                            android.util.Log.e("ChatUploadWorker", "❌ Error uploading media for ${msg.id}: ${e.message}")
                        }
                    } else {
                        // Text-only message OR media already uploaded — ready to sync
                        readyMessages.add(msg)
                    }
                }

                // 2️⃣ Sync only fully-ready messages
                if (readyMessages.isNotEmpty()) {
                    val now = System.currentTimeMillis()
                    val updatedMessages = readyMessages.map {
                        it.copy(updatedAt = now, isSynced = true)
                    }

                    val result = supabaseChatRepository.saveMessageBatch(updatedMessages)
                    if (result.isSuccess) {
                        localChatRepository.insertMessages(updatedMessages)
                        android.util.Log.d("ChatUploadWorker", "✅ Synced ${updatedMessages.size} messages")
                    } else {
                        android.util.Log.e("ChatUploadWorker", "❌ Batch upload failed, retrying...")
                        return@withContext Result.retry()
                    }
                }

                // 3️⃣ If some messages were skipped (media failed), retry worker
                if (skippedCount.isNotEmpty()) {
                    android.util.Log.w("ChatUploadWorker", "⏳ ${skippedCount.size} messages skipped, scheduling retry")
                    return@withContext Result.retry()
                }
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}
