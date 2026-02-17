package com.aewsn.alkhair.data.models

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@Parcelize
@Entity(tableName = "timetable")
data class Timetable(
    @PrimaryKey
    var id: String = "",
    
    @SerialName("class_id")
    @androidx.room.ColumnInfo(name = "class_id")
    var classId: String = "",
    
    @SerialName("subject_id")
    @androidx.room.ColumnInfo(name = "subject_id")
    var subjectId: String = "",
    
    @SerialName("user_id")
    @androidx.room.ColumnInfo(name = "user_id")
    var teacherId: String = "",
    
    @SerialName("day_of_week")
    @androidx.room.ColumnInfo(name = "day_of_week")
    var dayOfWeek: String = "", // Monday, Tuesday, etc.
    
    @SerialName("period_index")
    @androidx.room.ColumnInfo(name = "period_index")
    var periodIndex: Int = 0, // 1, 2, 3... used for sorting
    
    @SerialName("start_time")
    @androidx.room.ColumnInfo(name = "start_time")
    var startTime: String = "", // "09:00 AM"
    
    @SerialName("end_time")
    @androidx.room.ColumnInfo(name = "end_time")
    var endTime: String = "", // "10:00 AM"

    @SerialName("room_no")
    @androidx.room.ColumnInfo(name = "room_no")
    var roomNo: String = "",
    
    // Transient fields for UI (joined data)
    @kotlinx.serialization.Transient
    var subjectName: String = "",
    
    @kotlinx.serialization.Transient
    var teacherName: String = "",
    
    @SerialName("updated_at_ms")
    @androidx.room.ColumnInfo(name = "updated_at_ms")
    override var updatedAt: Long = System.currentTimeMillis(),
    
    @SerialName("is_synced")
    @androidx.room.ColumnInfo(name = "is_synced")
    override var isSynced: Boolean = true
) : Parcelable, Syncable
