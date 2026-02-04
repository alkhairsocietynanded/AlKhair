package com.aewsn.alkhair.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_deletions")
data class PendingDeletion(
    @PrimaryKey
    val id: String,
    val type: String, // e.g., "FEES"
    val timestamp: Long = System.currentTimeMillis()
)
