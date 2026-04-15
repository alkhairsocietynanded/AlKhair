package com.aewsn.alkhair.data.remote.supabase

import android.util.Log
import com.aewsn.alkhair.data.models.ChatMessage
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onCompletion
import javax.inject.Inject
import javax.inject.Singleton

/** Realtime events ke liye sealed class — INSERT ya DELETE */
sealed class ChatRealtimeEvent {
    data class MessageInserted(val message: ChatMessage) : ChatRealtimeEvent()
    data class MessageDeleted(val messageId: String) : ChatRealtimeEvent()
}

@Singleton
class SupabaseChatRepository @Inject constructor(
    private val supabase: SupabaseClient
) {

    companion object {
        private const val TAG = "SupabaseChatRepo"
        private const val TABLE = "chat_messages"
    }

    /**
     * Fetch messages for a specific group after a given timestamp (delta sync)
     */
    suspend fun getMessagesForGroup(groupId: String, after: Long): Result<List<ChatMessage>> {
        return try {
            val list = supabase.from(TABLE).select {
                filter {
                    eq("group_id", groupId)
                    gt("updated_at_ms", after)
                }
                order("updated_at_ms", io.github.jan.supabase.postgrest.query.Order.ASCENDING)
            }.decodeList<ChatMessage>()
            Result.success(list)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching messages for group $groupId", e)
            Result.failure(e)
        }
    }

    /**
     * Batch upsert — used by ChatUploadWorker
     */
    suspend fun saveMessageBatch(messages: List<ChatMessage>): Result<Unit> {
        return try {
            if (messages.isEmpty()) return Result.success(Unit)
            supabase.from(TABLE).upsert(messages)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving batch messages", e)
            Result.failure(e)
        }
    }

    /**
     * Delete a single message from Supabase by ID.
     * The DB trigger will automatically insert into deleted_chat_messages tombstone table.
     */
    suspend fun deleteMessage(messageId: String): Result<Unit> {
        return try {
            supabase.from(TABLE).delete {
                filter { eq("id", messageId) }
            }
            Log.d(TAG, "Message deleted from Supabase: $messageId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting message $messageId from Supabase", e)
            Result.failure(e)
        }
    }

    /**
     * Delete multiple messages from Supabase by IDs.
     */
    suspend fun deleteMessages(messageIds: List<String>): Result<Unit> {
        return try {
            if (messageIds.isEmpty()) return Result.success(Unit)
            supabase.from(TABLE).delete {
                filter { isIn("id", messageIds) }
            }
            Log.d(TAG, "Messages deleted from Supabase: $messageIds")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting messages $messageIds from Supabase", e)
            Result.failure(e)
        }
    }

    /**
     * Tombstone sync — fetch IDs of messages deleted after a given timestamp.
     * Used when device comes back online to catch up on missed deletions.
     */
    suspend fun fetchDeletedMessageIds(groupId: String, after: Long): Result<List<String>> {
        return try {
            @kotlinx.serialization.Serializable
            data class TombstoneRow(
                @kotlinx.serialization.SerialName("message_id") val messageId: String
            )
            val rows = supabase.from("deleted_chat_messages").select {
                filter {
                    eq("group_id", groupId)
                    gt("deleted_at", after)
                }
            }.decodeList<TombstoneRow>()
            Log.d(TAG, "Tombstone: ${rows.size} deleted IDs fetched for group $groupId since $after")
            Result.success(rows.map { it.messageId })
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching deleted messages for group $groupId", e)
            Result.failure(e)
        }
    }

    /**
     * Listen for real-time INSERT + DELETE events for a specific group.
     * Returns a Flow of ChatRealtimeEvent (MessageInserted or MessageDeleted).
     */
    suspend fun listenForGroupMessages(groupId: String): Flow<ChatRealtimeEvent> {
        val channel = supabase.channel("chat_messages_${groupId}")

        // 1️⃣ INSERT flow — naya message aaya
        val insertFlow = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = TABLE
        }.mapNotNull { action ->
            try {
                val message = action.decodeRecord<ChatMessage>()
                if (message.groupId == groupId) ChatRealtimeEvent.MessageInserted(message) else null
            } catch (e: Exception) {
                Log.e(TAG, "Error decoding inserted message", e)
                null
            }
        }

        // 2️⃣ DELETE flow — message delete hua
        val deleteFlow = channel.postgresChangeFlow<PostgresAction.Delete>(schema = "public") {
            table = TABLE
        }.mapNotNull { action ->
            try {
                // DELETE event mein old_record se ID milti hai
                val deletedId = action.oldRecord["id"]?.toString()?.removeSurrounding("\"")
                Log.d(TAG, "Realtime DELETE event received, id=$deletedId")
                if (deletedId != null) ChatRealtimeEvent.MessageDeleted(deletedId) else null
            } catch (e: Exception) {
                Log.e(TAG, "Error decoding deleted message", e)
                null
            }
        }

        supabase.realtime.connect()
        channel.subscribe()

        return merge(insertFlow, deleteFlow).onCompletion {
            channel.unsubscribe()
            supabase.realtime.removeChannel(channel)
        }
    }
}
