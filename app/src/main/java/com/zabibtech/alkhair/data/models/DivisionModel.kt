package com.zabibtech.alkhair.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "divisions")
data class DivisionModel(
    @PrimaryKey
    val id: String = "",
    val name: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)
