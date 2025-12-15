package com.zabibtech.alkhair.data.manager

import android.util.Log
import com.zabibtech.alkhair.data.local.local_repos.LocalSalaryRepository
import com.zabibtech.alkhair.data.models.DeletedRecord
import com.zabibtech.alkhair.data.models.SalaryModel
import com.zabibtech.alkhair.data.remote.firebase.FirebaseSalaryRepository
import com.zabibtech.alkhair.utils.FirebaseRefs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SalaryRepoManager @Inject constructor(
    private val localSalaryRepo: LocalSalaryRepository,
    private val firebaseSalaryRepo: FirebaseSalaryRepository
) {

    data class MonthlySummary(
        val totalPaid: Double,
        val totalPending: Double,
        val totalNet: Double
    )

    suspend fun createSalary(salary: SalaryModel): Result<SalaryModel> {
        val result = firebaseSalaryRepo.createSalary(salary)
        result.onSuccess { newSalary ->
            try {
                // newSalary from Firebase already has the latest timestamps
                localSalaryRepo.insertSalary(newSalary)
            } catch (e: Exception) {
                Log.e("SalaryRepoManager", "Failed to cache new salary locally", e)
            }
        }
        return result
    }

    suspend fun updateSalary(salary: SalaryModel): Result<Unit> {
        // CORRECTED: Add the new timestamp to the update map
        val updateMap: MutableMap<String, Any?> = mutableMapOf(
            "basicSalary" to salary.basicSalary,
            "allowances" to salary.allowances,
            "deductions" to salary.deductions,
            "netSalary" to salary.calculateNet(),
            "paymentStatus" to salary.paymentStatus,
            "paymentDate" to salary.paymentDate,
            "remarks" to salary.remarks,
            "updatedAt" to System.currentTimeMillis()
        )

        val result = firebaseSalaryRepo.updateSalary(
            salary.id,
            updateMap.filterValues { it != null } as Map<String, Any>)

        result.onSuccess {
            try {
                // Fetch the updated model from server to get the definitive state
                firebaseSalaryRepo.getSalaryById(salary.id).onSuccess { updatedSalaryFromServer ->
                    localSalaryRepo.insertSalary(updatedSalaryFromServer)
                }
            } catch (e: Exception) {
                Log.e("SalaryRepoManager", "Failed to update local salary cache", e)
            }
        }
        return result
    }

    suspend fun deleteSalary(salaryId: String): Result<Unit> {
        val result = firebaseSalaryRepo.deleteSalary(salaryId)
        result.onSuccess {
            try {
                localSalaryRepo.deleteSalary(salaryId)

                val deletedRecord = DeletedRecord(
                    id = salaryId,
                    type = "salary",
                    timestamp = System.currentTimeMillis()
                )
                FirebaseRefs.deletedRecordsRef.child(salaryId).setValue(deletedRecord).await()

            } catch (e: Exception) {
                Log.e("SalaryRepoManager", "Failed to process salary deletion for ID: $salaryId", e)
            }
        }
        return result
    }

    suspend fun deleteSalaryLocally(id: String) {
        try {
            localSalaryRepo.deleteSalary(id)
        } catch (e: Exception) {
            Log.e("SalaryRepoManager", "Failed to delete local salary: $id", e)
        }
    }

    suspend fun syncSalaries(lastSync: Long) {
        firebaseSalaryRepo.getSalariesUpdatedAfter(lastSync).onSuccess { salaries ->
            if (salaries.isNotEmpty()) {
                try {
                    val updatedList =
                        salaries.map { it.copy(updatedAt = System.currentTimeMillis()) }
                    localSalaryRepo.insertSalaries(updatedList)
                } catch (e: Exception) {
                    Log.e("SalaryRepoManager", "Failed to cache synced salaries", e)
                }
            }
        }
    }

    // SIMPLE GETTER: Relies on local data, which is kept fresh by syncSalaries
    suspend fun getSalaries(staffId: String?, monthYear: String?): Result<List<SalaryModel>> {
        return try {
            val allLocalSalaries = localSalaryRepo.getAllSalaries().first()
            val filteredSalaries = allLocalSalaries.filter { salary ->
                val staffMatch = staffId.isNullOrBlank() || salary.staffId == staffId
                val monthMatch = monthYear.isNullOrBlank() || salary.monthYear == monthYear
                staffMatch && monthMatch
            }
            Result.success(filteredSalaries)
        } catch (e: Exception) {
            Log.e("SalaryRepoManager", "Could not get local salary data", e)
            Result.failure(e)
        }
    }

    private fun calculateSummary(salaries: List<SalaryModel>): MonthlySummary {
        var totalPaid = 0.0
        var totalPending = 0.0
        var totalNet = 0.0

        salaries.forEach { s ->
            totalNet += s.netSalary
            if (s.paymentStatus.equals("Paid", ignoreCase = true)) {
                totalPaid += s.netSalary
            } else {
                totalPending += s.netSalary
            }
        }
        return MonthlySummary(totalPaid, totalPending, totalNet)
    }

    suspend fun getMonthlySummary(monthYear: String? = null): Result<MonthlySummary> {
        val salariesResult = getSalaries(null, monthYear)
        return salariesResult.fold(
            onSuccess = { salaries -> Result.success(calculateSummary(salaries)) },
            onFailure = { exception -> Result.failure(exception) }
        )
    }

    suspend fun getStaffSummary(
        staffId: String,
        monthYear: String? = null
    ): Result<MonthlySummary> {
        val salariesResult = getSalaries(staffId, monthYear)
        return salariesResult.fold(
            onSuccess = { salaries -> Result.success(calculateSummary(salaries)) },
            onFailure = { exception -> Result.failure(exception) }
        )
    }
}
