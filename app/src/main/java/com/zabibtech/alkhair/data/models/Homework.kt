package com.zabibtech.alkhair.data.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Homework(
    val id: String = "",
    val className: String = "",
    val division: String = "",
    val shift: String = "",
    val subject: String = "",
    val title: String = "",
    val description: String = "",
    val date: String = "",
    val teacherId: String = "",
    val attachmentUrl: String? = null
) : Parcelable
