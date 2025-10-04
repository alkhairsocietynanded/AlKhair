package com.zabibtech.alkhair.data.models

data class Attendance(
    val studentId: String = "",
    val date: String = "",       // yyyy-MM-dd
    val status: String = ""      // Present | Absent
)