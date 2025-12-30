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

    /**
     * ✅ CREATE SALARY (With Composite Key)
     * Adds 'staff_sync_key' for optimized syncing for teachers.
     */
    suspend fun createSalary(salary: SalaryModel): Result<SalaryModel> {
        return try {
            val salaryId = salary.id.ifEmpty { salariesRef.push().key!! }
            val currentTime = System.currentTimeMillis()

            val newSalary = salary.copy(
                id = salaryId,
                netSalary = salary.calculateNet(),
                // Keep existing business logic key
                staffMonth = "${salary.staffId}_${salary.monthYear}",
                createdAt = currentTime,
                updatedAt = currentTime
            )

            // Convert to Map to inject "staff_sync_key"
            val salaryMap = mapOf(
                "id" to newSalary.id,
                "staffId" to newSalary.staffId,
                "staffName" to newSalary.staffName,
                "monthYear" to newSalary.monthYear,
                "basicSalary" to newSalary.basicSalary,
                "allowances" to newSalary.allowances,
                "deductions" to newSalary.deductions,
                "netSalary" to newSalary.netSalary,
                "paymentStatus" to newSalary.paymentStatus,
                "paymentDate" to (newSalary.paymentDate ?: ""),
                "remarks" to (newSalary.remarks ?: ""),
                "staffMonth" to newSalary.staffMonth,
                "createdAt" to newSalary.createdAt,
                "updatedAt" to newSalary.updatedAt,

                // 🔥 COMPOSITE KEY: StaffID + Timestamp
                // Example: "Teacher123_1766500000"
                "staff_sync_key" to "${newSalary.staffId}_$currentTime"
            )

            salariesRef.child(salaryId).setValue(salaryMap).await()
            Result.success(newSalary)
        } catch (e: Exception) {
            Log.e("FirebaseSalaryRepo", "Error creating salary", e)
            Result.failure(e)
        }
    }

    /**
     * ✅ UPDATE SALARY
     */
    suspend fun updateSalary(salaryId: String, updatedData: Map<String, Any>): Result<Unit> {
        return try {
            val dataToUpdate = updatedData.toMutableMap()
            val currentTime = System.currentTimeMillis()

            // 1. Update Timestamp
            dataToUpdate["updatedAt"] = currentTime

            // 2. Update Composite Key (staff_sync_key)
            // We assume 'staffId' is passed in the map from RepoManager
            if (dataToUpdate.containsKey("staffId")) {
                val staffId = dataToUpdate["staffId"] as String
                dataToUpdate["staff_sync_key"] = "${staffId}_$currentTime"
            }

            salariesRef.child(salaryId).updateChildren(dataToUpdate).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirebaseSalaryRepo", "Error updating salary", e)
            Result.failure(e)
        }
    }

    /**
     * ✅ TEACHER SYNC (Targeted Staff Sync)
     * Fetches only salary records for a specific staff member updated after timestamp.
     */
    suspend fun getSalariesForStaffUpdatedAfter(staffId: String, timestamp: Long): Result<List<SalaryModel>> {
        return try {
            // Start: "StaffID_LastSyncTime"
            val startKey = "${staffId}_${timestamp + 1}"
            // End: "StaffID_Future"
            val endKey = "${staffId}_9999999999999"

            val snapshot = salariesRef
                .orderByChild("staff_sync_key") // ✅ Index Required
                .startAt(startKey)
                .endAt(endKey)
                .get()
                .await()

            val list = snapshot.children.mapNotNull { it.getValue(SalaryModel::class.java) }
            Result.success(list)
        } catch (e: Exception) {
            Log.e("FirebaseSalaryRepo", "Error fetching staff salaries", e)
            Result.failure(e)
        }
    }

    /**
     * ✅ ADMIN SYNC (Global)
     */
    suspend fun getSalariesUpdatedAfter(timestamp: Long): Result<List<SalaryModel>> {
        return try {
            val snapshot = salariesRef
                .orderByChild("updatedAt")
                .startAt((timestamp + 1).toDouble())
                .get()
                .await()
            val salaries = snapshot.children.mapNotNull { it.getValue(SalaryModel::class.java) }
            Result.success(salaries)
        } catch (e: Exception) {
            Log.e("FirebaseSalaryRepo", "Error getting updated salaries", e)
            Result.failure(e)
        }
    }

    // ... (Standard Methods: delete, getById remain same) ...

    suspend fun deleteSalary(salaryId: String): Result<Unit> {
        return try {
            salariesRef.child(salaryId).removeValue().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirebaseSalaryRepo", "Error deleting salary", e)
            Result.failure(e)
        }
    }

    suspend fun getSalaryById(salaryId: String): Result<SalaryModel> {
        return try {
            val snapshot = salariesRef.child(salaryId).get().await()
            val salary = snapshot.getValue(SalaryModel::class.java)
            if (salary != null) Result.success(salary)
            else Result.failure(NoSuchElementException("Salary not found"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Legacy method support (Optional)
    suspend fun getSalaries(staffId: String?, monthYear: String?): Result<List<SalaryModel>> {
        return try {
            val query: Query = when {
                !staffId.isNullOrBlank() && !monthYear.isNullOrBlank() ->
                    salariesRef.orderByChild("staffMonth").equalTo("${staffId}_${monthYear}")
                !staffId.isNullOrBlank() ->
                    salariesRef.orderByChild("staffId").equalTo(staffId)
                !monthYear.isNullOrBlank() ->
                    salariesRef.orderByChild("monthYear").equalTo(monthYear)
                else -> salariesRef
            }
            val snapshot = query.get().await()
            val salaries = snapshot.children.mapNotNull { it.getValue(SalaryModel::class.java) }
            Result.success(salaries.sortedByDescending { it.monthYear })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}