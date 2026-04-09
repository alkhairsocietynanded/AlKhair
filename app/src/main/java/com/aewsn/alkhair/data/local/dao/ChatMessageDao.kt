package com.aewsn.alkhair.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aewsn.alkhair.data.models.ChatMessage
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {

    // ✅ WRITE
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<ChatMessage>)

    // ✅ READ — Realtime Flow per group (for UI observation)
    @Query("SELECT * FROM chat_messages WHERE group_id = :groupId ORDER BY updated_at_ms DESC")
    fun observeMessagesByGroup(groupId: String): Flow<List<ChatMessage>>

    // ✅ READ — For WorkManager upload (unsynced messages)
    @Query("SELECT * FROM chat_messages WHERE is_synced = 0")
    suspend fun getUnsyncedMessages(): List<ChatMessage>

    // ✅ SYNC — Mark as synced after upload
    @Query("UPDATE chat_messages SET is_synced = 1 WHERE id IN (:ids)")
    suspend fun markAsSynced(ids: List<String>)

    // ✅ DELETE
    @Query("DELETE FROM chat_messages WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM chat_messages")
    suspend fun clearAll()

    // ✅ Download — Update local cached file path after download
    @Query("UPDATE chat_messages SET local_uri = :localUri WHERE id = :messageId")
    suspend fun updateLocalUri(messageId: String, localUri: String)

    // ✅ Initial sync — fetch latest messages for a group
    @Query("SELECT * FROM chat_messages WHERE group_id = :groupId AND updated_at_ms > :after ORDER BY updated_at_ms ASC")
    suspend fun getMessagesAfter(groupId: String, after: Long): List<ChatMessage>
}
