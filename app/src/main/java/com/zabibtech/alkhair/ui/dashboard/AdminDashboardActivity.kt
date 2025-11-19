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
import com.zabibtech.alkhair.data.models.Announcement
import com.zabibtech.alkhair.databinding.ActivityAdminDashboardBinding
import com.zabibtech.alkhair.ui.announcement.AddAnnouncementSheet
import com.zabibtech.alkhair.ui.announcement.AnnouncementPagerAdapter
import com.zabibtech.alkhair.ui.announcement.AnnouncementViewModel
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

@AndroidEntryPoint
class AdminDashboardActivity : AppCompatActivity() {
    private lateinit var adapter: AnnouncementPagerAdapter
    private lateinit var binding: ActivityAdminDashboardBinding
    private val mainViewModel: MainViewModel by viewModels()
    val announcementViewModel: AnnouncementViewModel by viewModels()

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
        setupListeners()
        observeMainViewModel()
        observeAnnouncementViewModel()
        setupAnnouncementViewPager()

        mainViewModel.loadDashboardStats()
        announcementViewModel.loadFiveLatestAnnouncements()

        binding.fabAnnounce.setOnClickListener {
            val addAnnouncementSheet = AddAnnouncementSheet()
            addAnnouncementSheet.show(supportFragmentManager, AddAnnouncementSheet.TAG)
        }

        binding.swipeRefreshLayout.setOnRefreshListener {
            announcementViewModel.loadFiveLatestAnnouncements()
            mainViewModel.loadDashboardStats()
            binding.swipeRefreshLayout.isRefreshing = false
        }

    }

    private fun setupListeners() {
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
            val intent = Intent(this, ClassManagerActivity::class.java).apply {
                putExtra("mode", Modes.ATTENDANCE)
                putExtra("role", Roles.STUDENT)
            }
            startActivity(intent)
        }
        binding.cardTeacherAttendance.setOnClickListener {
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


    private fun setupAnnouncementViewPager(announcements: List<Announcement> = emptyList()) {
        adapter = AnnouncementPagerAdapter(
            announcements.toMutableList(),
            onDelete = { announcement ->
                DialogUtils.showConfirmation(
                    context = this,
                    title = "Delete announcement",
                    message = "Are you sure you want to delete ${announcement.title}?",
                    onConfirmed = { announcementViewModel.deleteAnnouncement(announcement.id) })
            })

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

    private fun observeMainViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.dashboardState.collectLatest { state ->

                    binding.swipeRefreshLayout.isRefreshing = state is UiState.Loading

                    when (state) {
                        is UiState.Success -> {
//                            DialogUtils.hideLoading(supportFragmentManager)
                            updateDashboardUI(state.data)
                        }

                        is UiState.Error -> {
                            //DialogUtils.hideLoading(supportFragmentManager)
                            DialogUtils.showAlert(
                                this@AdminDashboardActivity,
                                "Error",
                                state.message
                            )
                        }

                        else -> {
                        }
                    }
                }
            }
        }
    }

    private fun observeAnnouncementViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                announcementViewModel.latestAnnouncementsState.collectLatest { state ->
                    binding.swipeRefreshLayout.isRefreshing = state is UiState.Loading

                    when (state) {

                        is UiState.Success -> {
                            if (state.data.isEmpty()) {
                                binding.announcementPager.visibility = View.GONE
                                binding.tabLayout.visibility = View.GONE
                                binding.tvNoAnnouncements.visibility = View.VISIBLE
                            } else {
                                binding.announcementPager.visibility = View.VISIBLE
                                binding.tabLayout.visibility = View.VISIBLE
                                binding.tvNoAnnouncements.visibility = View.GONE
                                adapter.updateData(state.data)
                            }
                        }

                        is UiState.Error -> {
                            DialogUtils.showAlert(
                                this@AdminDashboardActivity,
                                "Error",
                                state.message
                            )
                        }

                        else -> {}
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                announcementViewModel.addAnnouncementState.collectLatest { state ->

                    binding.swipeRefreshLayout.isRefreshing = state is UiState.Loading

                    when (state) {
                        is UiState.Success -> {
                            DialogUtils.hideLoading(supportFragmentManager)
                            Toast.makeText(
                                this@AdminDashboardActivity,
                                "Announcement Saved!",
                                Toast.LENGTH_SHORT
                            ).show()
                            announcementViewModel.resetAddAnnouncementState()
                        }

                        is UiState.Error -> {
                            DialogUtils.showAlert(
                                this@AdminDashboardActivity,
                                "Error",
                                state.message
                            )
                            announcementViewModel.resetAddAnnouncementState()
                        }

                        else -> {}
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
