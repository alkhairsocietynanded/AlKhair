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
                val result = supabaseChatRepository.saveMessageBatch(unsyncedMessages)
                if (result.isSuccess) {
                    localChatRepository.markAsSynced(unsyncedMessages.map { it.id })
                } else {
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
