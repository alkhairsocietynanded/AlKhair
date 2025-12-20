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
       📦 SSOT — ROOM (BaseRepoManager Implementation)
       ============================================================ */

    override fun observeLocal(): Flow<List<FeesModel>> =
        localRepo.getAllFees()

    // अगर आपको Specific Filtering DB लेवल पर चाहिए (Optional)
    fun observeFeesForStudent(studentId: String): Flow<List<FeesModel>> =
        localRepo.getFeesByStudentId(studentId)

    /* ============================================================
       🔁 SYNC — BaseRepoManager Implementation
       ============================================================ */

    override suspend fun fetchRemoteUpdated(after: Long): List<FeesModel> =
        remoteRepo.getFeesUpdatedAfter(after).getOrElse { emptyList() }

    override suspend fun insertLocal(items: List<FeesModel>) =
        localRepo.insertFees(items)

    override suspend fun insertLocal(item: FeesModel) =
        localRepo.insertFee(item)

    override suspend fun deleteLocally(id: String) =
        localRepo.deleteFee(id)

    /* ============================================================
       ✍️ WRITE — UI Operations (Remote First -> Then Local)
       ============================================================ */

    /**
     * Create New Fee
     */
    suspend fun createFee(feesModel: FeesModel): Result<Unit> {
        // 1. Firebase में create करें
        return remoteRepo.saveFee(feesModel)
            .onSuccess { savedFee ->
                // 2. Success होने पर Local DB में डालें (SSOT update)
                insertLocal(savedFee)
            }
            .map { } // Result<FeesModel> को Result<Unit> में convert करें
    }

    /**
     * Update Existing Fee
     */
    suspend fun updateFee(feesModel: FeesModel): Result<Unit> {
        // 1. Firebase में update map तैयार करें
        val updateMap = mapOf<String, Any>(
            "studentId" to feesModel.studentId,
            "studentName" to feesModel.studentName,
            "monthYear" to feesModel.monthYear,
            "baseAmount" to feesModel.baseAmount,
            "paidAmount" to feesModel.paidAmount,
            "discounts" to feesModel.discounts,
            "dueAmount" to feesModel.dueAmount,
            "netFees" to feesModel.netFees,
            "paymentStatus" to feesModel.paymentStatus,
            "remarks" to (feesModel.remarks ?: ""),
            "updatedAt" to System.currentTimeMillis()
        )

        // 2. Firebase कॉल
        return remoteRepo.updateFee(feesModel.id, updateMap)
            .onSuccess {
                // 3. Success होने पर Local DB को update करें
                // हम updated timestamp के साथ object save कर रहे हैं
                insertLocal(feesModel.copy(updatedAt = System.currentTimeMillis()))
            }
    }

    /**
     * Delete Fee
     */
    suspend fun deleteFee(id: String): Result<Unit> =
        remoteRepo.deleteFee(id).onSuccess {
            // 1. Local DB से हटाएं
            deleteLocally(id)

            // 2. Tombstone (Deleted Record) बनाएं ताकि सिंक को पता चले
            try {
                FirebaseRefs.deletedRecordsRef.child(id)
                    .setValue(
                        DeletedRecord(
                            id = id,
                            type = "fees",
                            timestamp = System.currentTimeMillis()
                        )
                    ).await()
            } catch (e: Exception) {
                Log.e("FeesRepoManager", "Failed to create delete record", e)
            }
        }
}