package com.zabibtech.alkhair.ui.dashboard

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
import com.google.android.material.tabs.TabLayoutMediator
import com.zabibtech.alkhair.R
import com.zabibtech.alkhair.data.models.DashboardStats
import com.zabibtech.alkhair.data.models.User
import com.zabibtech.alkhair.databinding.ActivityTeacherDashboardBinding
import com.zabibtech.alkhair.ui.announcement.AddAnnouncementSheet
import com.zabibtech.alkhair.ui.announcement.AnnouncementPagerAdapter
import com.zabibtech.alkhair.ui.announcement.AnnouncementViewModel
import com.zabibtech.alkhair.ui.attendance.AttendanceActivity
import com.zabibtech.alkhair.ui.attendance.AttendanceViewModel
import com.zabibtech.alkhair.ui.fees.FeesActivity
import com.zabibtech.alkhair.ui.homework.HomeworkActivity
import com.zabibtech.alkhair.ui.salary.SalaryActivity
import com.zabibtech.alkhair.ui.scanner.QRScannerActivity
import com.zabibtech.alkhair.ui.user.UserListActivity
import com.zabibtech.alkhair.ui.user.UserViewModel
import com.zabibtech.alkhair.utils.Constants
import com.zabibtech.alkhair.utils.DialogUtils
import com.zabibtech.alkhair.utils.LogoutManager
import com.zabibtech.alkhair.utils.Modes
import com.zabibtech.alkhair.utils.Roles
import com.zabibtech.alkhair.utils.UiState
import dagger.hilt.android.AndroidEntryPoint
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
            if (loggedInUser != null) {
                val intent = Intent(this, AttendanceActivity::class.java).apply {
                    putExtra("role", Roles.STUDENT)
                    putExtra("loggedInUser", loggedInUser)
                }
                startActivity(intent)
            } else {
                Toast.makeText(this, "Fetching teacher info... try again", Toast.LENGTH_SHORT)
                    .show()
            }
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

        binding.fabQRCode.setOnClickListener {
            startQrScanner()
        }

        binding.swipeRefreshLayout.setOnRefreshListener {
            // Data refreshes automatically via Flow when DB updates (Background sync)
            binding.swipeRefreshLayout.isRefreshing = false
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
                    announcementViewModel.mutationState // Updated here
                ) { dashboardState, announcementListState, mutationState ->
                    dashboardState is UiState.Loading ||
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
            // Background mein teacher ki details le aayein
            loggedInUser = userViewModel.getCurrentUser()
            if (loggedInUser != null) {
                binding.tvWelcomeName.text = "Welcome, ${loggedInUser!!.name}"
                binding.tvTeacherClassInfo.text = loggedInUser!!.className
                binding.tvTeacherDivisionInfo.text = loggedInUser!!.divisionName
            }
        }
    }

    private fun updateDashboardUI(stats: DashboardStats) {
        // --- Top Card (Attendance & Strength) ---
        binding.tvTodayDate.text = com.zabibtech.alkhair.utils.DateUtils.today()
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