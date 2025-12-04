package com.zabibtech.alkhair.ui.classmanager

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
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.zabibtech.alkhair.data.models.DivisionModel
import com.zabibtech.alkhair.databinding.ActivityClassManagerBinding
import com.zabibtech.alkhair.ui.attendance.AttendanceActivity
import com.zabibtech.alkhair.ui.user.UserListActivity
import com.zabibtech.alkhair.utils.DialogUtils
import com.zabibtech.alkhair.utils.Modes
import com.zabibtech.alkhair.utils.Roles
import com.zabibtech.alkhair.utils.UiState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ClassManagerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityClassManagerBinding
    private val viewModel: ClassManagerViewModel by viewModels()
    private lateinit var adapter: ClassManagerAdapter

    private var divisions: List<DivisionModel> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityClassManagerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupToolbar()
        setupRecyclerView()
        setupFab()
        setupSwipeRefreshLayout()
        observeViewModel()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupRecyclerView() {
        adapter = ClassManagerAdapter(
            onEdit = { classModel ->
                val addClassSheet = AddClassSheet.newInstance(
                    divisions = divisions.map { it.name },
                    existingDivision = classModel.division,
                    existingClassName = classModel.className,
                    existingClassId = classModel.id
                )
                addClassSheet.show(supportFragmentManager, AddClassSheet.TAG)
            },
            onDelete = { classModel ->
                DialogUtils.showConfirmation(
                    context = this,
                    title = "Delete Class",
                    message = "Are you sure you want to delete ${classModel.className}?",
                    onConfirmed = { viewModel.deleteClass(classModel.id) }
                )
            },
            onClick = { classModel ->
                val mode = intent.getStringExtra("mode") ?: Modes.CREATE
                val role = intent.getStringExtra("role") ?: Roles.STUDENT
                val targetIntent = if (mode == Modes.ATTENDANCE) {
                    Intent(this@ClassManagerActivity, AttendanceActivity::class.java)
                } else {
                    Intent(this@ClassManagerActivity, UserListActivity::class.java)
                }

                targetIntent.apply {
                    putExtra("mode", mode)
                    putExtra("role", role)
                    putExtra("classId", classModel.id)
                    putExtra("className", classModel.className)
                    putExtra("division", classModel.division)
                }
                startActivity(targetIntent)
            }
        )

        val gridLayoutManager = GridLayoutManager(this, 2)
        gridLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return when (adapter.getItemViewType(position)) {
                    ClassManagerAdapter.VIEW_TYPE_HEADER -> 2
                    ClassManagerAdapter.VIEW_TYPE_ITEM -> 1
                    else -> 1
                }
            }
        }

        binding.recyclerView.layoutManager = gridLayoutManager
        binding.recyclerView.adapter = adapter
    }

    private fun setupFab() {
        binding.fabAddClass.setOnClickListener {
            val addClassSheet = AddClassSheet.newInstance(divisions.map { it.name })
            addClassSheet.show(supportFragmentManager, AddClassSheet.TAG)
        }
    }

    private fun setupSwipeRefreshLayout() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            viewModel.refreshAll() // Single call to refresh everything
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {

                // Combined observer for all UI states
                launch {
                    combine(
                        viewModel.divisions,
                        viewModel.classes,
                        viewModel.operationState
                    ) { divisionsState, classesState, operationState ->
                        Triple(divisionsState, classesState, operationState)
                    }.collectLatest { (divisionsState, classesState, operationState) ->

                        // --- Handle Loading State ---
                        val isListLoading = (divisionsState is UiState.Loading || classesState is UiState.Loading) && (operationState !is UiState.Success && operationState !is UiState.Error)
                        val isOperationRunning = operationState is UiState.Loading
                        binding.swipeRefreshLayout.isRefreshing = isListLoading || isOperationRunning

                        // --- Handle List Display ---
                        if (operationState !is UiState.Loading) { // Only update list when no operation is running
                            val isSuccess = divisionsState is UiState.Success && classesState is UiState.Success
                            when {
                                divisionsState is UiState.Error -> {
                                    binding.emptyView.text = divisionsState.message
                                    binding.emptyView.isVisible = true
                                    adapter.submitList(emptyList())
                                }
                                classesState is UiState.Error -> {
                                    binding.emptyView.text = classesState.message
                                    binding.emptyView.isVisible = true
                                    adapter.submitList(emptyList())
                                }
                                isSuccess -> {
                                    val divisionsList = (divisionsState as UiState.Success).data
                                    val classesList = (classesState as UiState.Success).data
                                    this@ClassManagerActivity.divisions = divisionsList

                                    val items = mutableListOf<ClassListItem>()
                                    if (classesList.isNotEmpty() && divisionsList.isNotEmpty()) {
                                        val groupedByDivision = classesList.groupBy { it.division }
                                        divisionsList.forEach { division ->
                                            val classesInDivision = groupedByDivision[division.name]
                                            if (!classesInDivision.isNullOrEmpty()) {
                                                items.add(ClassListItem.Header(division.name))
                                                items.addAll(classesInDivision.map { ClassListItem.ClassItem(it) })
                                            }
                                        }
                                    }
                                    adapter.submitList(items)
                                    binding.emptyView.isVisible = items.isEmpty()
                                    binding.emptyView.text = "No classes found"
                                }
                            }
                        }

                        // --- Handle Operation Feedback (Snackbar) ---
                        when (operationState) {
                            is UiState.Success -> {
                                Snackbar.make(binding.root, "Operation successful", Snackbar.LENGTH_SHORT).show()
                                viewModel.resetOperationState()
                            }
                            is UiState.Error -> {
                                Snackbar.make(binding.root, operationState.message, Snackbar.LENGTH_LONG).show()
                                viewModel.resetOperationState()
                            }
                            else -> Unit // Idle or Loading
                        }
                    }
                }
            }
        }
    }
}