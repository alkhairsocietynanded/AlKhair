package com.aewsn.alkhair.ui.dashboard

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.widget.CompositePageTransformer
import androidx.viewpager2.widget.MarginPageTransformer
import com.aewsn.alkhair.R
import com.aewsn.alkhair.data.models.DashboardStats
import com.aewsn.alkhair.data.models.User
import com.aewsn.alkhair.databinding.ActivityTeacherDashboardBinding
import com.aewsn.alkhair.ui.announcement.AddAnnouncementSheet
import com.aewsn.alkhair.ui.announcement.AnnouncementPagerAdapter
import com.aewsn.alkhair.ui.announcement.AnnouncementViewModel
import com.aewsn.alkhair.ui.attendance.AttendanceActivity
import com.aewsn.alkhair.ui.attendance.AttendanceViewModel
import com.aewsn.alkhair.ui.fees.FeesActivity
import com.aewsn.alkhair.ui.homework.HomeworkActivity
import com.aewsn.alkhair.ui.salary.SalaryActivity
import com.aewsn.alkhair.ui.scanner.QRScannerActivity
import com.aewsn.alkhair.ui.user.UserDetailActivity
import com.aewsn.alkhair.ui.user.UserListActivity
import com.aewsn.alkhair.ui.user.UserViewModel
import com.aewsn.alkhair.utils.Constants
import com.aewsn.alkhair.utils.DialogUtils
import com.aewsn.alkhair.utils.LogoutManager
import com.aewsn.alkhair.utils.Modes
import com.aewsn.alkhair.utils.Roles
import com.aewsn.alkhair.utils.UiState
import com.google.android.material.tabs.TabLayoutMediator
import androidx.core.view.isVisible
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale
import javax.inject.Inject
import kotlin.math.abs

@AndroidEntryPoint
class TeacherDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTeacherDashboardBinding
    private val dashboardViewModel: TeacherDashboardViewModel by viewModels()
    private val announcementViewModel: AnnouncementViewModel by viewModels()
    private val attendanceViewModel: AttendanceViewModel by viewModels()
    private val userViewModel: UserViewModel by viewModels()

    private lateinit var adapter: AnnouncementPagerAdapter
    private var loggedInUser: User? = null

    @Inject
    lateinit var logoutManager: LogoutManager

    // ✅ 1. Define Launcher to receive result from QRScannerActivity
    private val qrScannerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val qrValue = result.data?.getStringExtra("scanned_qr_value")

            if (qrValue == Constants.SCHOOL_QR_CODE_VALUE) {
                // ✅ Valid QR - Mark Attendance
                attendanceViewModel.markSelfPresent()
            } else {
                DialogUtils.showAlert(
                    this,
                    "Invalid QR",
                    "This QR Code does not belong to Al-Khair School."
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityTeacherDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()
        setupToolbar()
        setupListeners()
        setupAnnouncementViewPager()

        // Observers
        observeLoadingState()
        observeDashboardStats()
        observeAnnouncements()
        observeAttendanceState()
        observeLoggedInUser()

//        Temp Script, run only once
//        dashboardViewModel.runMigrationScript()
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
    }

    private fun setupListeners() {
        // --- Navigation ---
        binding.cardDevInfo.setOnClickListener {
            val bottomSheet = com.aewsn.alkhair.ui.common.AppInfoBottomSheet()
            bottomSheet.show(supportFragmentManager, com.aewsn.alkhair.ui.common.AppInfoBottomSheet.TAG)
        }

        binding.cardStudents.setOnClickListener {
            startActivity(
                Intent(this, UserListActivity::class.java)
                    .putExtra("role", Roles.STUDENT)
                    .putExtra("mode", Modes.CREATE)
            )
        }

        binding.cardHomework.setOnClickListener {
            startActivity(Intent(this, HomeworkActivity::class.java))
        }

        binding.cardStudentAttendance.setOnClickListener {
            val user = loggedInUser
            if (user == null || user.classId.isNullOrBlank()) {
                Toast.makeText(this, "Please wait, loading user data...", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val intent = Intent(this, AttendanceActivity::class.java).apply {
                putExtra("role", Roles.STUDENT)
                putExtra("loggedInUser", Roles.TEACHER)
                putExtra("classId", user.classId)
            }
            startActivity(intent)
        }

        binding.cardFees.setOnClickListener {
            startActivity(Intent(this, FeesActivity::class.java).apply {
                putExtra("role", Roles.TEACHER) // ✅ Pass Role
            })
        }
        binding.cardAnnouncement.setOnClickListener {
            val addAnnouncementSheet = AddAnnouncementSheet()
            addAnnouncementSheet.show(supportFragmentManager, AddAnnouncementSheet.TAG)
        }

        binding.cardSalary.setOnClickListener {
            startActivity(Intent(this, SalaryActivity::class.java))
        }

        binding.cardLeaveApproval.setOnClickListener {
             val intent = Intent(this, com.aewsn.alkhair.ui.approval.LeaveApprovalActivity::class.java).apply {
                putExtra("user", loggedInUser)
            }
            startActivity(intent)
        }

        binding.cardSyllabus.setOnClickListener {
            startActivity(Intent(this, com.aewsn.alkhair.ui.syllabus.SyllabusActivity::class.java))
        }
        
        binding.cardProfile.setOnClickListener {
            val intent = Intent(this, UserDetailActivity::class.java).apply {
                putExtra("userId", loggedInUser?.uid)
                putExtra("user", loggedInUser)
            }
            startActivity(intent)
        }

        binding.fabQRCode.setOnClickListener {
            startQrScanner()
        }

        binding.swipeRefreshLayout.setOnRefreshListener {
            // Data refreshes automatically via Flow when DB updates (Background sync)
            binding.swipeRefreshLayout.isRefreshing = false
        }

        // --- Ask AI ---
        binding.cardAskAi.setOnClickListener {
            startActivity(Intent(this, com.aewsn.alkhair.ui.chat.ChatActivity::class.java))
        }

        binding.cardApplyLeave.setOnClickListener {
             com.aewsn.alkhair.ui.dashboard.leave.TeacherApplyLeaveBottomSheet().show(supportFragmentManager, com.aewsn.alkhair.ui.dashboard.leave.TeacherApplyLeaveBottomSheet.TAG)
        }

        binding.cardLeaveApproval.setOnClickListener {
             val intent = Intent(this, com.aewsn.alkhair.ui.approval.LeaveApprovalActivity::class.java).apply {
                putExtra("user", loggedInUser)
            }
            startActivity(intent)
        }

        binding.cardSyllabus.setOnClickListener {
            startActivity(Intent(this, com.aewsn.alkhair.ui.syllabus.SyllabusActivity::class.java))
        }
        
        binding.cardProfile.setOnClickListener {
            val intent = Intent(this, UserDetailActivity::class.java).apply {
                putExtra("userId", loggedInUser?.uid)
                putExtra("user", loggedInUser)
            }
            startActivity(intent)
        }

        binding.fabQRCode.setOnClickListener {
            startQrScanner()
        }

        binding.swipeRefreshLayout.setOnRefreshListener {
            dashboardViewModel.refreshData()
        }

        binding.cardLogout.setOnClickListener {
            DialogUtils.showConfirmation(
                this,
                "Logout",
                "Are you sure you want to logout?",
                onConfirmed = { logoutManager.logout(this) }
            )
        }
    }

    // ✅ 2. Update startQrScanner to launch Activity
    private fun startQrScanner() {
        val intent = Intent(this, QRScannerActivity::class.java)
        qrScannerLauncher.launch(intent)
    }

    private fun setupAnnouncementViewPager() {
        adapter = AnnouncementPagerAdapter(
            mutableListOf(),
            onDelete = { announcement ->
                DialogUtils.showConfirmation(
                    context = this,
                    title = "Delete announcement",
                    message = "Are you sure you want to delete ${announcement.title}?",
                    onConfirmed = { announcementViewModel.deleteAnnouncement(announcement.id) }
                )
            }
        )

        binding.announcementPager.adapter = adapter
        binding.announcementPager.apply {
            clipToPadding = false
            clipChildren = false
            offscreenPageLimit = 3
        }

        val compositePageTransformer = CompositePageTransformer()
        compositePageTransformer.addTransformer(MarginPageTransformer(40))
        compositePageTransformer.addTransformer { page, position ->
            val r = 1 - abs(position)
            page.scaleY = 0.85f + r * 0.15f
        }

        binding.announcementPager.setPageTransformer(compositePageTransformer)
        TabLayoutMediator(binding.tabLayout, binding.announcementPager) { _, _ -> }.attach()
    }

    /* ============================================================
       👀 OBSERVERS
       ============================================================ */

    private fun observeLoadingState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    dashboardViewModel.dashboardState,
                    announcementViewModel.latestAnnouncementsState,
                    announcementViewModel.mutationState,
                    dashboardViewModel.isSyncing // Added isSyncing observer
                ) { dashboardState, announcementListState, mutationState, isSyncing ->
                    isSyncing || dashboardState is UiState.Loading ||
                            announcementListState is UiState.Loading ||
                            mutationState is UiState.Loading
                }.collectLatest { isLoading ->
                    binding.swipeRefreshLayout.isRefreshing = isLoading
                }
            }
        }
    }

    private fun observeDashboardStats() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                dashboardViewModel.dashboardState.collectLatest { state ->
                    when (state) {
                        is UiState.Success -> updateDashboardUI(state.data)
                        is UiState.Error -> DialogUtils.showAlert(
                            this@TeacherDashboardActivity, "Error", state.message
                        )

                        else -> Unit
                    }
                }
            }
        }
    }

    private fun observeAnnouncements() {
        // 1. List Observer
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                announcementViewModel.latestAnnouncementsState.collectLatest { state ->
                    when (state) {
                        is UiState.Success -> {
                            val list = state.data
                            if (list.isEmpty()) {
                                binding.announcementPager.visibility = View.GONE
                                binding.tabLayout.visibility = View.GONE
                                binding.announcementPlaceholder.visibility = View.VISIBLE
                            } else {
                                binding.announcementPager.visibility = View.VISIBLE
                                binding.tabLayout.visibility = View.VISIBLE
                                binding.announcementPlaceholder.visibility = View.GONE
                                adapter.updateData(list)
                            }
                        }

                        is UiState.Error -> DialogUtils.showAlert(
                            this@TeacherDashboardActivity, "Error", state.message
                        )

                        else -> Unit
                    }
                }
            }
        }

        // 2. Mutation Observer (Add/Delete results)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Changed from addUpdateAnnouncementState to mutationState
                announcementViewModel.mutationState.collectLatest { state ->
                    when (state) {
                        is UiState.Success -> {
                            Toast.makeText(
                                this@TeacherDashboardActivity,
                                "Operation Successful",
                                Toast.LENGTH_SHORT
                            ).show()
                            announcementViewModel.resetMutationState()
                        }

                        is UiState.Error -> {
                            DialogUtils.showAlert(
                                this@TeacherDashboardActivity,
                                "Error",
                                state.message
                            )
                            announcementViewModel.resetMutationState()
                        }

                        else -> Unit
                    }
                }
            }
        }
    }

    private fun observeAttendanceState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                attendanceViewModel.saveState.collectLatest { state ->
                    when (state) {
                        is UiState.Loading -> {
                            DialogUtils.showLoading(supportFragmentManager, "Marking Present...")
                        }

                        is UiState.Success -> {
                            DialogUtils.hideLoading(supportFragmentManager)
                            // ✅ Success Dialog with Time
                            val time =
                                java.text.SimpleDateFormat("hh:mm a", Locale.getDefault())
                                    .format(java.util.Date())
                            DialogUtils.showAlert(
                                this@TeacherDashboardActivity,
                                "Success",
                                "Attendance Marked Successfully at $time"
                            )
                            attendanceViewModel.resetSaveState()
                        }

                        is UiState.Error -> {
                            DialogUtils.hideLoading(supportFragmentManager)
                            DialogUtils.showAlert(
                                this@TeacherDashboardActivity,
                                "Error",
                                state.message
                            )
                            attendanceViewModel.resetSaveState()
                        }

                        else -> Unit
                    }
                }
            }
        }
    }

    private fun observeLoggedInUser() {
        lifecycleScope.launch {
            // 1. Initial Fetch
            loggedInUser = userViewModel.getCurrentUser()
            updateUserHeader(loggedInUser)

            // 2. Retry if Class Info is missing (Sync might be in progress)
            if (loggedInUser != null && loggedInUser?.className.isNullOrBlank()) {
                repeat(5) { // Retry 5 times (5 seconds max)
                    delay(1000)
                    val refreshed = userViewModel.getCurrentUser()
                    if (refreshed?.className?.isNotBlank() == true) {
                        loggedInUser = refreshed
                        updateUserHeader(loggedInUser)
                        return@repeat
                    }
                }
            }
        }
    }

    private fun updateUserHeader(user: User?) {
        if (user != null) {
            binding.tvWelcomeName.text = "Hi, ${user.name}"
            binding.tvTeacherClassInfo.text = user.className.ifBlank { "Loading..." }
            binding.tvTeacherDivisionInfo.text = user.divisionName.ifBlank { "" }
        }
    }

    private fun updateDashboardUI(stats: DashboardStats) {
        // --- Top Card (Attendance & Strength) ---
        // --- Top Card (Attendance & Strength) ---
        val dateFormat = java.text.SimpleDateFormat(
            "EEEE, MMMM d, yyyy",
            java.util.Locale.getDefault()
        )
        binding.tvTodayDate.text = dateFormat.format(java.util.Date())
        binding.tvMyStudents.text = stats.studentsCount.toString()
        binding.tvPresentToday.text = stats.presentCount.toString()
        binding.tvAbsentToday.text = stats.absentCount.toString()
        binding.tvLeaveToday.text = stats.leaveCount.toString()

        // --- Bottom Card (REPURPOSED: Fee Collection) ---

        // 1. Title Change (XML me ya yahan)
        // Behtar hai XML me ID change karein, par abhi code se text badal rahe hain:
        // binding.tvCardTitle.text = "Fee Collection" (Agar ID hoti)

        // 2. Percentage
        binding.tvAttendancePercentage.text = "${stats.feePercentage}%" // Reusing ID
        binding.progressAttendance.progress = stats.feePercentage // Reusing ID

        // 3. Values (Currency Format)
        val formatter = NumberFormat.getCurrencyInstance(
            Locale.Builder().setLanguage("en").setRegion("IN").build()
        )
        binding.tvPresentCount.text =
            "Collected: ${formatter.format(stats.totalFeeCollected)}" // Reusing ID
        binding.tvPresentCount.setTextColor(getColor(R.color.success)) // Green color

        binding.tvAbsentCount.text =
            "Pending: ${formatter.format(stats.totalFeePending)}" // Reusing ID
        binding.tvAbsentCount.setTextColor(getColor(R.color.failure)) // Red color
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.teacher_dashboard_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_logout -> {
                DialogUtils.showConfirmation(
                    this,
                    "Logout",
                    "Are you sure you want to logout?",
                    onConfirmed = { logoutManager.logout(this) }
                )
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }
}