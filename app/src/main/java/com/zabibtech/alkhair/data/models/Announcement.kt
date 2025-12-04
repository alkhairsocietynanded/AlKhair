package com.zabibtech.alkhair.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "announcements")
data class Announcement(
    @PrimaryKey
    val id: String = "",
    val title: String = "",
    val content: String = "",
    val timeStamp: Long = 0,
    val updatedAt: Long = System.currentTimeMillis()
)
