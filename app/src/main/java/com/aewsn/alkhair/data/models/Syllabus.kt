package com.aewsn.alkhair.data.models

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@Parcelize
@Entity(tableName = "syllabus")
data class Syllabus(
    @PrimaryKey
    val id: String = "",
    @SerialName("class_id")
    @androidx.room.ColumnInfo(name = "class_id")
    val classId: String = "",
    val subject: String = "",
    val topic: String = "",
    val description: String = "",
    @SerialName("attachment_url")
    @androidx.room.ColumnInfo(name = "attachment_url")
    val attachmentUrl: String? = null,
    
    // Additional helpful fields
    @SerialName("completion_status")
    @androidx.room.ColumnInfo(name = "completion_status")
    val completionStatus: String = "Pending", // Pending, Completed
    
    @SerialName("updated_at_ms")
    @androidx.room.ColumnInfo(name = "updated_at_ms")
    override val updatedAt: Long = System.currentTimeMillis(),

    @kotlinx.serialization.Transient
    @SerialName("is_synced")
    @androidx.room.ColumnInfo(name = "is_synced")
    override val isSynced: Boolean = true
) : Parcelable, Syncable
