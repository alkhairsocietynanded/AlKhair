package com.zabibtech.alkhair.data.repository

import com.google.firebase.database.Query
import com.zabibtech.alkhair.data.models.SalaryModel
import com.zabibtech.alkhair.utils.FirebaseRefs.salariesRef
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SalaryRepository @Inject constructor() {

    /**
     * Yeh function Firebase se salaries laata hai. Yeh staffId aur monthYear ke hisaab se
     * data ko filter kar sakta hai taaki sirf zaroori data hi download ho.
     */
    suspend fun getSalaries(staffId: String?, monthYear: String?): List<SalaryModel> {
        // Step 1: Hum filter ke hisaab se ek Firebase query banayenge.
        val query: Query = when {

            // Case 1: Jab staff aur month DONO select kiye gaye hain.
            // Example: "Abdul Aala" ki "2025-01" ki salary.
            !staffId.isNullOrBlank() && !monthYear.isNullOrBlank() -> {
                // Hum "staff_month" field ka istemal karte hain, jo "staffId_monthYear" ko jod kar banta hai.
                // Yeh Firebase ki limit ko bypass karne ka sabse efficient tareeka hai.
                salariesRef.orderByChild("staff_month").equalTo("${staffId}_${monthYear}")
            }

            // Case 2: Jab SIRF staff select kiya gaya hai (Month "All Months" hai).
            // Example: "Abdul Aala" ki saare mahino ki salary.
            !staffId.isNullOrBlank() -> {
                salariesRef.orderByChild("staffId").equalTo(staffId)
            }

            // Case 3: Jab SIRF month select kiya gaya hai (Staff "All Staff" hai).
            // Example: "2025-01" mein saare staff ki salary.
            !monthYear.isNullOrBlank() -> {
                salariesRef.orderByChild("monthYear").equalTo(monthYear)
            }

            // Case 4: Jab koi bhi filter select nahi hai (All Staff, All Months).
            else -> {
                // Saari salaries download hongi.
                salariesRef
            }
        }

        // Step 2: Query ko Firebase par execute karo aur result ka intezar karo.
        val snapshot = query.get().await()

        // Step 3: Aaye hue result (snapshot) ko SalaryModel ki list mein convert karke return karo.
        return snapshot.children.mapNotNull { it.getValue(SalaryModel::class.java) }
    }

    suspend fun addOrUpdateSalary(salary: SalaryModel) {
        val key = salary.id.ifEmpty {
            salariesRef.push().key ?: throw Exception("Failed to generate salary id")
        }

        val newSalary = salary.copy(
            id = key,
            netSalary = salary.calculateNet(),
            staffMonth = "${salary.staffId}_${salary.monthYear}"
        )
        salariesRef.child(key).setValue(newSalary).await()
    }

    suspend fun deleteSalary(salaryId: String) {
        if (salaryId.isEmpty()) throw Exception("Invalid salary id")
        salariesRef.child(salaryId).removeValue().await()
    }

    suspend fun getAllSalaries(): List<SalaryModel> {
        val snapshot = salariesRef.get().await()
        return snapshot.children.mapNotNull { it.getValue(SalaryModel::class.java) }
    }

    suspend fun getSalariesByStaff(staffId: String): List<SalaryModel> {
        val snapshot = salariesRef.orderByChild("staffId").equalTo(staffId).get().await()
        return snapshot.children.mapNotNull { it.getValue(SalaryModel::class.java) }
    }

    suspend fun getSalariesByMonth(monthYear: String): List<SalaryModel> {
        val snapshot = salariesRef.orderByChild("monthYear").equalTo(monthYear).get().await()
        return snapshot.children.mapNotNull { it.getValue(SalaryModel::class.java) }
    }

    suspend fun getSalaryById(salaryId: String): SalaryModel? {
        if (salaryId.isEmpty()) throw Exception("Invalid salary id")
        val snapshot = salariesRef.child(salaryId).get().await()
        return snapshot.getValue(SalaryModel::class.java)
    }

    data class MonthlySummary(
        val totalPaid: Double,
        val totalPending: Double,
        val totalNet: Double
    )

    suspend fun getMonthlySummary(monthYear: String? = null): MonthlySummary {
        val snapshot = if (monthYear.isNullOrBlank()) {
            salariesRef.get().await()
        } else {
            salariesRef.orderByChild("monthYear").equalTo(monthYear).get().await()
        }

        var totalPaid = 0.0
        var totalPending = 0.0
        var totalNet = 0.0

        snapshot.children.forEach { child ->
            val s = child.getValue(SalaryModel::class.java) ?: return@forEach
            totalNet += s.netSalary
            if (s.paymentStatus.equals("Paid", ignoreCase = true)) totalPaid += s.netSalary
            else totalPending += s.netSalary
        }

        return MonthlySummary(totalPaid, totalPending, totalNet)
    }

    suspend fun getStaffSummary(staffId: String, monthYear: String? = null): MonthlySummary {
        val snapshot = if (monthYear.isNullOrBlank()) {
            salariesRef.orderByChild("staffId").equalTo(staffId).get().await()
        } else {
            salariesRef.orderByChild("staff_month").equalTo("${staffId}_${monthYear}").get().await()
        }

        var totalPaid = 0.0
        var totalPending = 0.0
        var totalNet = 0.0

        snapshot.children.forEach { child ->
            val s = child.getValue(SalaryModel::class.java) ?: return@forEach
            totalNet += s.netSalary
            if (s.paymentStatus.equals("Paid", ignoreCase = true)) totalPaid += s.netSalary
            else totalPending += s.netSalary
        }

        return MonthlySummary(totalPaid, totalPending, totalNet)
    }
}
