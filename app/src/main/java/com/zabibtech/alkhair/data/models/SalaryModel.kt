package com.zabibtech.alkhair.data.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class SalaryModel(
    val id: String = "",
    val staffId: String = "",
    val staffName: String = "",
    val monthYear: String = "", // Format: YYYY-MM
    val basicSalary: Double = 0.0,
    val allowances: Double = 0.0,
    val deductions: Double = 0.0,
    var netSalary: Double = 0.0,
    val paymentStatus: String = "Pending", // Pending, Paid
    val paymentDate: String? = null, // Format: YYYY-MM-DD
    val remarks: String? = null,
    val createdAt: Long = System.currentTimeMillis(),

    // New field for combined indexing
    val staffMonth: String = "${staffId}_${monthYear}"
) : Parcelable {
    fun calculateNet(): Double {
        return basicSalary + allowances - deductions
    }
}