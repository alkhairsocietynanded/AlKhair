package com.aewsn.alkhair.data.manager

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import com.aewsn.alkhair.data.local.dao.PendingDeletionDao
import com.aewsn.alkhair.data.local.dao.ResultDao
import com.aewsn.alkhair.data.models.Exam
import com.aewsn.alkhair.data.models.PendingDeletion
import com.aewsn.alkhair.data.models.Result
import com.aewsn.alkhair.data.remote.supabase.SupabaseResultRepo
import com.aewsn.alkhair.data.worker.ResultUploadWorker
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class ResultRepoManager @Inject constructor(
    private val localRepo: ResultDao,
    private val remoteRepo: SupabaseResultRepo,
    private val pendingDeletionDao: PendingDeletionDao,
    private val workManager: WorkManager
) {

    // ================== READ (Local) ==================

    fun observeAllExams(): Flow<List<Exam>> = localRepo.getAllExams()

    fun observeResultsForStudent(examId: String, studentId: String): Flow<List<Result>> =
        localRepo.getResultsForStudentRef(examId, studentId)
        
    fun observeResultsForExam(examId: String): Flow<List<Result>> =
        localRepo.getResultsForExam(examId)

    suspend fun getExamById(id: String): Exam? = localRepo.getExamById(id)

    // ================== WRITE (Local + Worker) ==================

    // --- Exams ---
    suspend fun createExam(exam: Exam): kotlin.Result<Exam> {
        val newId = exam.id.ifEmpty { UUID.randomUUID().toString() }
        val newExam = exam.copy(
            id = newId,
            updatedAt = System.currentTimeMillis(),
            isSynced = false
        )
        localRepo.insertExams(listOf(newExam))
        scheduleUploadWorker()
        return kotlin.Result.success(newExam)
    }

    suspend fun updateExam(exam: Exam): kotlin.Result<Unit> {
        val updated = exam.copy(
            updatedAt = System.currentTimeMillis(),
            isSynced = false
        )
        localRepo.insertExams(listOf(updated))
        scheduleUploadWorker()
        return kotlin.Result.success(Unit)
    }

    suspend fun deleteExam(id: String): kotlin.Result<Unit> {
        localRepo.deleteExam(id)
        pendingDeletionDao.insertPendingDeletion(PendingDeletion(id, "EXAM", System.currentTimeMillis()))
        scheduleUploadWorker()
        return kotlin.Result.success(Unit)
    }

    // --- Results ---
    suspend fun saveResult(result: Result): kotlin.Result<Result> {
        val newId = result.id.ifEmpty { UUID.randomUUID().toString() }
        val newResult = result.copy(
            id = newId,
            updatedAt = System.currentTimeMillis(),
            isSynced = false
        )
        localRepo.insertResults(listOf(newResult))
        scheduleUploadWorker()
        return kotlin.Result.success(newResult)
    }
    
    suspend fun saveResults(results: List<Result>): kotlin.Result<Unit> {
        val newResults = results.map { 
             it.copy(
                id = it.id.ifEmpty { UUID.randomUUID().toString() },
                updatedAt = System.currentTimeMillis(),
                isSynced = false
             )
        }
        localRepo.insertResults(newResults)
        scheduleUploadWorker()
        return kotlin.Result.success(Unit)
    }

    suspend fun deleteResult(id: String): kotlin.Result<Unit> {
        localRepo.deleteResult(id)
        pendingDeletionDao.insertPendingDeletion(PendingDeletion(id, "RESULT", System.currentTimeMillis()))
        scheduleUploadWorker()
        return kotlin.Result.success(Unit)
    }

    private fun scheduleUploadWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        
        val workRequest = OneTimeWorkRequestBuilder<ResultUploadWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .build()
        
        workManager.enqueueUniqueWork(
            "ResultUpload",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            workRequest
        )
    }

    // ================== SYNC (Download) ==================

    // 1. Sync All Exams (Global)
    suspend fun syncExams(after: Long): kotlin.Result<Long> {
        return remoteRepo.getExamsUpdatedAfter(after).map { exams ->
            if (exams.isNotEmpty()) {
                localRepo.insertExams(exams)
                exams.maxOf { it.updatedAt }
            } else {
                after
            }
        }
    }

    // 2. Sync Results (Role Based)
    // Teacher/Admin -> All (for now, maybe optimized later)
    // Student -> Only my results
    
    suspend fun syncAllResults(after: Long): kotlin.Result<Long> {
        return remoteRepo.getResultsUpdatedAfter(after).map { results ->
            if (results.isNotEmpty()) {
                localRepo.insertResults(results)
                results.maxOf { it.updatedAt }
            } else {
                after
            }
        }
    }

    suspend fun syncStudentResults(studentId: String, after: Long): kotlin.Result<Long> {
        return remoteRepo.getResultsForStudent(studentId, after).map { results ->
            if (results.isNotEmpty()) {
                localRepo.insertResults(results)
                results.maxOf { it.updatedAt }
            } else {
                after
            }
        }
    }
    
    suspend fun syncExamResults(examId: String): kotlin.Result<Unit> {
        // Fetch snapshot for exam
        return remoteRepo.getResultsForExam(examId).map { results ->
            if (results.isNotEmpty()) {
                 // Replace local results to ensure consistency
                 localRepo.replaceExamResults(examId, results)
            }
        }
    }
    
    suspend fun clearLocal() {
        // No clearAll in ResultDao yet? 
        // Need to add it or do individual deletes? 
        // For now, assume global clear will wipe db file or handle it
        // Or specific logic later.
    }

    // Quiet delete for Sync
    suspend fun deleteExamLocally(id: String) = localRepo.deleteExam(id)
    suspend fun deleteResultLocally(id: String) = localRepo.deleteResult(id)
}
