package com.aewsn.alkhair.ui.classmanager

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.aewsn.alkhair.data.models.ClassModel
import com.aewsn.alkhair.data.models.DivisionModel
import com.aewsn.alkhair.databinding.ActivityClassManagerBinding
import com.aewsn.alkhair.ui.attendance.AttendanceActivity
import com.aewsn.alkhair.ui.user.UserListActivity
import com.aewsn.alkhair.utils.DialogUtils
import com.aewsn.alkhair.utils.Modes
import com.aewsn.alkhair.utils.Roles
import com.aewsn.alkhair.utils.UiState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ClassManagerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityClassManagerBinding
    private val viewModel: ClassManagerViewModel by viewModels()
    private lateinit var adapter: ClassManagerAdapter

    // Keep track for FAB action
    private var currentDivisions: List<DivisionModel> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityClassManagerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()
        setupToolbar()
        setupRecyclerView()
        setupFab()

        // Swipe Refresh just resets UI state in SSOT (Sync handles actual refresh)
        binding.swipeRefreshLayout.setOnRefreshListener {
            binding.swipeRefreshLayout.isRefreshing = false
        }

        observeViewModel()
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun setupRecyclerView() {
        adapter = ClassManagerAdapter(
            onEdit = { classModel ->
                val sheet = AddClassSheet.newInstance(
                    divisions = currentDivisions.map { it.name },
                    existingDivision = classModel.divisionName,
                    existingClassName = classModel.className,
                    existingClassId = classModel.id
                )
                sheet.show(supportFragmentManager, AddClassSheet.TAG)
            },
            onDelete = { classModel ->
                DialogUtils.showConfirmation(
                    this, "Delete Class", "Delete ${classModel.className}?",
                    onConfirmed = { viewModel.deleteClass(classModel.id) }
                )
            },
            onClick = { classModel ->
                val mode = intent.getStringExtra("mode") ?: Modes.CREATE
                val role = intent.getStringExtra("role") ?: Roles.STUDENT

                val target = if (mode == Modes.ATTENDANCE) {
                    Intent(this, AttendanceActivity::class.java)
                } else {
                    Intent(this, UserListActivity::class.java)
                }

                target.apply {
                    putExtra("mode", mode)
                    putExtra("role", role)
                    putExtra("classId", classModel.id)
                    putExtra("className", classModel.className)
                    putExtra("division", classModel.divisionName)
                }
                startActivity(target)
            }
        )

        val gridLayout = GridLayoutManager(this, 2)
        gridLayout.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return if (adapter.getItemViewType(position) == ClassManagerAdapter.VIEW_TYPE_HEADER) 2 else 1
            }
        }

        binding.recyclerView.layoutManager = gridLayout
        binding.recyclerView.adapter = adapter
    }

    private fun setupFab() {
        binding.fabAddClass.setOnClickListener {
            val sheet = AddClassSheet.newInstance(currentDivisions.map { it.name })
            sheet.show(supportFragmentManager, AddClassSheet.TAG)
        }
    }

    private fun observeViewModel() {
        // 1. Data Observer
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collectLatest { state ->
                    binding.swipeRefreshLayout.isRefreshing = state is UiState.Loading

                    when (state) {
                        is UiState.Success -> {
                            val data = state.data
                            this@ClassManagerActivity.currentDivisions = data.divisions

                            val listItems = buildListItems(data.divisions, data.classes)
                            adapter.submitList(listItems)

                            binding.emptyView.visibility = if(listItems.isEmpty()) View.VISIBLE else View.GONE
                        }
                        is UiState.Error -> {
                            binding.emptyView.text = state.message
                            binding.emptyView.visibility = View.VISIBLE
                        }
                        else -> Unit
                    }
                }
            }
        }

        // 2. Operation Observer
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.operationState.collectLatest { state ->
                    when (state) {
                        is UiState.Loading -> binding.swipeRefreshLayout.isRefreshing = true
                        is UiState.Success -> {
                            binding.swipeRefreshLayout.isRefreshing = false
                            Snackbar.make(binding.root, "Success", Snackbar.LENGTH_SHORT).show()
                            viewModel.resetOperationState()
                        }
                        is UiState.Error -> {
                            binding.swipeRefreshLayout.isRefreshing = false
                            Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                            viewModel.resetOperationState()
                        }
                        else -> Unit
                    }
                }
            }
        }
    }

    private fun buildListItems(divisions: List<DivisionModel>, classes: List<ClassModel>): List<ClassListItem> {
        if (divisions.isEmpty()) return emptyList()
        
        val items = mutableListOf<ClassListItem>()
        // ✅ Fix: Group by divisionId (since division name might be empty)
        val groupedClasses = classes.groupBy { it.divisionId }
        
        // Sort divisions if needed, e.g. alphabetical
        val sortedDivisions = divisions.sortedBy { it.name }
        
        sortedDivisions.forEach { div ->
            items.add(ClassListItem.Header(div.name))
            
            // ✅ Fix: Access by Division ID
            val classList = groupedClasses[div.id]
            if (!classList.isNullOrEmpty()) {
                items.addAll(classList.map { ClassListItem.ClassItem(it) })
            }
        }
        return items
    }
}