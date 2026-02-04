package com.aewsn.alkhair.data.models

interface Syncable {
    val updatedAt: Long

    val isSynced: Boolean
}