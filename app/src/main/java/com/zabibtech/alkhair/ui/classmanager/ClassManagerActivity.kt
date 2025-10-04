package com.zabibtech.alkhair.ui.classmanager

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.zabibtech.alkhair.data.models.DivisionModel
import com.zabibtech.alkhair.databinding.ActivityClassManagerBinding
import com.zabibtech.alkhair.ui.attendance.AttendanceActivity
import com.zabibtech.alkhair.ui.user.UserListActivity
import com.zabibtech.alkhair.utils.DialogUtils
import com.zabibtech.alkhair.utils.Modes
import com.zabibtech.alkhair.utils.Roles
import com.zabibtech.alkhair.utils.UiState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ClassManagerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityClassManagerBinding
    private val viewModel: ClassManagerViewModel by viewModels()
    private lateinit var adapter: ClassManagerAdapter

    private var currentDivision: String? = null
    private var divisions: List<DivisionModel> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityClassManagerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ✅ Insets handle karo
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupRecyclerView()
        setupFab()

        // 🔹 Load initial data
        viewModel.loadDivisions()
        // Initial load of classes will be "All". ViewModel's currentDivisionFilterForRefresh defaults to null.
        viewModel.loadClasses()

        observeDivisions()
        observeClasses()
    }

    private fun setupRecyclerView() {
        adapter = ClassManagerAdapter(
            items = emptyList(),
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
                    onConfirmed = { viewModel.deleteClass(classModel.id) } // Pass only classId
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
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
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

    private fun observeDivisions() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.divisions.collect { state ->
                    when (state) {
                        is UiState.Success -> {
                            divisions = state.data
                            val names = listOf("All") + divisions.map { it.name }
                            val spinnerAdapter = ArrayAdapter(
                                this@ClassManagerActivity,
                                android.R.layout.simple_spinner_item,
                                names
                            )
                            spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                            binding.spinnerDivisionFilter.adapter = spinnerAdapter

                            // Restore previous selection if any, or default to "All"
                            val previousSelection = currentDivision
                            val selectionIndex =
                                if (previousSelection == null) 0 else names.indexOf(
                                    previousSelection
                                ).takeIf { it != -1 } ?: 0
                            binding.spinnerDivisionFilter.setSelection(selectionIndex)

                            binding.spinnerDivisionFilter.onItemSelectedListener =
                                object : AdapterView.OnItemSelectedListener {
                                    override fun onItemSelected(
                                        parent: AdapterView<*>?,
                                        view: View?,
                                        position: Int,
                                        id: Long
                                    ) {
                                        currentDivision =
                                            if (position == 0) null else divisions[position - 1].name

                                        // Inform ViewModel of the filter change
                                        viewModel.setCurrentDivisionFilter(currentDivision)
                                    }

                                    override fun onNothingSelected(parent: AdapterView<*>?) {
                                        // Do nothing
                                    }
                                }
                        }

                        is UiState.Error -> {
                            DialogUtils.showConfirmation(
                                this@ClassManagerActivity,
                                "Error",
                                state.message
                            )
                        }

                        else -> {}
                    }
                }
            }
        }
    }

    private fun observeClasses() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.classes.collect { state ->
                    when (state) {
                        is UiState.Success -> adapter.updateList(state.data)
                        is UiState.Error -> DialogUtils.showConfirmation(
                            this@ClassManagerActivity,
                            "Error",
                            state.message
                        )

                        else -> {}
                    }
                }
            }
        }
    }
}
