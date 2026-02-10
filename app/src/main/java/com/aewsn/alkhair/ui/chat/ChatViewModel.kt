package com.aewsn.alkhair.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aewsn.alkhair.data.manager.ClassDivisionRepoManager
import com.aewsn.alkhair.data.manager.UserRepoManager
import com.aewsn.alkhair.data.repository.ChatRepository
import com.aewsn.alkhair.utils.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.util.UUID

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val userRepoManager: UserRepoManager,
    private val classDivisionRepoManager: ClassDivisionRepoManager
) : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _chatState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val chatState: StateFlow<UiState<Unit>> = _chatState.asStateFlow()

    // Aggregate Auto-Complete Suggestions
    val suggestions: StateFlow<List<String>> = kotlinx.coroutines.flow.combine(
        userRepoManager.observeLocal(),
        classDivisionRepoManager.observeClasses(),
        classDivisionRepoManager.observeDivisions()
    ) { users, classes, divisions ->
        val userNames = users.map { it.name }
        val userEmails = users.map { it.email }
        val userPhones = users.map { it.phone }
        val classNames = classes.map { it.className }
        val divisionNames = divisions.map { it.name }

        (userNames + userEmails + userPhones + classNames + divisionNames)
            .filter { !it.isNullOrBlank() }
            .distinct()
            .sorted()
            .also { 
                android.util.Log.d("ChatViewModel", "Aggregated ${it.size} suggestions") 
            }
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), emptyList())

    // Generate a session ID for this chat session
    private val sessionId = UUID.randomUUID().toString()

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        // 1. Add User Message immediately
        val userMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            text = text,
            isFromUser = true,
            timestamp = System.currentTimeMillis()
        )
        // Correctly update StateFlow list
        _messages.value = _messages.value + userMsg

        // 2. Set Loading State
        _chatState.value = UiState.Loading

        // 3. Call API
        viewModelScope.launch {
            chatRepository.sendMessage(text, sessionId).collect { result ->
                result.fold(
                    onSuccess = { reply ->
                        val botMsg = ChatMessage(
                            id = UUID.randomUUID().toString(),
                            text = reply,
                            isFromUser = false,
                            timestamp = System.currentTimeMillis()
                        )
                        _messages.value = _messages.value + botMsg
                        _chatState.value = UiState.Success(Unit)
                    },
                    onFailure = { error ->
                        val errorMsg = ChatMessage(
                            id = UUID.randomUUID().toString(),
                            text = "Error: ${error.message}",
                            isFromUser = false,
                            timestamp = System.currentTimeMillis(),
                            isError = true
                        )
                        _messages.value = _messages.value + errorMsg
                        _chatState.value = UiState.Error(error.message ?: "Unknown error")
                    }
                )
            }
        }
    }
}

data class ChatMessage(
    val id: String,
    val text: String,
    val isFromUser: Boolean,
    val timestamp: Long,
    val isError: Boolean = false
)
