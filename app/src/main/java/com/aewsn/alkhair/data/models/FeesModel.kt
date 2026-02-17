package com.aewsn.alkhair.data.models

import android.os.Parcelable
import androidx.room.ColumnInfo
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
    @SerialName("user_id")
    @ColumnInfo(name = "user_id")
    val studentId: String = "",
    // studentName might need to be fetched via join or kept as local cache
    @kotlinx.serialization.Transient
    val studentName: String = "",
    @SerialName("class_id")
    @ColumnInfo(name = "class_id")
    val classId: String? = null,
    @kotlinx.serialization.Transient
    val shift: String = "",
    @SerialName("fee_date")
    @ColumnInfo(name = "fee_date")
    val feeDate: String = "", // "YYYY-MM-DD" (Represents the Fee Month)
    @SerialName("base_amount")
    @ColumnInfo(name = "base_amount")
    val baseAmount: Double = 0.0,
    @SerialName("paid_amount")
    @ColumnInfo(name = "paid_amount")
    val paidAmount: Double = 0.0,
    @SerialName("due_amount")
    @ColumnInfo(name = "due_amount")
    val dueAmount: Double = 0.0,
    val discounts: Double = 0.0,
    @SerialName("net_fees")
    @ColumnInfo(name = "net_fees")
    val netFees: Double = 0.0,
    @SerialName("payment_date")
    @ColumnInfo(name = "payment_date")
    val paymentDate: String = "",
    val remarks: String? = null,
    @SerialName("payment_status")
    @ColumnInfo(name = "payment_status")
    val paymentStatus: String = "Pending", // Pending, Paid
    @SerialName("updated_at_ms")
    @ColumnInfo(name = "updated_at_ms")
    override val updatedAt: Long = System.currentTimeMillis(),

    @kotlinx.serialization.Transient
    @SerialName("is_synced")
    @ColumnInfo(name = "is_synced")
    override val isSynced: Boolean = true
) : Parcelable, Syncable
