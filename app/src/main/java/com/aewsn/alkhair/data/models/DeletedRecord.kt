package com.aewsn.alkhair.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable

data class DeletedRecord(
    // User DB Schema: id (text), type (text), timestamp (bigint)
    @SerialName("id")
    val recordId: String = "", // Maps DB 'id' -> Kotlin 'recordId'
    
    @SerialName("type")
    val type: String = "", 
    
    @SerialName("updated_at_ms")
    val timestamp: Long = 0L
)
