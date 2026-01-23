package com.zabibtech.alkhair.data.models

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
    // division name might be needed for UI, but it's not in the 'classes' table.
    // We can keep a field for it if we join, or just fetch it separately.
    // For now, keeping it as 'division' but it might be null if not joined.
    val division: String = "", 
    @SerialName("class_name")
    val className: String = "",
    @SerialName("updated_at")
    override val updatedAt: Long = System.currentTimeMillis(),
    @SerialName("is_synced")

    override val isSynced: Boolean = true
) : Syncable