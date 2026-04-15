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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepoManager: ChatRepoManager,
    private val authRepoManager: AuthRepoManager
) : ViewModel() {

    private val _currentUserIdFlow = MutableStateFlow<String>("")
    val currentUserIdFlow: StateFlow<String> = _currentUserIdFlow

    val currentUserId: String
        get() = authRepoManager.getCurrentUserUid() ?: _currentUserIdFlow.value

    init {
        viewModelScope.launch {
            _currentUserIdFlow.value = authRepoManager.getLocalLoginUid() ?: ""
        }
    }

    private val _messagesState = MutableStateFlow<UiState<List<ChatMessage>>>(UiState.Loading)
    val messagesState: StateFlow<UiState<List<ChatMessage>>> = _messagesState

    private val _sendState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val sendState: StateFlow<UiState<Unit>> = _sendState

    private val _messageLimit = MutableStateFlow(50)

    private var currentGroupId: String = ""
    private var currentGroupType: String = ""

    fun loadMoreMessages() {
        _messageLimit.value += 50
    }

    /**
     * Start observing messages for a group + initial sync from Supabase
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun observeMessages(groupId: String, groupType: String, context: android.content.Context) {
        currentGroupId = groupId
        currentGroupType = groupType

        // 1. Observe local DB (SSOT)
        viewModelScope.launch {
            _messageLimit.flatMapLatest { limit ->
                chatRepoManager.observeMessagesByGroup(groupId, limit)
            }.catch { e ->
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

        // 3. Subscribe to real-time events (SDK handles its own heartbeat + reconnect)
        viewModelScope.launch {
            chatRepoManager.startRealtimeSubscription(groupId)
        }

        // 4. Network-aware re-sync: jab network wapas aaye missed messages fetch karo
        registerNetworkCallback(context, groupId)
    }

    // ─── Network Callback ────────────────────────────────────────────────────

    private var networkCallback: android.net.ConnectivityManager.NetworkCallback? = null
    private var lastSyncTimestamp: Long = 0L

    private fun registerNetworkCallback(context: android.content.Context, groupId: String) {
        val connectivityManager = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE)
            as android.net.ConnectivityManager

        // Unregister any previous callback to avoid duplicates
        unregisterNetworkCallback(context)

        networkCallback = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) {
                android.util.Log.d("ChatViewModel", "✅ Network restored — re-syncing missed messages")
                viewModelScope.launch {
                    // Fetch messages since last known sync time to catch up on missed messages
                    chatRepoManager.syncGroupMessages(groupId, after = lastSyncTimestamp)
                    lastSyncTimestamp = System.currentTimeMillis()
                }
            }
        }

        val request = android.net.NetworkRequest.Builder()
            .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, networkCallback!!)
        lastSyncTimestamp = System.currentTimeMillis()
        android.util.Log.d("ChatViewModel", "NetworkCallback registered for group $groupId")
    }

    fun unregisterNetworkCallback(context: android.content.Context) {
        networkCallback?.let {
            try {
                val cm = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE)
                    as android.net.ConnectivityManager
                cm.unregisterNetworkCallback(it)
            } catch (e: Exception) {
                android.util.Log.w("ChatViewModel", "NetworkCallback already unregistered")
            }
            networkCallback = null
        }
    }


    /**
     * Send a text message
     */
    fun sendMessage(
        text: String,
        senderName: String,
        mediaUri: String? = null,
        mimeType: String? = null,
        mediaFileName: String? = null
    ) {
        if (text.isBlank() && mediaUri == null) return

        viewModelScope.launch {
            var senderId = authRepoManager.getCurrentUserUid()
             if (senderId == null) {
                 senderId = authRepoManager.getLocalLoginUid()
             }
             if (senderId == null) return@launch

            _sendState.value = UiState.Loading
            val result = chatRepoManager.sendMessage(
                messageText = text.trim(),
                groupId = currentGroupId,
                groupType = currentGroupType,
                senderId = senderId,
                senderName = senderName,
                mediaUri = mediaUri,
                mimeType = mimeType,
                mediaFileName = mediaFileName
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

    private val _deleteState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val deleteState: StateFlow<UiState<Unit>> = _deleteState

    fun deleteMessages(messageIds: List<String>) {
        if (messageIds.isEmpty()) return
        viewModelScope.launch {
            _deleteState.value = UiState.Loading
            val result = chatRepoManager.deleteMessages(messageIds)
            _deleteState.value = if (result.isSuccess) {
                UiState.Success(Unit)
            } else {
                UiState.Error("Failed to delete messages: ${result.exceptionOrNull()?.message ?: "Unknown error"}")
            }
        }
    }

    fun resetDeleteState() {
        _deleteState.value = UiState.Idle
    }

    /* ============================================================
       ⬇️ DOWNLOAD MEDIA
       ============================================================ */

    // In-memory Set of message IDs currently being downloaded
    // This drives the ProgressBar in the adapter (no Room needed)
    private val _downloadingIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadingIds: StateFlow<Set<String>> = _downloadingIds

    fun downloadMedia(message: com.aewsn.alkhair.data.models.ChatMessage) {
        val msgId = message.id
        // Avoid duplicate downloads
        if (_downloadingIds.value.contains(msgId)) return

        viewModelScope.launch {
            // Mark as downloading → adapter shows ProgressBar
            _downloadingIds.value = _downloadingIds.value + msgId

            chatRepoManager.downloadMedia(message).onFailure { e ->
                android.util.Log.e("ChatViewModel", "Download failed: ${e.message}")
            }

            // Remove from downloading set regardless of success/failure
            // On success: Room Flow fires → adapter gets updated localUri → shows actual media
            // On failure: ProgressBar disappears → download icon reappears
            _downloadingIds.value = _downloadingIds.value - msgId
        }
    }
}
