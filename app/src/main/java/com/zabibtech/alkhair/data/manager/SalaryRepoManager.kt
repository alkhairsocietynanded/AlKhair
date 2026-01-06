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
       ✍️ WRITE — UI ACTIONS
       ============================================================ */

    suspend fun createSalary(salary: SalaryModel): Result<Unit> {
        return remoteRepo.createSalary(salary)
            .onSuccess { createdSalary ->
                insertLocal(createdSalary)
            }
            .map { }
    }

    suspend fun updateSalary(salary: SalaryModel): Result<Unit> {
        val currentTime = System.currentTimeMillis()

        // ✅ CRITICAL: Map mein 'staffId' bhejna zaroori hai
        // taaki FirebaseRepo 'staff_sync_key' ko update/maintain kar sake.
        val updateMap = mapOf(
            "staffId" to salary.staffId, // Required for Composite Key logic
            "basicSalary" to salary.basicSalary,
            "allowances" to salary.allowances,
            "deductions" to salary.deductions,
            "netSalary" to salary.calculateNet(), // Ensure updated net amount is sent
            "paymentStatus" to salary.paymentStatus,
            "paymentDate" to (salary.paymentDate ?: ""),
            "remarks" to (salary.remarks ?: ""),
            "updatedAt" to currentTime
        )

        return remoteRepo.updateSalary(salary.id, updateMap)
            .onSuccess {
                // Update Local immediately to reflect changes in UI
                val updatedLocalSalary = salary.copy(updatedAt = currentTime)
                insertLocal(updatedLocalSalary)
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

    // Base Implementations (Ensure these methods exist in LocalRepo)
    override suspend fun insertLocal(items: List<SalaryModel>) = localRepo.insertSalaries(items)
    override suspend fun insertLocal(item: SalaryModel) = localRepo.insertSalary(item)
    override suspend fun deleteLocally(id: String) = localRepo.deleteSalary(id)
    override suspend fun clearLocal() = localRepo.clearAll()}