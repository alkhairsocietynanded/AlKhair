package com.aewsn.alkhair.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "classes")

data class ClassModel(
    @PrimaryKey
    val id: String = "",
    @SerialName("division_id")
    val divisionId: String? = null,
    @kotlinx.serialization.Transient
    val divisionName: String = "",
    @SerialName("class_name")
    val className: String = "",
    @SerialName("updated_at")
    override val updatedAt: Long = System.currentTimeMillis(),
    @kotlinx.serialization.Transient
    @SerialName("is_synced")
    override val isSynced: Boolean = true
) : Syncable {
    override fun toString(): String = className
}