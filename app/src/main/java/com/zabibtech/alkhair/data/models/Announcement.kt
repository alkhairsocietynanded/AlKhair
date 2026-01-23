package com.zabibtech.alkhair.data.models

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
    val timeStamp: Long = 0, 
    @SerialName("target_id")
    val target: String = "ALL", // Mapped to target_id
    @SerialName("updated_at")
    override val updatedAt: Long = System.currentTimeMillis(),
    @SerialName("is_synced")

    override val isSynced: Boolean = true
) : Syncable