package com.zabibtech.alkhair.data.models

import androidx.room.Entity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "attendance", primaryKeys = ["studentId", "classId", "date"])

data class Attendance(
    @SerialName("student_id")
    val studentId: String = "",
    @SerialName("class_id")
    val classId: String = "",
    val date: String = "",       // yyyy-MM-dd
    val status: String = "",      // Present | Absent | Leave
    val shift: String = "",
    @SerialName("updated_at")
    override val updatedAt: Long = System.currentTimeMillis(),
    @SerialName("is_synced")

    override val isSynced: Boolean = true
) : Syncable