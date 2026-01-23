package com.zabibtech.alkhair.data.models

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@Parcelize
@Entity(tableName = "fees")

data class FeesModel(
    @PrimaryKey
    val id: String = "",
    @SerialName("student_id")
    val studentId: String = "",
    // studentName might need to be fetched via join or kept as local cache
    @SerialName("student_name")
    val studentName: String = "", 
    @SerialName("class_id")
    val classId: String? = null,
    val shift: String = "",
    @SerialName("month_year")
    val monthYear: String = "", // Format: YYYY-MM
    @SerialName("base_amount")
    val baseAmount: Double = 0.0,
    @SerialName("paid_amount")
    val paidAmount: Double = 0.0,
    @SerialName("due_amount")
    val dueAmount: Double = 0.0,
    val discounts: Double = 0.0,
    @SerialName("net_fees")
    val netFees: Double = 0.0,
    @SerialName("payment_date")
    val paymentDate: String = "",
    val remarks: String? = null,
    @SerialName("payment_status")
    val paymentStatus: String = "Pending", // Pending, Paid
    @SerialName("updated_at")
    override val updatedAt: Long = System.currentTimeMillis(),
    @SerialName("is_synced")

    override val isSynced: Boolean = true
) :Parcelable, Syncable
