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

    override fun observeLocal(): Flow<List<SalaryModel>> =
        localRepo.getAllSalaries()

    fun observeFiltered(
        staffId: String?,
        monthYear: String?
    ): Flow<List<SalaryModel>> =
        localRepo.getFilteredSalaries(staffId, monthYear)

    /* ============================================================
       🔁 SYNC — USED BY AppDataSyncManager
       ============================================================ */

    override suspend fun fetchRemoteUpdated(after: Long): List<SalaryModel> =
        remoteRepo.getSalariesUpdatedAfter(after).getOrElse { emptyList() }

    // Remove try-catch here so failures are reported to AppDataSyncManager
    override suspend fun insertLocal(items: List<SalaryModel>) =
        localRepo.insertSalaries(items)

    override suspend fun insertLocal(item: SalaryModel) =
        localRepo.insertSalary(item)

    override suspend fun deleteLocally(id: String) =
        localRepo.deleteSalary(id)

    /* ============================================================
       ✍️ WRITE — FIREBASE → ROOM (UI ACTIONS)
       ============================================================ */

    suspend fun createSalary(salary: SalaryModel): Result<Unit> {
        // 1. Save to Remote
        return remoteRepo.createSalary(salary)
            .onSuccess { createdSalary ->
                // 2. Save to Local
                insertLocal(createdSalary)
            }
            .map { }
    }

    suspend fun updateSalary(salary: SalaryModel): Result<Unit> {
        // Prepare the updated object with a fresh timestamp
        val currentTime = System.currentTimeMillis()

        // Note: Assuming salary.netSalary is already calculated in the UI/ViewModel
        // If not, calculate it here: val net = salary.basicSalary + ...

        val updateMap = mapOf(
            "basicSalary" to salary.basicSalary,
            "allowances" to salary.allowances,
            "deductions" to salary.deductions,
            "netSalary" to salary.netSalary, // or salary.calculateNet() if it's a method
            "paymentStatus" to salary.paymentStatus,
            "paymentDate" to (salary.paymentDate ?: ""),
            "remarks" to (salary.remarks ?: ""),
            "updatedAt" to currentTime
        )

        // 1. Update Remote
        return remoteRepo.updateSalary(salary.id, updateMap)
            .onSuccess {
                // 2. Update Local (Optimization: No need to fetch from remote again)
                insertLocal(salary.copy(updatedAt = currentTime))
            }
    }

    suspend fun deleteSalary(id: String): Result<Unit> {
        return remoteRepo.deleteSalary(id).onSuccess {
            // 1. Local delete
            deleteLocally(id)

            // 2. Tombstone for Sync
            try {
                FirebaseRefs.deletedRecordsRef
                    .child(id)
                    .setValue(
                        DeletedRecord(
                            id = id,
                            type = "salary",
                            timestamp = System.currentTimeMillis()
                        )
                    ).await()
            } catch (e: Exception) {
                Log.e("SalaryRepoManager", "Failed to set tombstone", e)
            }
        }
    }
}