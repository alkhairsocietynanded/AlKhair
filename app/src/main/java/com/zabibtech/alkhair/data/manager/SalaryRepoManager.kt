package com.zabibtech.alkhair.data.manager

import android.util.Log
import com.zabibtech.alkhair.data.local.local_repos.LocalSalaryRepository
import com.zabibtech.alkhair.data.manager.base.BaseRepoManager
import com.zabibtech.alkhair.data.models.DeletedRecord
import com.zabibtech.alkhair.data.models.SalaryModel
import com.zabibtech.alkhair.data.remote.firebase.FirebaseSalaryRepository
import com.zabibtech.alkhair.utils.FirebaseRefs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton
import com.zabibtech.alkhair.data.local.dao.PendingDeletionDao
import com.zabibtech.alkhair.data.models.PendingDeletion
import com.zabibtech.alkhair.data.worker.SalaryUploadWorker
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit  
import androidx.work.OneTimeWorkRequest

@Singleton
class SalaryRepoManager @Inject constructor(
    private val localRepo: LocalSalaryRepository,
    private val remoteRepo: FirebaseSalaryRepository,
    private val workManager: WorkManager,
    private val pendingDeletionDao: PendingDeletionDao
) : BaseRepoManager<SalaryModel>() {

    /* ============================================================
       📦 SSOT — LOCAL OBSERVATION
       ============================================================ */

    override fun observeLocal(): Flow<List<SalaryModel>> =
        localRepo.getAllSalaries()

    fun observeFiltered(
        staffId: String?,
        monthYear: String?
    ): Flow<List<SalaryModel>> =
        localRepo.getFilteredSalaries(staffId, monthYear)

    /* ============================================================
       🔁 SYNC LOGIC (Optimized)
       ============================================================ */

    // 1. Global Sync (Admin)
    override suspend fun fetchRemoteUpdated(after: Long): List<SalaryModel> =
        remoteRepo.getSalariesUpdatedAfter(after).getOrElse { emptyList() }

    // 2. Staff Targeted Sync (Teacher) - ✅ New Optimization
    suspend fun syncStaffSalary(staffId: String, lastSync: Long): Result<Unit> {
        return remoteRepo.getSalariesForStaffUpdatedAfter(staffId, lastSync)
            .onSuccess { list ->
                if (list.isNotEmpty()) insertLocal(list)
            }
            .map { }
    }

    /* ============================================================
       ✍️ WRITE — (Local First -> Background Sync)
       ============================================================ */

    suspend fun createSalary(salary: SalaryModel): Result<Unit> {
        // 1. Prepare Local Data
        val newId = salary.id.ifEmpty { java.util.UUID.randomUUID().toString() }
        val currentTime = System.currentTimeMillis()
        
        val newSalary = salary.copy(
            id = newId,
            netSalary = salary.calculateNet(),
            staffMonth = "${salary.staffId}_${salary.monthYear}",
            createdAt = currentTime,
            updatedAt = currentTime,
            isSynced = false
        )

        // 2. Insert Local Immediately
        insertLocal(newSalary)
        
        // 3. Schedule Background Sync
        scheduleUploadWorker()
        
        return Result.success(Unit)
    }

    suspend fun updateSalary(salary: SalaryModel): Result<Unit> {
        val currentTime = System.currentTimeMillis()

        // 1. Prepare Local Update
        val updatedLocalSalary = salary.copy(
            updatedAt = currentTime,
            netSalary = salary.calculateNet(),
            isSynced = false
        )
        
        // 2. Insert Local Immediately
        insertLocal(updatedLocalSalary)
        
        // 3. Schedule Background Sync
        scheduleUploadWorker()
        
        return Result.success(Unit)
    }

    suspend fun deleteSalary(id: String): Result<Unit> {
        // 1. Delete Local
        deleteLocally(id)
        
        // 2. Mark for deletion
        val pendingDeletion = PendingDeletion(
            id = id,
            type = "SALARY",
            timestamp = System.currentTimeMillis()
        )
        pendingDeletionDao.insertPendingDeletion(pendingDeletion)
        
        // 3. Schedule Upload Worker
        scheduleUploadWorker()
        
        return Result.success(Unit)
    }
    
    private fun scheduleUploadWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val uploadWorkRequest = OneTimeWorkRequestBuilder<SalaryUploadWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                androidx.work.WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        workManager.enqueueUniqueWork(
            "SalaryUploadWork",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            uploadWorkRequest
        )
    }

    // Base Implementations (Ensure these methods exist in LocalRepo)
    override suspend fun insertLocal(items: List<SalaryModel>) = localRepo.insertSalaries(items)
    override suspend fun insertLocal(item: SalaryModel) = localRepo.insertSalary(item)
    override suspend fun deleteLocally(id: String) = localRepo.deleteSalary(id)
    override suspend fun clearLocal() = localRepo.clearAll()
}