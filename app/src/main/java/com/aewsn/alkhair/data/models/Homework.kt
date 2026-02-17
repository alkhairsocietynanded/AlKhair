package com.aewsn.alkhair.data.models

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@Parcelize
@Entity(tableName = "homework")

data class Homework(
    @PrimaryKey
    val id: String = "",
    @SerialName("class_id")
    @androidx.room.ColumnInfo(name = "class_id")
    val classId: String = "",
    @kotlinx.serialization.Transient
    @SerialName("class_name")
    val className: String = "", // Likely fetched via join
    @kotlinx.serialization.Transient
    val divisionName: String = "",
    val shift: String = "",
    val subject: String = "",
    val title: String = "",
    val description: String = "",
    @SerialName("due_date")
    @androidx.room.ColumnInfo(name = "due_date")
    val date: String = "", // Mapped to due_date in SQL
    @SerialName("user_id")
    @androidx.room.ColumnInfo(name = "user_id")
    val teacherId: String? = null,
    @SerialName("attachment_url")
    @androidx.room.ColumnInfo(name = "attachment_url")
    val attachmentUrl: String? = null,
    @SerialName("updated_at_ms")
    @androidx.room.ColumnInfo(name = "updated_at_ms")
    override val updatedAt: Long = System.currentTimeMillis(),

    @kotlinx.serialization.Transient
    @SerialName("is_synced")
    @androidx.room.ColumnInfo(name = "is_synced")
    override val isSynced: Boolean = true
) :Parcelable, Syncable
