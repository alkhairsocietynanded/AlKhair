package com.zabibtech.alkhair.data.models

interface Syncable {
    val updatedAt: Long
    @get:com.google.firebase.database.Exclude
    val isSynced: Boolean
}