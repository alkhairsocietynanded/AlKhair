package com.zabibtech.alkhair.ui.dashboard

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zabibtech.alkhair.data.manager.AppDataSyncManager
import com.zabibtech.alkhair.data.manager.AttendanceRepoManager
import com.zabibtech.alkhair.data.manager.FeesRepoManager
import com.zabibtech.alkhair.data.manager.UserRepoManager
import com.zabibtech.alkhair.data.models.Attendance
import com.zabibtech.alkhair.data.models.DashboardStats
import com.zabibtech.alkhair.data.models.FeesModel
import com.zabibtech.alkhair.data.models.User
import com.zabibtech.alkhair.di.ApplicationScope

import com.zabibtech.alkhair.utils.DateUtils
import com.zabibtech.alkhair.utils.Roles
import com.zabibtech.alkhair.utils.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class TeacherDashboardViewModel @Inject constructor(
    private val appDataSyncManager: AppDataSyncManager,
    userRepoManager: UserRepoManager,
    attendanceRepoManager: AttendanceRepoManager,
    feesRepoManager: FeesRepoManager,
    @param:ApplicationScope private val externalScope: CoroutineScope
) : ViewModel() {

    init {
        triggerBackgroundSync()
    }



    private fun triggerBackgroundSync() {
        externalScope.launch {
            try {
                appDataSyncManager.syncAllData()
            } catch (e: Exception) {
                Log.e("TeacherDashboardVM", "Sync failed", e)
            }
        }
    }

    private val todayDate = DateUtils.formatDate(Calendar.getInstance())

    @OptIn(ExperimentalCoroutinesApi::class)
    val dashboardState: StateFlow<UiState<DashboardStats>> =
        combine(
            userRepoManager.observeLocal(),
            attendanceRepoManager.observeAttendanceByDate(todayDate),
            feesRepoManager.observeLocal()
        ) { users, attendanceRecords, allFees ->

            // 1. Get My Students
            val myStudents = users.filter { it.role == Roles.STUDENT }

            // ✅ Create a Set of Student IDs for fast lookup
            val myStudentIds = myStudents.map { it.uid }.toSet()

            // --- ATTENDANCE & STRENGTH (FIXED) ---

            // ✅ Filter Attendance: Only count if the ID belongs to a Student
            val studentAttendance = attendanceRecords.filter {
                it.studentId in myStudentIds
            }

            val present = studentAttendance.count { it.status == "Present" }
            val absent = studentAttendance.count { it.status == "Absent" }
            val leave = studentAttendance.count { it.status == "Leave" }

            // Percentage based on Total Students (strength), not just marked ones
            val attendancePercentage = if (myStudents.isNotEmpty()) {
                (present * 100) / myStudents.size
            } else {
                0
            }

            // --- FEES LOGIC (Current Month Only) ---
            // Note: myStudentIds variable reused here

            val currentMonthFilter = DateUtils.getCurrentMonthForFee()

            val currentMonthFees = allFees.filter {
                it.studentId in myStudentIds && it.monthYear == currentMonthFilter
            }

            val totalCollectedThisMonth = currentMonthFees.sumOf { it.paidAmount }

            val totalExpectedMonthly = myStudents.sumOf { student ->
                student.totalFees
            }

            val totalPendingThisMonth =
                (totalExpectedMonthly - totalCollectedThisMonth).coerceAtLeast(0.0)

            val feePerc = if (totalExpectedMonthly > 0.0) {
                ((totalCollectedThisMonth / totalExpectedMonthly) * 100).toInt()
            } else {
                0
            }

            DashboardStats(
                studentsCount = myStudents.size,
                teachersCount = 0,
                classesCount = 1,
                presentCount = present,
                absentCount = absent,
                leaveCount = leave,
                attendancePercentage = attendancePercentage,
                totalFeeCollected = totalCollectedThisMonth,
                totalFeePending = totalPendingThisMonth,
                feePercentage = feePerc
            )
        }
            .flowOn(Dispatchers.Default)
            .map { stats -> UiState.Success(stats) as UiState<DashboardStats> }
            .onStart { emit(UiState.Loading) }
            .catch { emit(UiState.Error(it.message ?: "Failed to load dashboard")) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                UiState.Idle
            )
}