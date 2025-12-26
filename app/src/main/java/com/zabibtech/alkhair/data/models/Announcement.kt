package com.zabibtech.alkhair.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "announcements")
@com.google.firebase.database.IgnoreExtraProperties
data class Announcement(
    @PrimaryKey
    val id: String = "",
    val title: String = "",
    val content: String = "",
    val timeStamp: Long = 0,
    override val updatedAt: Long = System.currentTimeMillis() // ✅ Override zaroori hai
) : Syncable // ✅ Interface implement karein