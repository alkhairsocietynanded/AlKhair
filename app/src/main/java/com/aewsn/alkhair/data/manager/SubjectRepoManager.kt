package com.aewsn.alkhair.data.manager

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import com.aewsn.alkhair.data.local.dao.PendingDeletionDao
import com.aewsn.alkhair.data.local.dao.SubjectDao
import com.aewsn.alkhair.data.manager.base.BaseRepoManager
import com.aewsn.alkhair.data.models.PendingDeletion
import com.aewsn.alkhair.data.models.Subject
import com.aewsn.alkhair.data.remote.supabase.SupabaseSubjectRepo
import com.aewsn.alkhair.data.worker.SubjectUploadWorker
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubjectRepoManager @Inject constructor(
    private val localRepo: SubjectDao,
    private val remoteRepo: SupabaseSubjectRepo,
    private val workManager: WorkManager,
    private val pendingDeletionDao: PendingDeletionDao
) : BaseRepoManager<Subject>() {

    override fun observeLocal(): Flow<List<Subject>> = localRepo.getAllSubjects()

    override suspend fun insertLocal(items: List<Subject>) {
        localRepo.insertSubjects(items)
    }

    override suspend fun insertLocal(item: Subject) {
        localRepo.insertSubject(item)
    }

    override suspend fun fetchRemoteUpdated(after: Long): List<Subject> {
        return remoteRepo.getSubjectsUpdatedAfter(after).getOrElse { emptyList() }
    }

    override suspend fun deleteLocally(id: String) {
        localRepo.deleteSubject(id)
    }
    
    override suspend fun clearLocal() {
        localRepo.clearall()
    }

    suspend fun addSubject(subject: Subject): Result<Subject> {
        val newId = subject.id.ifEmpty { UUID.randomUUID().toString() }
        val newSubject = subject.copy(
            id = newId,
            updatedAt = System.currentTimeMillis(),
            isSynced = false
        )
        insertLocal(newSubject)
        scheduleUploadWorker()
        return Result.success(newSubject)
    }

    suspend fun updateSubject(subject: Subject): Result<Unit> {
        val updatedSubject = subject.copy(
            updatedAt = System.currentTimeMillis(),
            isSynced = false
        )
        insertLocal(updatedSubject)
        scheduleUploadWorker()
        return Result.success(Unit)
    }

    suspend fun deleteSubject(id: String): Result<Unit> {
        deleteLocally(id)
        val pendingDeletion = PendingDeletion(
            id = id,
            type = "SUBJECT",
            timestamp = System.currentTimeMillis()
        )
        pendingDeletionDao.insertPendingDeletion(pendingDeletion)
        scheduleUploadWorker()
        return Result.success(Unit)
    }

    private fun scheduleUploadWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        
        val workRequest = OneTimeWorkRequestBuilder<SubjectUploadWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .build()
        
        workManager.enqueueUniqueWork(
            "SubjectUpload",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            workRequest
        )
    }
}
