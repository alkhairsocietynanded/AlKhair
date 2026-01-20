package com.zabibtech.alkhair.data.models

import androidx.room.Entity

@Entity(tableName = "attendance", primaryKeys = ["studentId", "classId", "date"])
@com.google.firebase.database.IgnoreExtraProperties
data class Attendance(
    val studentId: String = "",
    val classId: String = "",
    val date: String = "",       // yyyy-MM-dd
    val status: String = "",      // Present | Absent | Leave
    val shift: String = "",
    override val updatedAt: Long = System.currentTimeMillis(), // ✅ Override zaroori hai
    @get:com.google.firebase.database.Exclude
    override val isSynced: Boolean = true
) : Syncable // ✅ Interface implement karein