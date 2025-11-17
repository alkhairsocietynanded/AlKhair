package com.zabibtech.alkhair.ui.dashboard

import android.R.attr.clipChildren
import android.R.attr.clipToPadding
import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.CompositePageTransformer
import androidx.viewpager2.widget.MarginPageTransformer
import com.google.android.material.tabs.TabLayoutMediator
import com.zabibtech.alkhair.R
import com.zabibtech.alkhair.adapters.AnnouncementPagerAdapter
import com.zabibtech.alkhair.databinding.ActivityAdminDashboardBinding
import com.zabibtech.alkhair.model.Announcement
import com.zabibtech.alkhair.ui.classmanager.ClassManagerActivity
import com.zabibtech.alkhair.ui.fees.FeesActivity
import com.zabibtech.alkhair.ui.homework.HomeworkActivity
import com.zabibtech.alkhair.ui.main.DashboardStats
import com.zabibtech.alkhair.ui.main.MainViewModel
import com.zabibtech.alkhair.ui.salary.SalaryActivity
import com.zabibtech.alkhair.ui.user.UserListActivity
import com.zabibtech.alkhair.utils.DialogUtils
import com.zabibtech.alkhair.utils.LogoutManager
import com.zabibtech.alkhair.utils.Modes
import com.zabibtech.alkhair.utils.Roles
import com.zabibtech.alkhair.utils.UiState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

@AndroidEntryPoint
class AdminDashboardActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAdminDashboardBinding
    private val mainViewModel: MainViewModel by viewModels()

    @Inject
    lateinit var logoutManager: LogoutManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityAdminDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        setupToolbar()
        observeViewModel()
        setupAnnouncementViewPager()

        mainViewModel.loadDashboardStats()

        binding.cardTeachers.setOnClickListener {
            startActivity(
                Intent(this, UserListActivity::class.java)
                    .putExtra("role", "teacher")
                    .putExtra("mode", "create")
            )
        }

        binding.cardStudents.setOnClickListener {
            startActivity(
                Intent(this, UserListActivity::class.java)
                    .putExtra("role", "student")
                    .putExtra("mode", "create")
            )
        }

        binding.cardClasses.setOnClickListener {
            startActivity(Intent(this, ClassManagerActivity::class.java))
        }

        binding.cardHomework.setOnClickListener {
            val intent = Intent(this@AdminDashboardActivity, HomeworkActivity::class.java)
            startActivity(intent)
        }

        binding.cardStudentAttendance.setOnClickListener {
            // Attendance mode ke liye ClassManagerActivity khole
            val intent = Intent(this, ClassManagerActivity::class.java).apply {
                putExtra("mode", Modes.ATTENDANCE)
                putExtra("role", Roles.STUDENT)
            }
            startActivity(intent)
        }
        binding.cardTeacherAttendance.setOnClickListener {
            // Attendance mode ke liye ClassManagerActivity khole
            val intent = Intent(this, ClassManagerActivity::class.java).apply {
                putExtra("mode", Modes.ATTENDANCE)
                putExtra("role", Roles.TEACHER)
            }
            startActivity(intent)
        }

        binding.cardFees.setOnClickListener {
            startActivity(Intent(this, FeesActivity::class.java))
        }

        binding.cardSalary.setOnClickListener {
            startActivity(Intent(this, SalaryActivity::class.java))
        }

        binding.cardLogout.setOnClickListener {
            logoutManager.logout(this)
        }
    }

    private fun setupAnnouncementViewPager() {
        val announcements = listOf(
            Announcement(
                "Annual Sports Day",
                "The annual sports day will be held on December 20th. All students are requested to participate."
            ),
            Announcement(
                "Winter Vacations",
                "The school will remain closed for winter vacations from December 25th to January 5th."
            ),
            Announcement(
                "Parent-Teacher Meeting",
                "A parent-teacher meeting will be held on November 30th to discuss the progress of the students."
            ),
            Announcement(
                "Parent-Teacher Meeting",
                "A parent-teacher meeting will be held on November 30th to discuss the progress of the students."
            ),
            Announcement(
                "Parent-Teacher Meeting",
                "A parent-teacher meeting will be held on November 30th to discuss the progress of the students."
            )
        )

        val adapter = AnnouncementPagerAdapter(announcements)
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

        TabLayoutMediator(binding.tabLayout, binding.announcementPager) { _, _ ->
        }.attach()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            mainViewModel.dashboardState.collect { state ->
                when (state) {
                    is UiState.Loading -> {
                        DialogUtils.showLoading(supportFragmentManager)
                    }

                    is UiState.Success -> {
                        DialogUtils.hideLoading(supportFragmentManager)
                        updateDashboardUI(state.data)
                    }

                    is UiState.Error -> {
                        DialogUtils.hideLoading(supportFragmentManager)
                    }

                    is UiState.Idle -> {
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

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
    }
}