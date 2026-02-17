package com.aewsn.alkhair.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "announcements")

data class Announcement(
    @PrimaryKey
    val id: String = "",
    val title: String = "",
    val content: String = "",
    // timestamp is BigInt in SQL
    @SerialName("timestamp")
    @androidx.room.ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis(),
    @SerialName("target_id")
    @androidx.room.ColumnInfo(name = "target_id")
    val target: String = "ALL", // Mapped to target_id
    @SerialName("updated_at_ms")
    @androidx.room.ColumnInfo(name = "updated_at_ms")
    override val updatedAt: Long = System.currentTimeMillis(),
    
    @kotlinx.serialization.Transient
    @SerialName("is_synced")
    @androidx.room.ColumnInfo(name = "is_synced")
    override val isSynced: Boolean = true
) : Syncable