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
import com.google.android.material.snackbar.Snackbar
import com.zabibtech.alkhair.databinding.ActivityHomeworkBinding
import com.zabibtech.alkhair.utils.DialogUtils
import com.zabibtech.alkhair.utils.LoadingDialogFragment
import com.zabibtech.alkhair.utils.UiState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HomeworkActivity : AppCompatActivity(), LoadingDialogFragment.OnLoadingFinishedListener {

    private lateinit var binding: ActivityHomeworkBinding
    private val viewModel: HomeworkViewModel by viewModels()
    private lateinit var homeworkAdapter: HomeworkAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityHomeworkBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()
        setupToolbar()
        setupRecyclerView()
        setupListeners()
        observeViewModel()
    }

    /* ============================================================
       🔧 UI SETUP
       ============================================================ */

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
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
                AddHomeworkDialog.newInstance(homework)
                    .show(supportFragmentManager, "EditHomeworkDialog")
            },
            onDelete = { homework ->
                DialogUtils.showConfirmation(
                    context = this@HomeworkActivity,
                    title = "Delete Homework",
                    message = "Are you sure you want to delete '${homework.title}'?",
                    positiveText = "Delete",
                    negativeText = "Cancel",
                    onConfirmed = {
                        viewModel.deleteHomework(homework.id)
                    }
                )
            }
        )

        binding.rvHomework.apply {
            layoutManager = LinearLayoutManager(this@HomeworkActivity)
            adapter = homeworkAdapter
            setHasFixedSize(true)
        }
    }

    private fun setupListeners() {
        binding.fabAddHomework.setOnClickListener {
            AddHomeworkDialog.newInstance()
                .show(supportFragmentManager, "AddHomeworkDialog")
        }

        binding.swipeRefreshLayout.setOnRefreshListener {
            // SSOT → Room → UI, no direct remote call
            binding.swipeRefreshLayout.isRefreshing = false
        }
    }

    /* ============================================================
       👀 STATE OBSERVERS
       ============================================================ */

    private fun observeViewModel() {

        // 📦 Homework List (SSOT)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.homeworkListState.collect { state ->

                    binding.swipeRefreshLayout.isRefreshing =
                        state is UiState.Loading

                    when (state) {
                        is UiState.Success -> {
                            val list = state.data
                            homeworkAdapter.submitList(list)

                            binding.rvHomework.isVisible = list.isNotEmpty()
                            binding.tvEmptyState.isVisible = list.isEmpty()
                            binding.tvEmptyState.text =
                                if (list.isEmpty()) "No homework assigned yet"
                                else ""
                        }

                        is UiState.Error -> {
                            Toast.makeText(
                                this@HomeworkActivity,
                                state.message,
                                Toast.LENGTH_LONG
                            ).show()

                            binding.tvEmptyState.isVisible =
                                homeworkAdapter.itemCount == 0
                            binding.tvEmptyState.text = state.message
                        }

                        UiState.Loading -> Unit
                        UiState.Idle -> Unit
                    }
                }
            }
        }

        // ✍️ Create / Update / Delete Result
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.deleteMutationState.collect { state ->
                    when (state) {
                        is UiState.Success -> {
                            DialogUtils.hideLoading(supportFragmentManager)
                            Toast.makeText(
                                this@HomeworkActivity,
                                "Deleted successfully",
                                Toast.LENGTH_SHORT
                            ).show()
                            viewModel.resetDeleteMutationState()
                        }

                        is UiState.Error -> {
                            DialogUtils.hideLoading(supportFragmentManager)
                            Toast.makeText(
                                this@HomeworkActivity,
                                state.message,
                                Toast.LENGTH_LONG
                            ).show()
                            viewModel.resetDeleteMutationState()
                        }

                        UiState.Loading -> DialogUtils.showLoading(
                            supportFragmentManager, 
                            "Deleting...", 
                            10000
                        )
                        UiState.Idle -> Unit
                    }
                }
            }
        }
    }

    override fun onLoadingFinished() {
        Snackbar.make(binding.root, "Request timed out. Please try again.", Snackbar.LENGTH_LONG).show()
    }
}
