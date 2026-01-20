package com.zabibtech.alkhair.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "classes")
@com.google.firebase.database.IgnoreExtraProperties
data class ClassModel(
    @PrimaryKey
    val id: String = "",
    val division: String = "",
    val className: String = "",
    override val updatedAt: Long = System.currentTimeMillis(), // ✅ Override zaroori hai
    @get:com.google.firebase.database.Exclude
    override val isSynced: Boolean = true
) : Syncable // ✅ Interface implement karein