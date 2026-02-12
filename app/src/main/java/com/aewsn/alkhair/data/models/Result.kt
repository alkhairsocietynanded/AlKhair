package com.aewsn.alkhair.data.models

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity

import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@Parcelize
@Entity(
    tableName = "results",
    // FK constraints removed: They cause crashes during partial/role-based sync
    // when referenced Users/Exams/Subjects are not yet in local DB.
    // Data integrity is maintained by Supabase (server-side).
    indices = [
        Index(value = ["exam_id"]),
        Index(value = ["student_id"]),
        Index(value = ["subject_id"]),
        Index(value = ["exam_id", "student_id", "subject_id"], unique = true)
    ]
)
data class Result(
    @PrimaryKey
    var id: String = "",

    @SerialName("exam_id")
    @ColumnInfo(name = "exam_id")
    var examId: String = "",

    @SerialName("student_id")
    @ColumnInfo(name = "student_id")
    var studentId: String = "",

    @SerialName("subject_id")
    @ColumnInfo(name = "subject_id")
    var subjectId: String = "",

    @SerialName("marks_obtained")
    @ColumnInfo(name = "marks_obtained")
    var marksObtained: Double = 0.0,

    @SerialName("total_marks")
    @ColumnInfo(name = "total_marks")
    var totalMarks: Double = 100.0,

    @SerialName("grade")
    var grade: String? = null,

    @SerialName("remarks")
    var remarks: String? = null,

    @SerialName("updated_at")
    @ColumnInfo(name = "updated_at")
    override var updatedAt: Long = System.currentTimeMillis(),

    @kotlinx.serialization.Transient
    @SerialName("is_synced")
    @ColumnInfo(name = "is_synced")
    override var isSynced: Boolean = true
) : Parcelable, Syncable
