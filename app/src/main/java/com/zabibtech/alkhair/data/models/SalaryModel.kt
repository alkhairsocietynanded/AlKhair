package com.zabibtech.alkhair.data.models

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(tableName = "salary")
@com.google.firebase.database.IgnoreExtraProperties
data class SalaryModel(
    @PrimaryKey
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
    val staffMonth: String = "${staffId}_${monthYear}",
    val updatedAt: Long = System.currentTimeMillis()
) : Parcelable {
    fun calculateNet(): Double {
        return basicSalary + allowances - deductions
    }
}