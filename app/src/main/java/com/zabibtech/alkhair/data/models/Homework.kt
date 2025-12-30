package com.zabibtech.alkhair.data.models

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(tableName = "homework")
@com.google.firebase.database.IgnoreExtraProperties
data class Homework(
    @PrimaryKey
    val id: String = "",
    val classId: String = "",
    val className: String = "",
    val division: String = "",
    val shift: String = "",
    val subject: String = "",
    val title: String = "",
    val description: String = "",
    val date: String = "",
    val teacherId: String = "",
    val attachmentUrl: String? = null,
    override val updatedAt: Long = System.currentTimeMillis() // ✅ Override zaroori hai
) :Parcelable, Syncable // ✅ Interface implement karein
