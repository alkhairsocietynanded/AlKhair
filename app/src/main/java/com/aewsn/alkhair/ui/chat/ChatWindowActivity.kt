package com.aewsn.alkhair.ui.chat

import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.aewsn.alkhair.data.models.ChatMessage
import com.aewsn.alkhair.databinding.ActivityChatWindowBinding
import com.aewsn.alkhair.utils.Roles
import com.aewsn.alkhair.utils.UiState
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ChatWindowActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_GROUP_ID = "group_id"
        const val EXTRA_GROUP_TYPE = "group_type"
        const val EXTRA_GROUP_NAME = "group_name"
        const val EXTRA_SENDER_NAME = "sender_name"
        const val EXTRA_USER_ROLE = "user_role"
    }

    private lateinit var binding: ActivityChatWindowBinding
    private val viewModel: ChatViewModel by viewModels()
    private lateinit var adapter: ChatMessageAdapter

    private var groupId: String = ""
    private var groupType: String = ""
    private var groupName: String = ""
    private var senderName: String = ""
    private var userRole: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityChatWindowBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Extract intent extras
        groupId = intent.getStringExtra(EXTRA_GROUP_ID) ?: ""
        groupType = intent.getStringExtra(EXTRA_GROUP_TYPE) ?: ""
        groupName = intent.getStringExtra(EXTRA_GROUP_NAME) ?: "Chat"
        senderName = intent.getStringExtra(EXTRA_SENDER_NAME) ?: ""
        userRole = intent.getStringExtra(EXTRA_USER_ROLE) ?: ""

        if (groupId.isEmpty()) {
            Toast.makeText(this, "Invalid chat group", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupWindowInsets()
        setupToolbar()
        setupRecyclerView()
        setupSendButton()
        observeMessages()
        observeSendState()
        observeDeleteState()

        // Start observing messages for this group
        viewModel.observeMessages(groupId, groupType)
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            
            // Padding the root for system status bar (top)
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            
            val bottomInset = if (ime.bottom > 0) ime.bottom else systemBars.bottom
            
            val density = resources.displayMetrics.density
            val hPadding = (16 * density).toInt()
            val vPadding = (8 * density).toInt()
            
            // Preserve horizontal and top padding while adjusting bottom for insets
            binding.inputBarContainer.setPadding(hPadding, vPadding, hPadding, vPadding + bottomInset)
            
            // Adjust RecyclerView padding so messages aren't hidden behind the floating bar
            val floatingBarHeight = (80 * density).toInt() 
            binding.rvMessages.setPadding(0, (8 * density).toInt(), 0, bottomInset + floatingBarHeight)
            
            insets
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = groupName
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun setupRecyclerView() {
        val isAdmin = userRole.equals(Roles.ADMIN, ignoreCase = true)

        adapter = ChatMessageAdapter(
            currentUserId = viewModel.currentUserId,
            isAdmin = isAdmin,
            onMessageLongPressed = { message -> showDeleteOptions(message) }
        )
        val layoutManager = LinearLayoutManager(this).apply {
            reverseLayout = true
        }
        binding.rvMessages.layoutManager = layoutManager
        binding.rvMessages.adapter = adapter
    }

    private fun showDeleteOptions(message: ChatMessage) {
        val isOwnMessage = message.senderId == viewModel.currentUserId
        val isAdmin = userRole.equals(Roles.ADMIN, ignoreCase = true)

        // Only show delete if it's own message OR admin
        if (!isOwnMessage && !isAdmin) return

        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Message")
            .setMessage("\"${message.messageText.take(60)}${if (message.messageText.length > 60) "…" else ""}\"")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteMessage(message.id)
            }
            .show()
    }

    private fun setupSendButton() {
        binding.btnSend.setOnClickListener {
            val text = binding.etMessage.text?.toString()?.trim() ?: ""
            if (text.isNotEmpty()) {
                viewModel.sendMessage(text, senderName)
                binding.etMessage.text?.clear()
            }
        }
    }

    private fun observeMessages() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.messagesState.collectLatest { state ->
                    when (state) {
                        is UiState.Success -> {
                            val messages = state.data

                            adapter.submitList(messages) {
                                // Scroll to bottom after new messages (index 0 is newest now due to reverseLayout)
                                if (messages.isNotEmpty()) {
                                    binding.rvMessages.scrollToPosition(0)
                                }
                            }

                            binding.rvMessages.isVisible = messages.isNotEmpty()
                            binding.emptyState.isVisible = messages.isEmpty()
                        }

                        is UiState.Error -> {
                            binding.emptyState.isVisible = true
                        }

                        is UiState.Loading -> Unit

                        else -> Unit
                    }
                }
            }
        }
    }

    private fun observeSendState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.sendState.collectLatest { state ->
                    when (state) {
                        is UiState.Error -> {
                            Toast.makeText(
                                this@ChatWindowActivity,
                                state.message,
                                Toast.LENGTH_SHORT
                            ).show()
                            viewModel.resetSendState()
                        }

                        is UiState.Success -> {
                            viewModel.resetSendState()
                        }

                        else -> Unit
                    }
                }
            }
        }
    }

    private fun observeDeleteState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.deleteState.collectLatest { state ->
                    when (state) {
                        is UiState.Success -> {
                            Toast.makeText(this@ChatWindowActivity, "Message deleted", Toast.LENGTH_SHORT).show()
                            viewModel.resetDeleteState()
                        }
                        is UiState.Error -> {
                            Toast.makeText(this@ChatWindowActivity, "Failed to delete: ${state.message}", Toast.LENGTH_SHORT).show()
                            viewModel.resetDeleteState()
                        }
                        else -> Unit
                    }
                }
            }
        }
    }
}
