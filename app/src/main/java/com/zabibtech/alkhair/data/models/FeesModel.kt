package com.zabibtech.alkhair.data.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class FeesModel(
    val id: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val monthYear: String = "", // Format: YYYY-MM
    val baseAmount: Double = 0.0,
    val paidAmount: Double = 0.0,
    val dueAmount: Double = 0.0,
    val discounts: Double = 0.0,
    val netFees: Double = 0.0,
    val paymentDate: String = "",
    val remarks: String? = null,
    val paymentStatus: String = "Pending", // Pending, Paid

) : Parcelable
