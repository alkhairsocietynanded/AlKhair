package com.zabibtech.alkhair.ui.homework

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
import com.zabibtech.alkhair.databinding.ActivityHomeworkBinding
import com.zabibtech.alkhair.utils.UiState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HomeworkActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeworkBinding
    private val viewModel: HomeworkViewModel by viewModels()
    private lateinit var homeworkAdapter: HomeworkAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityHomeworkBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Apply window insets to handle system bars
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, systemBars.top, v.paddingRight, v.paddingBottom)
            insets
        }

        setupToolbar()
        setupRecyclerView()
        setupListeners()
        observeViewModel()

        // Trigger the initial load of all homework
        viewModel.loadAllHomework()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupRecyclerView() {
        homeworkAdapter = HomeworkAdapter(
            onEdit = { homework ->
                // Show the dialog with existing data to edit.
                // Note: Your AddHomeworkDialog will need a `newInstance` method that
                // accepts a Homework object (ideally as a Parcelable argument).
                val dialog = AddHomeworkDialog() // Replace with your newInstance if available
                val args = Bundle().apply {
                    putParcelable("homework_to_edit", homework)
                }
                dialog.arguments = args
                dialog.show(supportFragmentManager, "EditHomeworkDialog")
            },
            onDelete = { homework ->
                viewModel.deleteHomework(homework)
            }
        )
        binding.rvHomework.apply {
            adapter = homeworkAdapter
            layoutManager = LinearLayoutManager(this@HomeworkActivity)
        }
    }

    private fun setupListeners() {
        binding.fabAddHomework.setOnClickListener {
            AddHomeworkDialog().show(supportFragmentManager, "AddHomeworkDialog")
        }

        binding.swipeRefreshLayout.setOnRefreshListener {
            viewModel.loadAllHomework()
        }
    }

    private fun observeViewModel() {
        // Observer for loading the list of homework
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.homeworkState.collect { state ->
                    binding.swipeRefreshLayout.isRefreshing = state is UiState.Loading
                    binding.progressBar.isVisible = state is UiState.Loading && !binding.swipeRefreshLayout.isRefreshing

                    when (state) {
                        is UiState.Success -> {
                            val homework = state.data
                            val isEmpty = homework.isEmpty()
                            binding.tvEmptyState.isVisible = isEmpty
                            binding.rvHomework.isVisible = !isEmpty
                            if (!isEmpty) {
                                homeworkAdapter.submitList(homework)
                            }
                            binding.tvEmptyState.text = "No homework assigned yet."
                        }
                        is UiState.Error -> {
                            Toast.makeText(this@HomeworkActivity, state.message, Toast.LENGTH_LONG).show()
                            binding.tvEmptyState.text = state.message
                            binding.tvEmptyState.isVisible = homeworkAdapter.itemCount == 0
                        }
                        else -> {
                            // Handles Idle and Loading states where the main content is hidden
                        }
                    }
                }
            }
        }

        // Observer for add, update, or delete operations
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.mutationState.collect { state ->
                    when (state) {
                        is UiState.Loading -> {
                            binding.progressBar.isVisible = true
                        }
                        is UiState.Success -> {
                            binding.progressBar.isVisible = false
                            Toast.makeText(this@HomeworkActivity, "Operation successful", Toast.LENGTH_SHORT).show()
                            viewModel.loadAllHomework() // Refresh the list
                            viewModel.resetMutationState() // Reset state to prevent re-triggering
                        }
                        is UiState.Error -> {
                            binding.progressBar.isVisible = false
                            Toast.makeText(this@HomeworkActivity, state.message, Toast.LENGTH_LONG).show()
                            viewModel.resetMutationState()
                        }
                        is UiState.Idle -> { /* Do nothing */ }
                    }
                }
            }
        }
    }
}