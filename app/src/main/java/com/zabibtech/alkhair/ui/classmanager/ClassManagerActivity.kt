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
import com.zabibtech.alkhair.data.models.DivisionModel
import com.zabibtech.alkhair.databinding.ActivityClassManagerBinding
import com.zabibtech.alkhair.ui.attendance.AttendanceActivity
import com.zabibtech.alkhair.ui.user.UserListActivity
import com.zabibtech.alkhair.utils.DialogUtils
import com.zabibtech.alkhair.utils.Modes
import com.zabibtech.alkhair.utils.Roles
import com.zabibtech.alkhair.utils.UiState
import dagger.hilt.android.AndroidEntryPoint
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

        setSupportActionBar(binding.toolbar)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupRecyclerView()
        setupFab()

        observeViewModel()

        viewModel.loadDivisions()
        viewModel.loadClasses()
    }

    private fun setupRecyclerView() {
        adapter = ClassManagerAdapter(
            onEdit = { classModel ->
                DialogUtils.showAddClassDialog(
                    this,
                    divisions = divisions.map { it.name },
                    existingDivision = classModel.division,
                    existingClassName = classModel.className
                ) { division, className ->
                    val updated = classModel.copy(division = division, className = className)
                    viewModel.updateClass(updated)
                }
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
            DialogUtils.showAddClassDialog(
                context = this,
                divisions = divisions.map { it.name }
            ) { division, className ->
                viewModel.addClass(division, className)
            }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.divisions.combine(viewModel.classes) { divisionsState, classesState ->
                    Pair(divisionsState, classesState)
                }.collect { (divisionsState, classesState) ->

                    // Determine the overall state
                    val isError = divisionsState is UiState.Error || classesState is UiState.Error
                    val isSuccess = divisionsState is UiState.Success && classesState is UiState.Success
                    // Show loading only if not in success or error, and the list is empty
                    val isLoading = !isSuccess && !isError && adapter.itemCount == 0

                    binding.progressBar.isVisible = isLoading

                    if (isError) {
                        val errorMessage = (divisionsState as? UiState.Error)?.message
                            ?: (classesState as? UiState.Error)?.message
                        binding.emptyView.text = errorMessage
                        binding.emptyView.isVisible = true
                        adapter.submitList(emptyList()) // Clear previous data
                    } else if (isSuccess) {
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
        }
    }
}
