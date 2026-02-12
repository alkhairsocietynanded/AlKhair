package com.aewsn.alkhair.data.manager

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import com.aewsn.alkhair.data.local.dao.PendingDeletionDao
import com.aewsn.alkhair.data.local.dao.TimetableDao
import com.aewsn.alkhair.data.manager.base.BaseRepoManager
import com.aewsn.alkhair.data.models.PendingDeletion
import com.aewsn.alkhair.data.models.Timetable
import com.aewsn.alkhair.data.remote.supabase.SupabaseTimetableRepo
import com.aewsn.alkhair.data.worker.TimetableUploadWorker
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TimetableRepoManager @Inject constructor(
    private val localRepo: TimetableDao,
    private val remoteRepo: SupabaseTimetableRepo,
    private val workManager: WorkManager,
    private val pendingDeletionDao: PendingDeletionDao
) : BaseRepoManager<Timetable>() {

    override fun observeLocal(): Flow<List<Timetable>> = localRepo.getAllTimetables()

    override suspend fun insertLocal(items: List<Timetable>) {
        localRepo.insertTimetables(items)
    }

    override suspend fun insertLocal(item: Timetable) {
        localRepo.insertTimetable(item)
    }

    override suspend fun fetchRemoteUpdated(after: Long): List<Timetable> {
        return remoteRepo.getTimetablesUpdatedAfter(after).getOrElse { emptyList() }
    }

    override suspend fun deleteLocally(id: String) {
        localRepo.deleteTimetable(id)
    }
    
    override suspend fun clearLocal() {
        localRepo.clearall()
    }

    suspend fun addTimetable(timetable: Timetable): Result<Timetable> {
        val newId = timetable.id.ifEmpty { UUID.randomUUID().toString() }
        val newTimetable = timetable.copy(
            id = newId,
            updatedAt = System.currentTimeMillis(),
            isSynced = false
        )
        insertLocal(newTimetable)
        scheduleUploadWorker()
        return Result.success(newTimetable)
    }

    suspend fun updateTimetable(timetable: Timetable): Result<Unit> {
        val updatedTimetable = timetable.copy(
            updatedAt = System.currentTimeMillis(),
            isSynced = false
        )
        insertLocal(updatedTimetable)
        scheduleUploadWorker()
        return Result.success(Unit)
    }

    suspend fun deleteTimetable(id: String): Result<Unit> {
        deleteLocally(id)
        val pendingDeletion = PendingDeletion(
            id = id,
            type = "TIMETABLE",
            timestamp = System.currentTimeMillis()
        )
        pendingDeletionDao.insertPendingDeletion(pendingDeletion)
        scheduleUploadWorker()
        return Result.success(Unit)
    }
    
    fun observeTimetableForClass(classId: String): Flow<List<Timetable>> = localRepo.getTimetableForClass(classId)
    
    fun observeTimetableForTeacher(teacherId: String): Flow<List<Timetable>> = localRepo.getTimetableForTeacher(teacherId)

    private fun scheduleUploadWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        
        val workRequest = OneTimeWorkRequestBuilder<TimetableUploadWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .build()
        
        workManager.enqueueUniqueWork(
            "TimetableUpload",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            workRequest
        )
    }
}
