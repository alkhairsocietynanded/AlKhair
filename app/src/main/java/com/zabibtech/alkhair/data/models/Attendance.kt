package com.zabibtech.alkhair.data.models

import androidx.room.Entity

// A unique attendance record is a combination of a student, in a specific class, on a specific date.
@Entity(tableName = "attendance", primaryKeys = ["studentId", "classId", "date"])
data class Attendance(
    val studentId: String = "",
    val classId: String = "",
    val date: String = "",       // yyyy-MM-dd
    val status: String = "",      // Present | Absent | Leave
    val updatedAt: Long = System.currentTimeMillis()
)
