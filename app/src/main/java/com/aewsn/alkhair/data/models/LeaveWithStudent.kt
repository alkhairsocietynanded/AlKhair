package com.aewsn.alkhair.data.models

import androidx.room.ColumnInfo
import androidx.room.Embedded

data class LeaveWithStudent(
    @Embedded val leave: Leave,
    @ColumnInfo(name = "student_name") 
    val studentName: String
)
