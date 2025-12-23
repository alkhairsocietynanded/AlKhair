package com.zabibtech.alkhair.ui.main

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zabibtech.alkhair.data.manager.AppDataSyncManager
import com.zabibtech.alkhair.data.manager.AttendanceRepoManager
import com.zabibtech.alkhair.data.manager.AuthRepoManager
import com.zabibtech.alkhair.data.manager.ClassDivisionRepoManager
import com.zabibtech.alkhair.data.manager.UserRepoManager
import com.zabibtech.alkhair.data.models.User
import com.zabibtech.alkhair.utils.DateUtils
import com.zabibtech.alkhair.utils.Roles
import com.zabibtech.alkhair.utils.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class DashboardStats(
    val studentsCount: Int = 0,
    val teachersCount: Int = 0,
    val classesCount: Int = 0,
    val attendancePercentage: Int = 0,
    val presentCount: Int = 0,
    val absentCount: Int = 0
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val appDataSyncManager: AppDataSyncManager,
    private val authRepoManager: AuthRepoManager,
    private val userRepoManager: UserRepoManager,
    private val attendanceRepoManager: AttendanceRepoManager,
    private val classDivisionRepoManager: ClassDivisionRepoManager
) : ViewModel() {

    /* ============================================================
       👤 USER SESSION STATE
       ============================================================ */

    private val _userSessionState = MutableStateFlow<UiState<User?>>(UiState.Idle)
    val userSessionState: StateFlow<UiState<User?>> = _userSessionState

    init {
        checkUserSession()
        triggerBackgroundSync()
    }

    private fun checkUserSession() {
        _userSessionState.value = UiState.Loading
        viewModelScope.launch {
            val user = authRepoManager.getCurrentUserUid()?.let { uid ->
                userRepoManager.getUserById(uid)
            }
            // If user is null, it means not logged in or local DB is empty (which requires login)
            _userSessionState.value = UiState.Success(user)
        }
    }

    private fun triggerBackgroundSync() {
        viewModelScope.launch {
            // Run sync in background. It updates local DB, which updates Flows automatically.
            appDataSyncManager.syncAllData()
        }
    }

    /* ============================================================
       📊 DASHBOARD STATS (Reactive Pipeline)
       ============================================================ */

    private val todayDate = DateUtils.formatDate(Calendar.getInstance())

    @OptIn(ExperimentalCoroutinesApi::class)
    val dashboardState: StateFlow<UiState<DashboardStats>> =
        combine(
            userRepoManager.observeLocal(), // All Users Flow
            classDivisionRepoManager.observeClasses(), // All Classes Flow
            attendanceRepoManager.observeAttendanceByDate(todayDate) // Today's Attendance Flow
        ) { users, classes, attendanceRecords ->

            // Calculate Stats on the fly whenever DB changes
            val students = users.count { it.role == Roles.STUDENT }
            val teachers = users.count { it.role == Roles.TEACHER }
            val classCount = classes.size

            val present = attendanceRecords.count { it.status == "Present" }
            val absent = attendanceRecords.count { it.status == "Absent" }
            val totalMarked = present + absent
            val percentage = if (totalMarked > 0) (present * 100) / totalMarked else 0

            DashboardStats(
                studentsCount = students,
                teachersCount = teachers,
                classesCount = classCount,
                presentCount = present,
                absentCount = absent,
                attendancePercentage = percentage
            )
        }
            .map { stats -> UiState.Success(stats) as UiState<DashboardStats> }
            .onStart { emit(UiState.Loading) }
            .catch { emit(UiState.Error(it.message ?: "Failed to load dashboard")) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                UiState.Idle
            )
}