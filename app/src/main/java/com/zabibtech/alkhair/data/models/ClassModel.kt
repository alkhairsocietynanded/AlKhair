package com.zabibtech.alkhair.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "classes")
data class ClassModel(
    @PrimaryKey
    val id: String = "",
    val division: String = "",
    val className: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)