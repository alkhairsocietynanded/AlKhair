package com.aewsn.alkhair.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "divisions")

data class DivisionModel(
    @PrimaryKey
    val id: String = "",
    val name: String = "",
    @SerialName("updated_at_ms")
    @androidx.room.ColumnInfo(name = "updated_at_ms")
    override val updatedAt: Long = System.currentTimeMillis(),
    
    @kotlinx.serialization.Transient
    @SerialName("is_synced")
    @androidx.room.ColumnInfo(name = "is_synced")
    override val isSynced: Boolean = true
) : Syncable {
    override fun toString(): String = name
}