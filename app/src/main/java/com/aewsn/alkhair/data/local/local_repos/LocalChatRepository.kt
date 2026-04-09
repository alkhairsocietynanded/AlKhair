package com.aewsn.alkhair.data.local.local_repos

import com.aewsn.alkhair.data.local.dao.ChatMessageDao
import com.aewsn.alkhair.data.models.ChatMessage
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalChatRepository @Inject constructor(
    private val chatMessageDao: ChatMessageDao
) {

    fun observeMessagesByGroup(groupId: String): Flow<List<ChatMessage>> =
        chatMessageDao.observeMessagesByGroup(groupId)

    suspend fun insertMessage(message: ChatMessage) =
        chatMessageDao.insertMessage(message)

    suspend fun insertMessages(messages: List<ChatMessage>) =
        chatMessageDao.insertMessages(messages)

    suspend fun getUnsyncedMessages(): List<ChatMessage> =
        chatMessageDao.getUnsyncedMessages()

    suspend fun markAsSynced(ids: List<String>) =
        chatMessageDao.markAsSynced(ids)

    suspend fun deleteById(id: String) =
        chatMessageDao.deleteById(id)

    suspend fun updateLocalUri(messageId: String, localUri: String) =
        chatMessageDao.updateLocalUri(messageId, localUri)

    suspend fun clearAll() =
        chatMessageDao.clearAll()
}
