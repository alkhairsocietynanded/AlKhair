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
import com.zabibtech.alkhair.R
import com.zabibtech.alkhair.databinding.ActivityAttendanceBinding
import com.zabibtech.alkhair.ui.user.UserViewModel
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

        // ✅ Insets handle karo
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
        setupDateNavigation() // ✅ Add this
        updateDateText() // Set initial date
        reloadAttendanceForSelectedDate()

        binding.swipeRefresh.setOnRefreshListener { userViewModel.loadUsers(role) }

        // ✅ Save Attendance Button
        binding.fabSaveAttendance.apply {
            visibility = View.GONE
            isEnabled = false

            adapter.attendanceCompleteListener = { isComplete ->
                visibility = if (isComplete) View.VISIBLE else View.GONE
                isEnabled = true
                updateAttendanceSummary() // ✅ Update summary whenever attendance changes
            }

            setOnClickListener {
                val attendanceMap = adapter.getAttendanceMap()
                if (adapter.isAttendanceComplete()) {
                    attendanceViewModel.saveAttendanceForClass(
                        classId,
                        DateUtils.formatDate(selectedDate),
                        attendanceMap
                    )
                } else {
                    DialogUtils.showAlert(
                        this@AttendanceActivity,
                        "Incomplete",
                        "Please mark attendance for all students"
                    )
                }
            }
        }

        // ✅ Default shift filter = All
//        binding.radioGroupShift.check(R.id.radioAll)

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
            // Agar kisi student pe click karna ho (optional)
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun setupObservers() {
        // Students list observe
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                userViewModel.userListState.collectLatest { state ->
                    // Always stop refreshing first
//                    binding.swipeRefresh.isRefreshing = false

                    when (state) {
                        UiState.Loading -> if (!binding.swipeRefresh.isRefreshing)
                            DialogUtils.showLoading(supportFragmentManager)

                        is UiState.Success -> {
                            DialogUtils.hideLoading(supportFragmentManager)
                            binding.swipeRefresh.isRefreshing = false

                            val filtered = UserFilterHelper.filterUsers(
                                users = state.data,
                                role = role,
                                classId = classId,
                                shift = currentShift
                            )
                            adapter.submitList(filtered)
                        }

                        is UiState.Error -> {
                            DialogUtils.hideLoading(supportFragmentManager)
                            binding.swipeRefresh.isRefreshing = false
                            DialogUtils.showAlert(
                                this@AttendanceActivity,
                                "Error",
                                state.message
                            )
                        }

                        else -> {}
                    }
                }
            }
        }

        // Attendance save state observe
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                attendanceViewModel.attendanceState.collectLatest { state ->
                    when (state) {
                        is UiState.Loading -> {
                            binding.fabSaveAttendance.isEnabled = false
                            DialogUtils.showLoading(supportFragmentManager) // ✅ Show loading dialog
                        }

                        is UiState.Success -> {
                            binding.fabSaveAttendance.isEnabled = true
                            binding.fabSaveAttendance.visibility = View.GONE // ✅ Hide on success
                            adapter.resetChangeFlag() // ✅ Reset change flag
                            DialogUtils.hideLoading(supportFragmentManager) // ✅ Hide loading
                            DialogUtils.showAlert(
                                this@AttendanceActivity,
                                "Saved",
                                "Attendance saved successfully"
                            )
                        }

                        is UiState.Error -> {
                            binding.fabSaveAttendance.isEnabled = true
                            DialogUtils.hideLoading(supportFragmentManager) // ✅ Hide loading
                            DialogUtils.showAlert(
                                this@AttendanceActivity,
                                "Error",
                                state.message
                            )
                        }

                        else -> binding.fabSaveAttendance.isEnabled = adapter.isAttendanceComplete()
                    }
                }
            }
        }

        // ✅ Prefill attendance observer
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                attendanceViewModel.attendanceLoadState.collectLatest { state ->
                    when (state) {
                        is UiState.Success -> {
                            adapter.setAttendanceMap(state.data) // ✅ Prefill adapter
                        }

                        is UiState.Error -> {
                            DialogUtils.showAlert(this@AttendanceActivity, "Error", state.message)
                        }

                        else -> {}
                    }
                }
            }
        }
    }

    private fun setupChipFilterListeners() {
        binding.chipGroupShift.setOnCheckedStateChangeListener { group, checkedIds ->
            // Since singleSelection is true, we can safely take the first ID.
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener

            currentShift = when (checkedIds.first()) {
                R.id.chipSubah -> "Subah"
                R.id.chipDopahar -> "Dopahar"
                R.id.chipShaam -> "Shaam"
                else -> "All"
            }
            // Trigger a reload of the user list with the new filter
            userViewModel.loadUsers(role)
        }
    }


    private fun setupDateNavigation() {
        // Previous Date button
        binding.btnPrevDate.setOnClickListener {
            selectedDate.add(Calendar.DAY_OF_YEAR, -1)
            binding.tvSelectedDate.text = DateUtils.formatDate(selectedDate)
            reloadAttendanceForSelectedDate()
        }

        // Next Date button
        binding.btnNextDate.setOnClickListener {
            selectedDate.add(Calendar.DAY_OF_YEAR, 1)
            binding.tvSelectedDate.text = DateUtils.formatDate(selectedDate)
            reloadAttendanceForSelectedDate()
        }

        // Click on TextView to pick date
        binding.tvSelectedDate.setOnClickListener {
            DateUtils.showMaterialDatePicker(supportFragmentManager, selectedDate) {
                selectedDate = it
                binding.tvSelectedDate.text = DateUtils.formatDate(selectedDate)
                reloadAttendanceForSelectedDate()
            }
        }

        // Initial date display
        binding.tvSelectedDate.text = DateUtils.formatDate(selectedDate)
    }

    private fun updateDateText() {
        binding.tvSelectedDate.text = DateUtils.today()
    }

    // Function to reload attendance based on selected date, role, and class
    private fun reloadAttendanceForSelectedDate() {
        adapter.clearAttendance()
        userViewModel.loadUsers(role)
        attendanceViewModel.loadAttendanceForClass(
            classId,
            DateUtils.formatDate(selectedDate)
        )
        binding.fabSaveAttendance.visibility = View.GONE
        updateAttendanceSummary() // Reset summary
    }

    private fun updateAttendanceSummary() {
        val (presentCount, absentCount, leaveCount) = adapter.getAttendanceSummary()

        binding.tvSummaryPresent.text = presentCount.toString()
        binding.tvSummaryAbsent.text = absentCount.toString()
        binding.tvSummaryOnLeave.text = leaveCount.toString()
    }
}
