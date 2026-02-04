package com.aewsn.alkhair.data.manager

import android.util.Log
import com.aewsn.alkhair.data.local.local_repos.LocalSalaryRepository
import com.aewsn.alkhair.data.manager.base.BaseRepoManager
import com.aewsn.alkhair.data.models.SalaryModel
import com.aewsn.alkhair.data.remote.supabase.SupabaseSalaryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import com.aewsn.alkhair.data.local.dao.PendingDeletionDao
import com.aewsn.alkhair.data.models.PendingDeletion
import com.aewsn.alkhair.data.worker.SalaryUploadWorker
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import kotlinx.coroutines.flow.flowOn
import java.util.concurrent.TimeUnit

@Singleton
class SalaryRepoManager @Inject constructor(
    private val localRepo: LocalSalaryRepository,
    private val remoteRepo: SupabaseSalaryRepository,
    private val workManager: WorkManager,
    private val pendingDeletionDao: PendingDeletionDao,
    private val localUserRepository: com.aewsn.alkhair.data.local.local_repos.LocalUserRepository
) : BaseRepoManager<SalaryModel>() {

    /* ============================================================
       📦 SSOT — LOCAL OBSERVATION
       ============================================================ */
// ... (existing code)

    /* ============================================================
       🔧 LOCAL HELPER OVERRIDES (With Hydration)
       ============================================================ */

    override suspend fun insertLocal(items: List<SalaryModel>) {
        val hydrated = hydrateSalaries(items)
        localRepo.insertSalaries(hydrated)
    }

    override suspend fun insertLocal(item: SalaryModel) {
        val hydrated = hydrateSalaries(listOf(item)).first()
        localRepo.insertSalary(hydrated)
    }

    // 💧 Hydration Logic
    private suspend fun hydrateSalaries(salaries: List<SalaryModel>): List<SalaryModel> {
        if (salaries.isEmpty()) return salaries

        // 1. Collect staff IDs
        val staffIds = salaries.map { it.staffId }.distinct()

        // 2. Bulk fetch Users
        val userMap = localUserRepository.getUsersByIds(staffIds).associateBy { it.uid }

        // 3. Populate Names based on Match
        return salaries.map { salary ->
            val staff = userMap[salary.staffId]
            salary.copy(
                staffName = staff?.name ?: salary.staffName
            )
        }
    }


    /* ============================================================
       📦 SSOT — LOCAL OBSERVATION
       ============================================================ */

    override fun observeLocal(): Flow<List<SalaryModel>> =
        localRepo.getAllSalaries()
            .map { hydrateSalaries(it) }
            .flowOn(kotlinx.coroutines.Dispatchers.IO) // Move to Background

    fun observeFiltered(
        staffId: String?,
        monthYear: String?
    ): Flow<List<SalaryModel>> =
        localRepo.getFilteredSalaries(staffId, monthYear)
            .map { hydrateSalaries(it) }
            .flowOn(kotlinx.coroutines.Dispatchers.IO) // Move to Background

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
        Log.d("SalaryRepoManager", "createSalary called for: ${salary.staffName}")
        // 1. Prepare Local Data
        val newId = salary.id.ifEmpty { java.util.UUID.randomUUID().toString() }
        val currentTime = System.currentTimeMillis()

        val newSalary = salary.copy(
            id = newId,
            netSalary = salary.calculateNet(),
            staffMonth = "${salary.staffId}_${salary.salaryDate}",
            createdAt = currentTime,
            updatedAt = currentTime,
            isSynced = false
        )

        // 2. Insert Local Immediately
        insertLocal(newSalary)
        Log.d("SalaryRepoManager", "Inserted local salary: $newId. Scheduling worker...")

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
        Log.d("SalaryRepoManager", "scheduleUploadWorker called")
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
    // insertLocal is overridden above with hydration logic
    override suspend fun deleteLocally(id: String) = localRepo.deleteSalary(id)
    override suspend fun clearLocal() = localRepo.clearAll()
}