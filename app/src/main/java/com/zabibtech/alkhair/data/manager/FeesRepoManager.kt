package com.zabibtech.alkhair.data.manager

import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.zabibtech.alkhair.data.local.dao.PendingDeletionDao
import com.zabibtech.alkhair.data.local.local_repos.LocalFeesRepository
import com.zabibtech.alkhair.data.manager.base.BaseRepoManager
import com.zabibtech.alkhair.data.models.FeesModel
import com.zabibtech.alkhair.data.models.PendingDeletion
import com.zabibtech.alkhair.data.remote.supabase.SupabaseFeesRepository
import com.zabibtech.alkhair.data.worker.FeesUploadWorker
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeesRepoManager @Inject constructor(
    private val localRepo: LocalFeesRepository,
    private val remoteRepo: SupabaseFeesRepository,
    private val pendingDeletionDao: PendingDeletionDao,
    private val workManager: WorkManager,
    private val localUserRepository: com.zabibtech.alkhair.data.local.local_repos.LocalUserRepository
) : BaseRepoManager<FeesModel>() {



    /* ============================================================
       📦 READ — SSOT (Flow from Room)
       ============================================================ */

    // Admin/Teacher View (All Fees)
    override fun observeLocal(): Flow<List<FeesModel>> =
        localRepo.getAllFees()

    // Student View (My Fees)
    fun observeFeesForStudent(studentId: String): Flow<List<FeesModel>> =
        localRepo.getFeesByStudentId(studentId)

    /* ============================================================
       🔁 SYNC LOGIC (Optimized)
       ============================================================ */

    // 1. GLOBAL SYNC (For Admin) - Fetches updates for ALL students
    override suspend fun fetchRemoteUpdated(after: Long): List<FeesModel> =
        remoteRepo.getFeesUpdatedAfter(after).getOrElse {
            Log.e("FeesRepoManager", "Global sync failed", it)
            emptyList()
        }

    // 2. TARGETED SYNC (For Student) - Fetches only THEIR updates using Composite Key
    suspend fun syncStudentFees(studentId: String, lastSync: Long): Result<Unit> {
        return remoteRepo.getFeesForStudentUpdatedAfter(studentId, lastSync)
            .onSuccess { list ->
                if (list.isNotEmpty()) {
                    insertLocal(list)
                    Log.d("FeesRepoManager", "Synced ${list.size} fees for student $studentId")
                }
            }
            .map { }
    }

    // 3. Class Sync (For Teacher)
    suspend fun syncClassFees(classId: String, shift: String, lastSync: Long): Result<Unit> {
        return remoteRepo.getFeesForClassAndShiftUpdatedAfter(classId, shift, lastSync)
            .onSuccess { list ->
                if (list.isNotEmpty()) insertLocal(list)
            }
            .map { }
    }
    /* ============================================================
       ✍️ WRITE — (Local First -> Background Sync)
       ============================================================ */

    suspend fun createFee(feesModel: FeesModel): Result<Unit> {
        val newId = feesModel.id.ifEmpty { UUID.randomUUID().toString() }
        val feeWithId = feesModel.copy(
            id = newId,
            isSynced = false,
            updatedAt = System.currentTimeMillis()
        )
        insertLocal(feeWithId)
        scheduleUploadWorker()
        return Result.success(Unit)
    }

    suspend fun updateFee(feesModel: FeesModel): Result<Unit> {
        val updatedFee = feesModel.copy(
            isSynced = false,
            updatedAt = System.currentTimeMillis()
        )
        insertLocal(updatedFee)
        scheduleUploadWorker()
        return Result.success(Unit)
    }

    suspend fun deleteFee(id: String): Result<Unit> {
        deleteLocally(id)
        val pendingDeletion = PendingDeletion(
            id = id,
            type = "FEES",
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

        val uploadWorkRequest = OneTimeWorkRequestBuilder<FeesUploadWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                androidx.work.WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        workManager.enqueueUniqueWork(
            "FeesUploadWork",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            uploadWorkRequest
        )
    }

    /* ============================================================
       🔧 LOCAL HELPER OVERRIDES (With Hydration)
       ============================================================ */

    override suspend fun insertLocal(items: List<FeesModel>) {
        val hydrated = hydrateFees(items)
        localRepo.insertFees(hydrated)
    }

    override suspend fun insertLocal(item: FeesModel) {
        val hydrated = hydrateFees(listOf(item)).first()
        localRepo.insertFee(hydrated)
    }

    override suspend fun deleteLocally(id: String) =
        localRepo.deleteFee(id)
    override suspend fun clearLocal() = localRepo.clearAll()
    
    // 💧 Hydration Logic
    private suspend fun hydrateFees(fees: List<FeesModel>): List<FeesModel> {
        if (fees.isEmpty()) return fees

        // 1. Collect student IDs
        val studentIds = fees.map { it.studentId }.distinct()

        // 2. Bulk fetch Users
        val userMap = localUserRepository.getUsersByIds(studentIds).associateBy { it.uid }

        // 3. Populate Names/Shifts
        return fees.map { fee ->
            val student = userMap[fee.studentId]
            fee.copy(
                studentName = student?.name ?: fee.studentName,
                shift = if (fee.shift.isBlank()) student?.shift ?: "" else fee.shift
            )
        }
    }
}