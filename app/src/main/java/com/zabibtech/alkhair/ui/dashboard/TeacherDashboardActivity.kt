package com.zabibtech.alkhair.ui.dashboard

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
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
import com.zabibtech.alkhair.databinding.ActivityTeacherDashboardBinding
import com.zabibtech.alkhair.ui.announcement.AddAnnouncementSheet
import com.zabibtech.alkhair.ui.announcement.AnnouncementPagerAdapter
import com.zabibtech.alkhair.ui.announcement.AnnouncementViewModel
import com.zabibtech.alkhair.ui.classmanager.ClassManagerActivity
import com.zabibtech.alkhair.ui.fees.FeesActivity
import com.zabibtech.alkhair.ui.homework.HomeworkActivity
import com.zabibtech.alkhair.ui.salary.SalaryActivity
import com.zabibtech.alkhair.ui.user.UserListActivity
import com.zabibtech.alkhair.utils.DialogUtils
import com.zabibtech.alkhair.utils.LogoutManager
import com.zabibtech.alkhair.utils.Modes
import com.zabibtech.alkhair.utils.Roles
import com.zabibtech.alkhair.utils.UiState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

@AndroidEntryPoint
class TeacherDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTeacherDashboardBinding
    private lateinit var adapter: AnnouncementPagerAdapter
    private val adminDashboardViewModel: AdminDashboardViewModel by viewModels()
    private val announcementViewModel: AnnouncementViewModel by viewModels()

    @Inject
    lateinit var logoutManager: LogoutManager

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
            val intent = Intent(this, ClassManagerActivity::class.java).apply {
                putExtra("mode", Modes.ATTENDANCE)
                putExtra("role", Roles.STUDENT)
            }
            startActivity(intent)
        }

        binding.cardFees.setOnClickListener {
            startActivity(Intent(this, FeesActivity::class.java))
        }

        binding.cardSalary.setOnClickListener {
            startActivity(Intent(this, SalaryActivity::class.java))
        }

        // --- Actions ---
        binding.cardLogout.setOnClickListener {
            logoutManager.logout(this)
        }

        binding.fabAnnounce.setOnClickListener {
            val addAnnouncementSheet = AddAnnouncementSheet()
            addAnnouncementSheet.show(supportFragmentManager, AddAnnouncementSheet.TAG)
        }

        binding.swipeRefreshLayout.setOnRefreshListener {
            // Data refreshes automatically via Flow when DB updates (Background sync)
            binding.swipeRefreshLayout.isRefreshing = false
        }
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
                    adminDashboardViewModel.dashboardState,
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
                adminDashboardViewModel.dashboardState.collectLatest { state ->
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
                            Toast.makeText(this@TeacherDashboardActivity, "Operation Successful", Toast.LENGTH_SHORT).show()
                            announcementViewModel.resetMutationState()
                        }
                        is UiState.Error -> {
                            DialogUtils.showAlert(this@TeacherDashboardActivity, "Error", state.message)
                            announcementViewModel.resetMutationState()
                        }
                        else -> Unit
                    }
                }
            }
        }
    }

    private fun updateDashboardUI(stats: DashboardStats) {
        binding.tvTotalStudents.text = stats.studentsCount.toString()
        binding.tvTotalTeachers.text = stats.teachersCount.toString()
        binding.tvTotalClasses.text = stats.classesCount.toString()
        binding.tvAttendancePercentage.text = "${stats.attendancePercentage}%"
        binding.progressAttendance.progress = stats.attendancePercentage
        binding.tvPresentCount.text = "Present: ${stats.presentCount}"
        binding.tvAbsentCount.text = "Absent: ${stats.absentCount}"
    }
}