package com.zabibtech.alkhair.data.remote.firebase

import android.util.Log
import com.google.firebase.database.Query
import com.zabibtech.alkhair.data.models.SalaryModel
import com.zabibtech.alkhair.utils.FirebaseRefs.salariesRef
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseSalaryRepository @Inject constructor() {

    // ... (createSalary, updateSalary, deleteSalary, getSalaryById functions remain the same)

    suspend fun createSalary(salary: SalaryModel): Result<SalaryModel> {
        return try {
            val salaryId = salary.id.ifEmpty { salariesRef.push().key!! }
            val newSalary = salary.copy(
                id = salaryId,
                netSalary = salary.calculateNet(),
                staffMonth = "${salary.staffId}_${salary.monthYear}",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            salariesRef.child(salaryId).setValue(newSalary).await()
            Result.success(newSalary)
        } catch (e: Exception) {
            Log.e("FirebaseSalaryRepo", "Error creating salary for staff: ${salary.staffId}", e)
            Result.failure(e)
        }
    }

    suspend fun updateSalary(salaryId: String, updatedData: Map<String, Any>): Result<Unit> {
        return try {
            val dataToUpdate = updatedData.toMutableMap()
            dataToUpdate["updatedAt"] = System.currentTimeMillis()

            salariesRef.child(salaryId).updateChildren(dataToUpdate).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirebaseSalaryRepo", "Error updating salary with ID: $salaryId", e)
            Result.failure(e)
        }
    }

    suspend fun deleteSalary(salaryId: String): Result<Unit> {
        return try {
            if (salaryId.isBlank()) {
                throw IllegalArgumentException("Salary ID cannot be blank.")
            }
            salariesRef.child(salaryId).removeValue().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirebaseSalaryRepo", "Error deleting salary with ID: $salaryId", e)
            Result.failure(e)
        }
    }

    suspend fun getSalaryById(salaryId: String): Result<SalaryModel> {
        return try {
            if (salaryId.isBlank()) {
                throw IllegalArgumentException("Salary ID cannot be blank.")
            }
            val snapshot = salariesRef.child(salaryId).get().await()
            val salary = snapshot.getValue(SalaryModel::class.java)
            if (salary != null) {
                Result.success(salary)
            } else {
                Result.failure(NoSuchElementException("Salary with ID $salaryId not found."))
            }
        } catch (e: Exception) {
            Log.e("FirebaseSalaryRepo", "Error getting salary with ID: $salaryId", e)
            Result.failure(e)
        }
    }

    /**
     * Fetches salaries with optional filters for staffId and monthYear.
     */
    suspend fun getSalaries(staffId: String?, monthYear: String?): Result<List<SalaryModel>> {
        return try {
            val query: Query = when {
                !staffId.isNullOrBlank() && !monthYear.isNullOrBlank() -> {
                    salariesRef.orderByChild("staffMonth").equalTo("${staffId}_${monthYear}")
                }

                !staffId.isNullOrBlank() -> {
                    salariesRef.orderByChild("staffId").equalTo(staffId)
                }

                !monthYear.isNullOrBlank() -> {
                    salariesRef.orderByChild(
                        "monthYear"
                    ).equalTo(monthYear)
                }

                else -> {
                    salariesRef
                }
            }

            val snapshot = query.get().await()
            val salaries = snapshot.children.mapNotNull { it.getValue(SalaryModel::class.java) }
            // Sort by monthYear (YYYY-MM) descending, so latest month comes first
            Result.success(salaries.sortedByDescending { it.monthYear })
        } catch (e: Exception) {
            Log.e("FirebaseSalaryRepo", "Error getting salaries", e)
            Result.failure(e)
        }
    }
}
