package com.aewsn.alkhair.data.models

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@Parcelize
@Entity(tableName = "study_materials")
data class StudyMaterial(
    @PrimaryKey
    val id: String = "",
    @SerialName("class_id")
    @androidx.room.ColumnInfo(name = "class_id")
    val classId: String = "",
    val subject: String = "",
    val title: String = "",
    val description: String = "",
    @SerialName("material_type")
    @androidx.room.ColumnInfo(name = "material_type")
    val materialType: String = "PDF", // PDF, Notes, Video, Link
    @SerialName("attachment_url")
    @androidx.room.ColumnInfo(name = "attachment_url")
    val attachmentUrl: String? = null,
    @SerialName("user_id")
    @androidx.room.ColumnInfo(name = "user_id")
    val teacherId: String? = null,

    @SerialName("updated_at_ms")
    @androidx.room.ColumnInfo(name = "updated_at_ms")
    override val updatedAt: Long = System.currentTimeMillis(),

    @kotlinx.serialization.Transient
    @SerialName("is_synced")
    @androidx.room.ColumnInfo(name = "is_synced")
    override val isSynced: Boolean = true
) : Parcelable, Syncable
