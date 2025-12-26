package com.zabibtech.alkhair.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "divisions")
@com.google.firebase.database.IgnoreExtraProperties
data class DivisionModel(
    @PrimaryKey
    val id: String = "",
    val name: String = "",
    override val updatedAt: Long = System.currentTimeMillis() // ✅ Override zaroori hai
) : Syncable // ✅ Interface implement karein