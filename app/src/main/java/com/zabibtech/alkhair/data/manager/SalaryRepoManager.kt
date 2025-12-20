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

@Singleton
class SalaryRepoManager @Inject constructor(
    private val localRepo: LocalSalaryRepository,
    private val remoteRepo: FirebaseSalaryRepository
) : BaseRepoManager<SalaryModel>() {

    /* ============================================================
       📦 SSOT — LOCAL OBSERVATION (UI / ViewModel)
       ============================================================ */

    override fun observeLocal(): Flow<List<SalaryModel>> {
        return localRepo.getAllSalaries()
    }

    fun observeFiltered(
        staffId: String?,
        monthYear: String?
    ): Flow<List<SalaryModel>> {
        return localRepo.getFilteredSalaries(staffId, monthYear)
    }

    /* ============================================================
       🔁 SYNC — USED ONLY BY AppDataSyncManager
       ============================================================ */

    override suspend fun fetchRemoteUpdated(after: Long): List<SalaryModel> {
        return remoteRepo.getSalariesUpdatedAfter(after)
            .getOrElse {
                Log.e("SalaryRepoManager", "Remote sync failed", it)
                emptyList()
            }
    }

    override suspend fun insertLocal(items: List<SalaryModel>) {
        try {
            localRepo.insertSalaries(items)
        } catch (e: Exception) {
            Log.e("SalaryRepoManager", "Local bulk insert failed", e)
        }
    }

    override suspend fun insertLocal(item: SalaryModel) {
        try {
            localRepo.insertSalary(item)
        } catch (e: Exception) {
            Log.e("SalaryRepoManager", "Local insert failed", e)
        }
    }

    override suspend fun deleteLocally(id: String) {
        try {
            localRepo.deleteSalary(id)
        } catch (e: Exception) {
            Log.e("SalaryRepoManager", "Local delete failed for $id", e)
        }
    }

    /* ============================================================
       ✍️ WRITE — FIREBASE → ROOM (UI ACTIONS)
       ============================================================ */

    suspend fun createSalary(salary: SalaryModel): Result<Unit> {
        return try {
            remoteRepo.createSalary(salary).onSuccess { created ->
                insertLocal(created)
            }.map { }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateSalary(salary: SalaryModel): Result<Unit> {
        val updateMap = mapOf<String, Any>(
            "basicSalary" to salary.basicSalary,
            "allowances" to salary.allowances,
            "deductions" to salary.deductions,
            "netSalary" to salary.calculateNet(),
            "paymentStatus" to salary.paymentStatus,
            "paymentDate" to (salary.paymentDate ?: ""),
            "remarks" to (salary.remarks ?: ""),
            "updatedAt" to System.currentTimeMillis()
        )

        return try {
            remoteRepo.updateSalary(salary.id, updateMap)
                .onSuccess {
                    remoteRepo.getSalaryById(salary.id)
                        .onSuccess { insertLocal(it) }
                }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteSalary(id: String): Result<Unit> {
        return try {
            remoteRepo.deleteSalary(id).onSuccess {

                // 1️⃣ Local delete
                deleteLocally(id)

                // 2️⃣ Global deletion log for sync
                FirebaseRefs.deletedRecordsRef
                    .child(id)
                    .setValue(
                        DeletedRecord(
                            id = id,
                            type = "salary",
                            timestamp = System.currentTimeMillis()
                        )
                    ).await()
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
