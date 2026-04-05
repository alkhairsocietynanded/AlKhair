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
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.mapNotNull
import javax.inject.Inject
import javax.inject.Singleton

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
     * Listen for real-time inserts for a specific group
     */
    suspend fun listenForGroupMessages(groupId: String): kotlinx.coroutines.flow.Flow<ChatMessage> {
        val channel = supabase.channel("chat_messages_$groupId")
        
        val changeFlow = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = TABLE
        }
        
        // Connect and join if not already connected
        supabase.realtime.connect()
        // join() completes when subscribed
        channel.subscribe()

        return changeFlow.mapNotNull { action ->
            try {
                val message = action.decodeRecord<ChatMessage>()
                if (message.groupId == groupId) message else null
            } catch (e: Exception) {
                Log.e(TAG, "Error decoding realtime message", e)
                null
            }
        }.onCompletion {
            channel.unsubscribe()
            supabase.realtime.removeChannel(channel)
        }
    }
}
