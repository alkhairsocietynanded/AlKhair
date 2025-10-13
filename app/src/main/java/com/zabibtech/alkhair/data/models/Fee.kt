package com.zabibtech.alkhair.data.models

data class Fee(
    val id: String = "",
    val studentId: String = "",
    val month: String = "",
    val totalAmount: Double = 0.0,
    val paidAmount: Double = 0.0,
    val dueAmount: Double = 0.0,
    val status: String = "Unpaid", // Paid | Partial | Unpaid
    val paymentDate: String = ""
)
