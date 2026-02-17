package com.aewsn.alkhair.data.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "attendance", primaryKeys = ["user_id", "class_id", "date"])

data class Attendance(
    @SerialName("user_id")
    @ColumnInfo(name = "user_id")
    val studentId: String = "",
    @SerialName("class_id")
    @ColumnInfo(name = "class_id")
    val classId: String = "",
    val date: String = "",       // yyyy-MM-dd
    val status: String = "",      // Present | Absent | Leave
    
    @kotlinx.serialization.Transient
    val shift: String = "", // Not needed in Supabase as we use ClassID
    
    @SerialName("time")
    val time: String? = null, // ✅ Added Time Field

    @SerialName("updated_at_ms")
    @ColumnInfo(name = "updated_at_ms")
    override val updatedAt: Long = System.currentTimeMillis(),
    
    @kotlinx.serialization.Transient
    @SerialName("is_synced")
    @androidx.room.ColumnInfo(name = "is_synced")
    override val isSynced: Boolean = true
) : Syncable