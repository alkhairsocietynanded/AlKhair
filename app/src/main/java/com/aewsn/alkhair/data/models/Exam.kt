package com.aewsn.alkhair.data.models

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@Parcelize
@Entity(tableName = "exams")
data class Exam(
    @PrimaryKey
    var id: String = "",

    @SerialName("title")
    var title: String = "",

    @SerialName("start_date")
    @ColumnInfo(name = "start_date")
    var startDate: Long = 0L,

    @SerialName("end_date")
    @ColumnInfo(name = "end_date")
    var endDate: Long = 0L,

    @SerialName("session")
    var session: String? = null,

    @SerialName("class_id")
    @ColumnInfo(name = "class_id")
    var classId: String? = null,

    @SerialName("is_published")
    @ColumnInfo(name = "is_published")
    var isPublished: Boolean = false,

    @SerialName("updated_at_ms")
    @ColumnInfo(name = "updated_at_ms")
    override var updatedAt: Long = System.currentTimeMillis(),

    @kotlinx.serialization.Transient
    @SerialName("is_synced")
    @ColumnInfo(name = "is_synced")
    override val isSynced: Boolean = true
) : Parcelable, Syncable
