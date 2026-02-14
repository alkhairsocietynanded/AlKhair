package com.aewsn.alkhair.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import androidx.room.ColumnInfo

@Serializable
@Entity(tableName = "app_config")
data class AppConfig(
    @PrimaryKey
    @SerialName("key")
    @ColumnInfo(name = "key")
    val key: String, // Acts as ID

    @SerialName("value")
    @ColumnInfo(name = "value")
    val value: String,

    @SerialName("description")
    @ColumnInfo(name = "description")
    val description: String? = null,

    @SerialName("updated_at")
    @ColumnInfo(name = "updated_at")
    override var updatedAt: Long = System.currentTimeMillis(),

    @kotlinx.serialization.Transient
    @SerialName("is_synced")
    @ColumnInfo(name = "is_synced")
    override var isSynced: Boolean = true
) : Syncable
