package com.aewsn.alkhair.data.models

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@Parcelize
@Entity(tableName = "salary")

data class SalaryModel(
    @PrimaryKey
    val id: String = "",
    @SerialName("staff_id")
    val staffId: String = "",
    // staffName likely fetched via join
    @kotlinx.serialization.Transient
    @SerialName("staff_name")
    val staffName: String = "",
    @SerialName("salary_date")
    val salaryDate: String = "", // Format: YYYY-MM-DD
    @SerialName("basic_salary")
    val basicSalary: Double = 0.0,
    @SerialName("allowances")
    val allowances: Double = 0.0,
    @SerialName("deductions")
    val deductions: Double = 0.0,
    @SerialName("net_salary")
    var netSalary: Double = 0.0,
    @SerialName("payment_status")
    val paymentStatus: String = "Pending", // Pending, Paid
    @SerialName("payment_date")
    val paymentDate: String? = null, // Format: YYYY-MM-DD
    val remarks: String? = null,

    
    @kotlinx.serialization.Transient
    @SerialName("created_at") // Kept for local Room if needed, but Transient stops Network Sync
    val createdAt: Long = System.currentTimeMillis(),

    // New field for combined indexing
    @kotlinx.serialization.Transient
    @SerialName("staff_month")
    val staffMonth: String = "${staffId}_${salaryDate}",
    @SerialName("updated_at")
    override val updatedAt: Long = System.currentTimeMillis(),
    
    @kotlinx.serialization.Transient
    @SerialName("is_synced")
    override val isSynced: Boolean = true
) : Syncable, 
    Parcelable {
    fun calculateNet(): Double {
        return basicSalary + allowances - deductions
    }
}