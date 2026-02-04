package com.aewsn.alkhair.ui.attendance

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
import com.aewsn.alkhair.R
import com.aewsn.alkhair.databinding.ActivityAttendanceBinding
import com.aewsn.alkhair.utils.DateUtils
import com.aewsn.alkhair.utils.DialogUtils
import com.aewsn.alkhair.utils.Roles
import com.aewsn.alkhair.utils.Shift
import com.aewsn.alkhair.utils.UiState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Calendar

@AndroidEntryPoint
class AttendanceActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAttendanceBinding
    private val viewModel: AttendanceViewModel by viewModels()
    private lateinit var adapter: AttendanceAdapter
    private var selectedDate: Calendar = Calendar.getInstance()
    private var loggedInUser: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityAttendanceBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()
        setupToolbar()

        loggedInUser = intent.getStringExtra("loggedInUser") ?: Roles.STUDENT
        val intentRole = intent.getStringExtra("role") ?: Roles.STUDENT
        val classId = intent.getStringExtra("classId")

        // 1. Initialize ViewModel Filters
        viewModel.setFilters(classId, intentRole)
        viewModel.setShift(Shift.ALL)
        viewModel.setDate(selectedDate)

        // 2. Determine Logged In User for UI Control
        checkLoggedInUserAndSetupUI()

        setupRecyclerView()
        setupListeners()
        setupObservers()
        updateDateUi()

        binding.chipGroupShift.check(R.id.chipAll)
    }

    private fun checkLoggedInUserAndSetupUI() {

        if (loggedInUser.equals(Roles.TEACHER, ignoreCase = true)) {
            // Teacher: Hide Filter
            binding.shiftSelectionCard.visibility = View.GONE
        } else {
            // Admin: Show Filter
            binding.shiftSelectionCard.visibility = View.VISIBLE
        }
    }

    // ... (Rest of the code: setupWindowInsets, setupToolbar, setupRecyclerView, etc. same as before) ...
    // Note: Copy the rest of the functions from your previous code
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
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun setupRecyclerView() {
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
        binding.btnPrevDate.setOnClickListener {
            selectedDate.add(Calendar.DAY_OF_YEAR, -1)
            updateDateUi(); viewModel.setDate(selectedDate)
        }
        binding.btnNextDate.setOnClickListener {
            selectedDate.add(Calendar.DAY_OF_YEAR, 1)
            updateDateUi(); viewModel.setDate(selectedDate)
        }
        binding.tvSelectedDate.setOnClickListener {
            DateUtils.showMaterialDatePicker(supportFragmentManager, selectedDate) {
                selectedDate = it; updateDateUi(); viewModel.setDate(selectedDate)
            }
        }

        binding.chipGroupShift.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            val shift = when (checkedIds.first()) {
                R.id.chipSubah -> Shift.SUBAH
                R.id.chipDopahar -> Shift.DOPAHAR
                R.id.chipShaam -> Shift.SHAAM
                else -> Shift.ALL
            }
            viewModel.setShift(shift)
        }

        binding.fabSaveAttendance.setOnClickListener { viewModel.saveCurrentAttendance() }
        binding.swipeRefresh.setOnRefreshListener { binding.swipeRefresh.isRefreshing = false }
    }

    private fun updateDateUi() {
        binding.tvSelectedDate.text = DateUtils.formatDate(selectedDate)
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collectLatest { state ->
                    binding.swipeRefresh.isRefreshing = state is UiState.Loading
                    when (state) {
                        is UiState.Success -> {
                            val list = state.data
                            adapter.submitList(list)

                            val present = list.count { it.status == "Present" }
                            val absent = list.count { it.status == "Absent" }
                            val leave = list.count { it.status == "Leave" }
                            binding.tvSummaryPresent.text = present.toString()
                            binding.tvSummaryAbsent.text = absent.toString()
                            binding.tvSummaryOnLeave.text = leave.toString()

                            binding.fabSaveAttendance.visibility =
                                if (list.isNotEmpty()) View.VISIBLE else View.GONE
                            binding.fabSaveAttendance.isEnabled = true
                            binding.emptyView.visibility =
                                if (list.isEmpty()) View.VISIBLE else View.GONE
                        }

                        is UiState.Error -> DialogUtils.showAlert(
                            this@AttendanceActivity,
                            "Error",
                            state.message
                        )

                        else -> Unit
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.saveState.collectLatest { state ->
                    when (state) {
                        is UiState.Loading -> DialogUtils.showLoading(
                            supportFragmentManager,
                            "Saving..."
                        )

                        is UiState.Success -> {
                            DialogUtils.hideLoading(supportFragmentManager)
                            DialogUtils.showAlert(this@AttendanceActivity, "Success", "Attendance saved successfully.")
                            viewModel.resetSaveState()
                        }

                        is UiState.Error -> {
                            DialogUtils.hideLoading(supportFragmentManager)
                            DialogUtils.showAlert(this@AttendanceActivity, "Error", state.message)
                            viewModel.resetSaveState()
                        }

                        else -> Unit
                    }
                }
            }
        }
    }
}