package com.zabibtech.alkhair.data.manager

import android.util.Log
import com.zabibtech.alkhair.data.local.local_repos.LocalFeesRepository
import com.zabibtech.alkhair.data.manager.base.BaseRepoManager
import com.zabibtech.alkhair.data.models.DeletedRecord
import com.zabibtech.alkhair.data.models.FeesModel
import com.zabibtech.alkhair.data.remote.firebase.FirebaseFeesRepository
import com.zabibtech.alkhair.utils.FirebaseRefs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeesRepoManager @Inject constructor(
    private val localRepo: LocalFeesRepository,
    private val remoteRepo: FirebaseFeesRepository
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
       ✍️ WRITE — (Remote First -> Then Local)
       ============================================================ */

    suspend fun createFee(feesModel: FeesModel): Result<Unit> {
        return remoteRepo.saveFee(feesModel)
            .onSuccess { savedFee ->
                // Save to Local immediately with fresh timestamp from Remote
                insertLocal(savedFee)
            }
            .map { }
    }

    suspend fun updateFee(feesModel: FeesModel): Result<Unit> {
        val currentTime = System.currentTimeMillis()

        // ✅ CRITICAL: Map mein 'studentId' bhejna zaroori hai
        // taaki FirebaseRepo 'student_sync_key' ko update/maintain kar sake.
        val updateMap = mapOf(
            "studentId" to feesModel.studentId,
            "studentName" to feesModel.studentName,
            "classId" to feesModel.classId,
            "shift" to feesModel.shift,
            "monthYear" to feesModel.monthYear,
            "baseAmount" to feesModel.baseAmount,
            "paidAmount" to feesModel.paidAmount,
            "discounts" to feesModel.discounts,
            "dueAmount" to feesModel.dueAmount,
            "netFees" to feesModel.netFees,
            "paymentStatus" to feesModel.paymentStatus,
            "paymentDate" to feesModel.paymentDate,
            "remarks" to (feesModel.remarks ?: ""),
            "updatedAt" to currentTime
        )

        return remoteRepo.updateFee(feesModel.id, updateMap)
            .onSuccess {
                // Update Local immediately to reflect changes in UI
                val updatedLocalFee = feesModel.copy(updatedAt = currentTime)
                insertLocal(updatedLocalFee)
            }
    }

    suspend fun deleteFee(id: String): Result<Unit> =
        remoteRepo.deleteFee(id).onSuccess {
            // 1. Delete Locally
            deleteLocally(id)

            // 2. Create Tombstone for Sync
            try {
                val record = DeletedRecord(
                    id = id,
                    type = "fees",
                    timestamp = System.currentTimeMillis()
                )
                FirebaseRefs.deletedRecordsRef.child(id).setValue(record).await()
            } catch (e: Exception) {
                Log.e("FeesRepoManager", "Failed to create delete record", e)
            }
        }

    /* ============================================================
       🔧 LOCAL HELPER OVERRIDES
       ============================================================ */

    override suspend fun insertLocal(items: List<FeesModel>) =
        localRepo.insertFees(items)

    override suspend fun insertLocal(item: FeesModel) =
        localRepo.insertFee(item)

    override suspend fun deleteLocally(id: String) =
        localRepo.deleteFee(id)
    override suspend fun clearLocal() = localRepo.clearAll()
}