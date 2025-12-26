package com.zabibtech.alkhair.data.models


@com.google.firebase.database.IgnoreExtraProperties
data class DeletedRecord(
    val id: String = "",
    val type: String = "", // "user", "class", "division", etc.
    val timestamp: Long = System.currentTimeMillis()
)
