package com.aewsn.alkhair.data.models

data class DashboardStats(
    // Attendance & Count Stats
    val studentsCount: Int = 0,
    val teachersCount: Int = 0,
    val classesCount: Int = 0,
    val attendancePercentage: Int = 0,
    val presentCount: Int = 0,
    val absentCount: Int = 0,
    val leaveCount: Int = 0,

    // ✅ Fees Stats (New)
    val totalFeeCollected: Double = 0.0,
    val totalFeePending: Double = 0.0,
    val feePercentage: Int = 0
)