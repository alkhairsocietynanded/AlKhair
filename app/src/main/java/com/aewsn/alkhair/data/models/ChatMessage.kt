package com.aewsn.alkhair.data.models

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@Parcelize
@Entity(tableName = "chat_messages")
data class ChatMessage(

    @PrimaryKey
    @SerialName("id")
    @ColumnInfo(name = "id")
    var id: String = "",

    // Sender info
    @SerialName("sender_id")
    @ColumnInfo(name = "sender_id")
    var senderId: String = "",

    // sender_name is LOCAL ONLY — not in Supabase table
    // Resolved from local User DB for UI display
    @kotlinx.serialization.Transient
    @ColumnInfo(name = "sender_name")
    var senderName: String = "",

    // Group info
    // group_id: 'teachers_group' (fixed string) ya class UUID string
    @SerialName("group_id")
    @ColumnInfo(name = "group_id")
    var groupId: String = "",

    // group_type: 'teachers' ya 'class'
    @SerialName("group_type")
    @ColumnInfo(name = "group_type")
    var groupType: String = "class",

    // Message content
    @SerialName("message_text")
    @ColumnInfo(name = "message_text")
    var messageText: String = "",

    @SerialName("media_url")
    @ColumnInfo(name = "media_url")
    var mediaUrl: String? = null,

    @SerialName("media_type")
    @ColumnInfo(name = "media_type")
    var mediaType: String? = null, // "image", "document", null

    // Sync fields (Offline-first pattern)
    @SerialName("updated_at_ms")
    @ColumnInfo(name = "updated_at_ms")
    override var updatedAt: Long = System.currentTimeMillis(),

    // is_synced is LOCAL ONLY — not in Supabase table
    @kotlinx.serialization.Transient
    @ColumnInfo(name = "is_synced")
    override var isSynced: Boolean = false,

    // local_uri: LOCAL ONLY — path to cached downloaded media on this device
    // null means media has NOT been downloaded yet (show placeholder)
    @kotlinx.serialization.Transient
    @ColumnInfo(name = "local_uri")
    var localUri: String? = null

) : Parcelable, Syncable {

    // isDownloading is TRANSIENT (in-memory only, not stored in Room)
    // Used by the adapter to show a ProgressBar while download is in progress
    @Ignore
    var isDownloading: Boolean = false
}
