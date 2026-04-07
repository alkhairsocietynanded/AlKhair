package com.aewsn.alkhair.ui.chat

import android.content.Intent
import android.os.Bundle
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
import com.aewsn.alkhair.databinding.ActivityChatListBinding
import com.aewsn.alkhair.utils.Roles
import com.aewsn.alkhair.utils.UiState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ChatListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatListBinding
    private val viewModel: ChatListViewModel by viewModels()
    private lateinit var adapter: ChatGroupAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityChatListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()
        setupToolbar()
        setupRecyclerView()
        observeGroups()
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            
            // Apply status bar padding to the AppBarLayout
            binding.appBarLayout.setPadding(0, systemBars.top, 0, 0)
            
            // Apply navigation bar padding to the RecyclerView
            binding.rvChatGroups.setPadding(
                binding.rvChatGroups.paddingLeft,
                binding.rvChatGroups.paddingTop,
                binding.rvChatGroups.paddingRight,
                systemBars.bottom
            )

            // ✅ Apply bottom margin to FAB so it stays above the navigation bar
            val params = binding.fabAskAi.layoutParams as android.view.ViewGroup.MarginLayoutParams
            params.bottomMargin = systemBars.bottom + (16 * resources.displayMetrics.density).toInt()
            binding.fabAskAi.layoutParams = params
            
            insets
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        
        binding.fabAskAi.setOnClickListener {
            startActivity(Intent(this, com.aewsn.alkhair.ui.aichat.AiChatActivity::class.java))
        }
    }

    private fun setupRecyclerView() {
        adapter = ChatGroupAdapter { group ->
            openChatWindow(group)
        }
        binding.rvChatGroups.layoutManager = LinearLayoutManager(this)
        binding.rvChatGroups.adapter = adapter

        binding.swipeRefreshLayout.setOnRefreshListener {
            viewModel.loadGroups()
        }
    }

    private fun observeGroups() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.groupsState.collectLatest { state ->
                    binding.swipeRefreshLayout.isRefreshing = state is UiState.Loading

                    when (state) {
                        is UiState.Success -> {
                            val groups = state.data

                            // Student shortcut: If only 1 group, redirect directly
                            if (viewModel.currentUserRole.equals(Roles.STUDENT, true)
                                && groups.size == 1
                            ) {
                                openChatWindow(groups.first())
                                finish()
                                return@collectLatest
                            }

                            adapter.submitList(groups)
                            binding.rvChatGroups.isVisible = groups.isNotEmpty()
                            binding.emptyState.isVisible = groups.isEmpty()
                        }

                        is UiState.Error -> {
                            binding.emptyState.isVisible = true
                            binding.rvChatGroups.isVisible = false
                        }

                        else -> Unit
                    }
                }
            }
        }
    }

    private fun openChatWindow(group: ChatGroup) {
        val intent = Intent(this, ChatWindowActivity::class.java).apply {
            putExtra(ChatWindowActivity.EXTRA_GROUP_ID, group.groupId)
            putExtra(ChatWindowActivity.EXTRA_GROUP_TYPE, group.groupType)
            putExtra(ChatWindowActivity.EXTRA_GROUP_NAME, group.groupName)
            putExtra(ChatWindowActivity.EXTRA_SENDER_NAME, viewModel.currentUserName)
            putExtra(ChatWindowActivity.EXTRA_USER_ROLE, viewModel.currentUserRole)
        }
        startActivity(intent)
    }
}
