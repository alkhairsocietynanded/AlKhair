package com.aewsn.alkhair.data.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
@Entity(tableName = "leaves")
data class Leave(
    @PrimaryKey
    val id: String = java.util.UUID.randomUUID().toString(),
    @ColumnInfo(name = "student_id")
    @SerialName("student_id")
    val studentId: String = "",
    @ColumnInfo(name = "start_date")
    @SerialName("start_date")
    val startDate: String = "",
    @ColumnInfo(name = "end_date")
    @SerialName("end_date")
    val endDate: String = "",
    @ColumnInfo(name = "reason")
    val reason: String = "",
    @ColumnInfo(name = "status")
    val status: String = "Pending",
    @ColumnInfo(name = "updated_at")
    @SerialName("updated_at")
    override val updatedAt: Long = System.currentTimeMillis(),
    
    @kotlinx.serialization.Transient
    @ColumnInfo(name = "is_synced")
    @SerialName("is_synced")
    override val isSynced: Boolean = true
) : Syncable
