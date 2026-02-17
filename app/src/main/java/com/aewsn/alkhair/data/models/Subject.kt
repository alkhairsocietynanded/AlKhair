package com.aewsn.alkhair.data.models

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@Parcelize
@Entity(tableName = "subjects")
data class Subject(
    @PrimaryKey
    var id: String = "",
    
    @SerialName("name")
    var name: String = "",
    
    @SerialName("code")
    var code: String = "", // Optional subject code (e.g., MATH101)
    
    @SerialName("updated_at_ms")
    @androidx.room.ColumnInfo(name = "updated_at_ms")
    override var updatedAt: Long = System.currentTimeMillis(),
    
    @kotlinx.serialization.Transient
    @SerialName("is_synced")
    @androidx.room.ColumnInfo(name = "is_synced")
    override var isSynced: Boolean = true
) : Parcelable, Syncable {
    override fun toString(): String = name
}
