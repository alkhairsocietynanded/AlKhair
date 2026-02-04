package com.aewsn.alkhair.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aewsn.alkhair.data.local.dao.PendingDeletionDao
import com.aewsn.alkhair.data.local.local_repos.LocalUserRepository
import com.aewsn.alkhair.data.remote.supabase.SupabaseUserRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class UserUploadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val localUserRepository: LocalUserRepository,
    private val pendingDeletionDao: PendingDeletionDao,
    private val supabaseUserRepository: SupabaseUserRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): androidx.work.ListenableWorker.Result = withContext(Dispatchers.IO) {
        try {
            // Task 1: Upload Unsynced Users
            val unsyncedUsers = localUserRepository.getUnsyncedUsers()
            if (unsyncedUsers.isNotEmpty()) {
                val result = supabaseUserRepository.saveUsersBatch(unsyncedUsers)
                if (result.isSuccess) {
                    localUserRepository.markUsersAsSynced(unsyncedUsers.map { it.uid })
                } else {
                    return@withContext Result.retry()
                }
            }

            // Task 2: Handle Pending Deletions
            val pendingDeletions = pendingDeletionDao.getPendingDeletionsByType("USER")
            if (pendingDeletions.isNotEmpty()) {
                val result = supabaseUserRepository.deleteUsersBatch(pendingDeletions.map { it.id })
                if (result.isSuccess) {
                    pendingDeletionDao.removePendingDeletions(pendingDeletions.map { it.id })
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
