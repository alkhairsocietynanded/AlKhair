package com.aewsn.alkhair.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.aewsn.alkhair.data.local.dao.PendingDeletionDao
import com.aewsn.alkhair.data.local.dao.ResultDao
import com.aewsn.alkhair.data.remote.supabase.SupabaseResultRepo
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class ResultUploadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val resultDao: ResultDao,
    private val pendingDeletionDao: PendingDeletionDao,
    private val remoteRepo: SupabaseResultRepo
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): ListenableWorker.Result {
        return try {
            // 1. Pending Deletions
            val deletions = pendingDeletionDao.getAllPendingDeletions()
            deletions.forEach { pending ->
                if (pending.type == "EXAM") {
                    val res = remoteRepo.deleteExam(pending.id)
                    if (res.isSuccess) pendingDeletionDao.deletePendingDeletion(pending)
                } else if (pending.type == "RESULT") {
                    val res = remoteRepo.deleteResult(pending.id)
                    if (res.isSuccess) pendingDeletionDao.deletePendingDeletion(pending)
                }
            }

            // 2. Upload Exams
            val unsyncedExams = resultDao.getUnsyncedExams()
            if (unsyncedExams.isNotEmpty()) {
                unsyncedExams.forEach { exam ->
                    val res = remoteRepo.upsertExam(exam)
                    if (res.isSuccess) {
                        resultDao.markExamsAsSynced(listOf(exam.id))
                    }
                }
            }

            // 3. Upload Results
            val unsyncedResults = resultDao.getUnsyncedResults()
            if (unsyncedResults.isNotEmpty()) {
                unsyncedResults.forEach { result ->
                    val res = remoteRepo.upsertResult(result)
                    if (res.isSuccess) {
                        resultDao.markResultsAsSynced(listOf(result.id))
                    }
                }
            }

            ListenableWorker.Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            ListenableWorker.Result.retry()
        }
    }
}
