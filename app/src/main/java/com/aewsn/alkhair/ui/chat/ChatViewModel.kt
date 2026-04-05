package com.aewsn.alkhair.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aewsn.alkhair.data.manager.AuthRepoManager
import com.aewsn.alkhair.data.manager.ChatRepoManager
import com.aewsn.alkhair.data.models.ChatMessage
import com.aewsn.alkhair.utils.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepoManager: ChatRepoManager,
    private val authRepoManager: AuthRepoManager
) : ViewModel() {

    val currentUserId: String
        get() = authRepoManager.getCurrentUserUid() ?: ""

    private val _messagesState = MutableStateFlow<UiState<List<ChatMessage>>>(UiState.Loading)
    val messagesState: StateFlow<UiState<List<ChatMessage>>> = _messagesState

    private val _sendState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val sendState: StateFlow<UiState<Unit>> = _sendState

    private var currentGroupId: String = ""
    private var currentGroupType: String = ""

    /**
     * Start observing messages for a group + initial sync from Supabase
     */
    fun observeMessages(groupId: String, groupType: String) {
        currentGroupId = groupId
        currentGroupType = groupType

        // 1. Observe local DB (SSOT)
        viewModelScope.launch {
            chatRepoManager.observeMessagesByGroup(groupId)
                .catch { e ->
                    _messagesState.value = UiState.Error(e.message ?: "Error loading messages")
                }
                .collectLatest { messages ->
                    _messagesState.value = UiState.Success(messages)
                }
        }

        // 2. Initial sync from remote
        viewModelScope.launch {
            chatRepoManager.syncGroupMessages(groupId, after = 0L)
        }

        // 3. Subscribe to real-time events
        viewModelScope.launch {
            chatRepoManager.startRealtimeSubscription(groupId)
        }
    }

    /**
     * Send a text message
     */
    fun sendMessage(
        text: String,
        senderName: String
    ) {
        if (text.isBlank()) return

        val senderId = authRepoManager.getCurrentUserUid() ?: return

        viewModelScope.launch {
            _sendState.value = UiState.Loading
            val result = chatRepoManager.sendMessage(
                messageText = text.trim(),
                groupId = currentGroupId,
                groupType = currentGroupType,
                senderId = senderId,
                senderName = senderName
            )
            _sendState.value = if (result.isSuccess) {
                UiState.Success(Unit)
            } else {
                UiState.Error(result.exceptionOrNull()?.message ?: "Failed to send message")
            }
        }
    }

    fun resetSendState() {
        _sendState.value = UiState.Idle
    }
}
