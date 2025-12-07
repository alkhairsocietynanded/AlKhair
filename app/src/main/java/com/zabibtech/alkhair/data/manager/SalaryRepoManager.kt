package com.zabibtech.alkhair.data.manager

import android.util.Log
import com.zabibtech.alkhair.data.local.local_repos.LocalSalaryRepository
import com.zabibtech.alkhair.data.models.SalaryModel
import com.zabibtech.alkhair.data.remote.firebase.FirebaseSalaryRepository
import com.zabibtech.alkhair.utils.StaleDetector
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SalaryRepoManager @Inject constructor(
    private val localSalaryRepo: LocalSalaryRepository,
    private val firebaseSalaryRepo: FirebaseSalaryRepository
) {

    // Data class for summary results, now part of the manager
    data class MonthlySummary(
        val totalPaid: Double,
        val totalPending: Double,
        val totalNet: Double
    )

    suspend fun createSalary(salary: SalaryModel): Result<SalaryModel> {
        val result = firebaseSalaryRepo.createSalary(salary)
        result.onSuccess { newSalary ->
            try {
                localSalaryRepo.insertSalary(newSalary) // newSalary has the updated timestamps
            } catch (e: Exception) {
                Log.e("SalaryRepoManager", "Failed to cache new salary locally", e)
            }
        }
        return result
    }

    suspend fun updateSalary(salary: SalaryModel): Result<Unit> {
        val updateMap: Map<String, Any?> = mapOf(
            "basicSalary" to salary.basicSalary,
            "allowances" to salary.allowances,
            "deductions" to salary.deductions,
            "netSalary" to salary.calculateNet(), // Recalculate net salary
            "paymentStatus" to salary.paymentStatus,
            "paymentDate" to salary.paymentDate,
            "remarks" to salary.remarks
        )

        val result = firebaseSalaryRepo.updateSalary(salary.id, updateMap.filterValues { it != null } as Map<String, Any>)

        result.onSuccess {
            try {
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
            } catch (e: Exception) {
                Log.e("SalaryRepoManager", "Failed to delete local salary", e)
            }
        }
        return result
    }

    suspend fun getSalaries(staffId: String?, monthYear: String?): Result<List<SalaryModel>> {
        val localData = try {
            val allLocalSalaries = localSalaryRepo.getAllSalaries().first()
            allLocalSalaries.filter { salary ->
                val staffMatch = staffId.isNullOrBlank() || salary.staffId == staffId
                val monthMatch = monthYear.isNullOrBlank() || salary.monthYear == monthYear
                staffMatch && monthMatch
            }
        } catch (e: Exception) {
            Log.w("SalaryRepoManager", "Could not get local salary data", e)
            emptyList()
        }

        if (localData.isNotEmpty() && localData.all { !StaleDetector.isStale(it.updatedAt) }) {
            return Result.success(localData)
        }

        val remoteResult = firebaseSalaryRepo.getSalaries(staffId, monthYear)
        return remoteResult.fold(
            onSuccess = { salaries ->
                try {
                    localSalaryRepo.insertSalaries(salaries)
                } catch (e: Exception) {
                    Log.e("SalaryRepoManager", "Failed to refresh salary cache from remote", e)
                }
                Result.success(salaries)
            },
            onFailure = { exception ->
                if (localData.isNotEmpty()) Result.success(localData) else Result.failure(exception)
            }
        )
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

    suspend fun getStaffSummary(staffId: String, monthYear: String? = null): Result<MonthlySummary> {
        val salariesResult = getSalaries(staffId, monthYear)

        return salariesResult.fold(
            onSuccess = { salaries -> Result.success(calculateSummary(salaries)) },
            onFailure = { exception -> Result.failure(exception) }
        )
    }
}
