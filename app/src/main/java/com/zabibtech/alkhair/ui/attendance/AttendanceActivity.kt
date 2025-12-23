package com.zabibtech.alkhair.ui.attendance

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
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.zabibtech.alkhair.R
import com.zabibtech.alkhair.databinding.ActivityAttendanceBinding
import com.zabibtech.alkhair.utils.DateUtils
import com.zabibtech.alkhair.utils.DialogUtils
import com.zabibtech.alkhair.utils.Roles
import com.zabibtech.alkhair.utils.Shift
import com.zabibtech.alkhair.utils.UiState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Calendar

@AndroidEntryPoint
class AttendanceActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAttendanceBinding
    private val viewModel: AttendanceViewModel by viewModels() // Only one VM needed now
    private lateinit var adapter: AttendanceAdapter

    // Local state for UI only (Date navigation)
    private var selectedDate: Calendar = Calendar.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityAttendanceBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()
        setupToolbar()

        // 1. Initialize ViewModel Filters from Intent
        val classId = intent.getStringExtra("classId")
        val role = intent.getStringExtra("role") ?: Roles.STUDENT

        viewModel.setFilters(classId, role)
        viewModel.setDate(selectedDate)

        setupRecyclerView()
        setupListeners()
        setupObservers()
        updateDateUi()
    }

    /* ============================================================
       🔧 UI SETUP
       ============================================================ */

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
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
        // Adapter now reports changes back to VM immediately
        adapter = AttendanceAdapter { studentId, status ->
            viewModel.markAttendance(studentId, status)
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    /* ============================================================
       🎧 LISTENERS
       ============================================================ */

    private fun setupListeners() {
        // 1. Date Navigation
        binding.btnPrevDate.setOnClickListener {
            selectedDate.add(Calendar.DAY_OF_YEAR, -1)
            updateDateUi()
            viewModel.setDate(selectedDate)
        }

        binding.btnNextDate.setOnClickListener {
            selectedDate.add(Calendar.DAY_OF_YEAR, 1)
            updateDateUi()
            viewModel.setDate(selectedDate)
        }

        binding.tvSelectedDate.setOnClickListener {
            DateUtils.showMaterialDatePicker(supportFragmentManager, selectedDate) {
                selectedDate = it
                updateDateUi()
                viewModel.setDate(selectedDate)
            }
        }

        // 2. Shift Filter
        binding.chipGroupShift.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            val shift = when (checkedIds.first()) {
                R.id.chipSubah -> Shift.SUBAH
                R.id.chipDopahar -> Shift.DOPAHAR
                R.id.chipShaam -> Shift.SHAAM
                else -> "All"
            }
            viewModel.setShift(shift)
        }
        // Set default check
        binding.chipGroupShift.check(R.id.chipAll)

        // 3. Save Action
        binding.fabSaveAttendance.setOnClickListener {
            viewModel.saveCurrentAttendance()
        }

        // 4. Swipe Refresh (Triggers Sync in a real app, here just UI reset)
        binding.swipeRefresh.setOnRefreshListener {
            binding.swipeRefresh.isRefreshing = false
        }
    }

    private fun updateDateUi() {
        binding.tvSelectedDate.text = DateUtils.formatDate(selectedDate)
    }

    /* ============================================================
       👀 OBSERVERS
       ============================================================ */

    private fun setupObservers() {

        // 📦 Main Data Stream (Users + Attendance + Local Edits)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collectLatest { state ->
                    binding.swipeRefresh.isRefreshing = state is UiState.Loading

                    when (state) {
                        is UiState.Success -> {
                            val list = state.data
                            adapter.submitList(list)

                            // Calculate Summary
                            val present = list.count { it.status == "Present" }
                            val absent = list.count { it.status == "Absent" }
                            val leave = list.count { it.status == "Leave" }

                            binding.tvSummaryPresent.text = present.toString()
                            binding.tvSummaryAbsent.text = absent.toString()
                            binding.tvSummaryOnLeave.text = leave.toString()

                            // Update FAB Visibility (Only show if list is not empty)
                            // We allow saving partially marked attendance, logic handled in VM
                            binding.fabSaveAttendance.visibility =
                                if (list.isNotEmpty()) View.VISIBLE else View.GONE
                            binding.fabSaveAttendance.isEnabled = true
                        }

                        is UiState.Error -> {
                            DialogUtils.showAlert(this@AttendanceActivity, "Error", state.message)
                        }

                        else -> Unit
                    }
                }
            }
        }

        // 💾 Save Operation State
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.saveState.collectLatest { state ->
                    when (state) {
                        is UiState.Loading -> {
                            DialogUtils.showLoading(supportFragmentManager, "Saving...")
                        }

                        is UiState.Success -> {
                            DialogUtils.hideLoading(supportFragmentManager)
                            Snackbar.make(
                                binding.root,
                                "Attendance saved successfully",
                                Snackbar.LENGTH_SHORT
                            ).show()
                            viewModel.resetSaveState()
                        }

                        is UiState.Error -> {
                            DialogUtils.hideLoading(supportFragmentManager)
                            DialogUtils.showAlert(
                                this@AttendanceActivity,
                                "Save Failed",
                                state.message
                            )
                            viewModel.resetSaveState()
                        }

                        else -> Unit
                    }
                }
            }
        }
    }
}