package com.aewsn.alkhair.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aewsn.alkhair.data.local.local_repos.LocalChatRepository
import com.aewsn.alkhair.data.remote.supabase.SupabaseChatRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class ChatUploadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val localChatRepository: LocalChatRepository,
    private val supabaseChatRepository: SupabaseChatRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // Upload Unsynced Messages
            val unsyncedMessages = localChatRepository.getUnsyncedMessages()
            if (unsyncedMessages.isNotEmpty()) {
                // IMPORTANT: WhatsApp logic. Update timestamp at the exact moment of successful 
                // server delivery so delayed messages don't get inserted in the middle of history.
                val now = System.currentTimeMillis()
                val updatedMessages = unsyncedMessages.map { 
                    it.copy(updatedAt = now, isSynced = true) 
                }

                val result = supabaseChatRepository.saveMessageBatch(updatedMessages)
                if (result.isSuccess) {
                    // Overwrite the existing local message rows with the new timestamp and synced state
                    localChatRepository.insertMessages(updatedMessages)
                    android.util.Log.d("ChatUploadWorker", "✅ Successfully uploaded and updated timestamps for ${updatedMessages.size} messages in Room")
                } else {
                    android.util.Log.e("ChatUploadWorker", "❌ Upload failed, retrying...")
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
