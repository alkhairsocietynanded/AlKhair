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
import com.zabibtech.alkhair.ui.user.UserViewModel
import com.zabibtech.alkhair.utils.DateUtils
import com.zabibtech.alkhair.utils.DialogUtils
import com.zabibtech.alkhair.utils.Roles
import com.zabibtech.alkhair.utils.UiState
import com.zabibtech.alkhair.utils.UserFilterHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Calendar

@AndroidEntryPoint
class AttendanceActivity : AppCompatActivity() {

    private var selectedDate: Calendar = Calendar.getInstance()

    private lateinit var binding: ActivityAttendanceBinding
    private val userViewModel: UserViewModel by viewModels()
    private val attendanceViewModel: AttendanceViewModel by viewModels()
    private lateinit var adapter: AttendanceAdapter

    private var classId: String? = null
    private var role: String = Roles.STUDENT
    private var currentShift: String = "All"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityAttendanceBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        setupToolbar()

        classId = intent.getStringExtra("classId")
        role = intent.getStringExtra("role") ?: Roles.STUDENT

        setupRecyclerView()
        setupObservers()
        setupDateNavigation()
        updateDateText()
        reloadAttendanceForSelectedDate()

        binding.swipeRefresh.setOnRefreshListener { reloadAttendanceForSelectedDate() }

        binding.fabSaveAttendance.apply {
            visibility = View.GONE
            isEnabled = false

            adapter.attendanceCompleteListener = { isComplete ->
                visibility = if (isComplete) View.VISIBLE else View.GONE
                isEnabled = true
                updateAttendanceSummary()
            }

            setOnClickListener {
                val attendanceMap = adapter.getAttendanceMap()
                if (adapter.isAttendanceComplete()) {
                    classId?.let {
                        attendanceViewModel.saveAttendance(
                            it,
                            DateUtils.formatDate(selectedDate),
                            attendanceMap
                        )
                    }
                } else {
                    DialogUtils.showAlert(
                        this@AttendanceActivity,
                        "Incomplete",
                        "Please mark attendance for all students"
                    )
                }
            }
        }

        setupChipFilterListeners()
        binding.chipGroupShift.check(R.id.chipAll)
        userViewModel.loadUsers(role)
    }


    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupRecyclerView() {
        adapter = AttendanceAdapter { user ->
            // Optional: Handle click on a student item
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun setupObservers() {
        // Observer for student list
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                userViewModel.userListState.collectLatest { state ->
                    binding.swipeRefresh.isRefreshing = state is UiState.Loading

                    when (state) {
                        is UiState.Success -> {
                            val filtered = UserFilterHelper.filterUsers(
                                users = state.data,
                                role = role,
                                classId = classId,
                                shift = currentShift
                            )
                            adapter.submitList(filtered)
                        }
                        is UiState.Error -> {
                            DialogUtils.showAlert(this@AttendanceActivity, "Error", state.message)
                        }
                        else -> Unit
                    }
                }
            }
        }

        // Observer for SAVING attendance state
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                attendanceViewModel.saveState.collectLatest { state ->
                    when (state) {
                        is UiState.Loading -> {
                            binding.fabSaveAttendance.isEnabled = false
                            DialogUtils.showLoading(supportFragmentManager, "Saving...")
                        }
                        is UiState.Success -> {
                            DialogUtils.hideLoading(supportFragmentManager)
                            binding.fabSaveAttendance.visibility = View.GONE
                            adapter.resetChangeFlag()
                            Snackbar.make(binding.root, "Attendance saved successfully", Snackbar.LENGTH_SHORT).show()
                            attendanceViewModel.resetSaveState()
                        }
                        is UiState.Error -> {
                            DialogUtils.hideLoading(supportFragmentManager)
                            binding.fabSaveAttendance.isEnabled = true
                            Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                            attendanceViewModel.resetSaveState()
                        }
                        is UiState.Idle -> {
                            binding.fabSaveAttendance.isEnabled = adapter.isAttendanceComplete()
                        }
                    }
                }
            }
        }

        // Observer for LOADING attendance to prefill the adapter
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                attendanceViewModel.attendanceState.collectLatest { state ->
                    // Show swipe-to-refresh animation while loading attendance
                    binding.swipeRefresh.isRefreshing = state is UiState.Loading

                    when (state) {
                        is UiState.Success -> {
                            adapter.setAttendanceMap(state.data)
                        }
                        is UiState.Error -> {
                            Snackbar.make(binding.root, "Could not load previous attendance: ${state.message}", Snackbar.LENGTH_LONG).show()
                        }
                        else -> Unit
                    }
                }
            }
        }
    }

    private fun setupChipFilterListeners() {
        binding.chipGroupShift.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener

            currentShift = when (checkedIds.first()) {
                R.id.chipSubah -> "Subah"
                R.id.chipDopahar -> "Dopahar"
                R.id.chipShaam -> "Shaam"
                else -> "All"
            }
            userViewModel.loadUsers(role) // Reload users with the new shift filter
        }
    }

    private fun setupDateNavigation() {
        binding.btnPrevDate.setOnClickListener {
            selectedDate.add(Calendar.DAY_OF_YEAR, -1)
            updateDateText()
            reloadAttendanceForSelectedDate()
        }

        binding.btnNextDate.setOnClickListener {
            selectedDate.add(Calendar.DAY_OF_YEAR, 1)
            updateDateText()
            reloadAttendanceForSelectedDate()
        }

        binding.tvSelectedDate.setOnClickListener {
            DateUtils.showMaterialDatePicker(supportFragmentManager, selectedDate) {
                selectedDate = it
                updateDateText()
                reloadAttendanceForSelectedDate()
            }
        }
    }

    private fun updateDateText() {
        binding.tvSelectedDate.text = DateUtils.formatDate(selectedDate)
    }

    private fun reloadAttendanceForSelectedDate() {
        adapter.clearAttendance()
        userViewModel.loadUsers(role)
        classId?.let {
            attendanceViewModel.getAttendance(it, DateUtils.formatDate(selectedDate))
        }
        binding.fabSaveAttendance.visibility = View.GONE
        updateAttendanceSummary()
    }

    private fun updateAttendanceSummary() {
        val (presentCount, absentCount, leaveCount) = adapter.getAttendanceSummary()
        binding.tvSummaryPresent.text = presentCount.toString()
        binding.tvSummaryAbsent.text = absentCount.toString()
        binding.tvSummaryOnLeave.text = leaveCount.toString()
    }
}