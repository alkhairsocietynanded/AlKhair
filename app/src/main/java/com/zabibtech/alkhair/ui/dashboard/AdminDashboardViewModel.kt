package com.zabibtech.alkhair.ui.dashboard

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zabibtech.alkhair.data.manager.AppDataSyncManager
import com.zabibtech.alkhair.data.manager.AttendanceRepoManager
import com.zabibtech.alkhair.data.manager.ClassDivisionRepoManager
import com.zabibtech.alkhair.data.manager.UserRepoManager
import com.zabibtech.alkhair.data.models.DashboardStats
import com.zabibtech.alkhair.di.ApplicationScope
import com.zabibtech.alkhair.ui.main.Temp
import com.zabibtech.alkhair.utils.DateUtils
import com.zabibtech.alkhair.utils.Roles
import com.zabibtech.alkhair.utils.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
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



@HiltViewModel
class AdminDashboardViewModel @Inject constructor(
    private val appDataSyncManager: AppDataSyncManager,
    userRepoManager: UserRepoManager,
    attendanceRepoManager: AttendanceRepoManager,
    classDivisionRepoManager: ClassDivisionRepoManager,
    // ✅ Inject the Application Scope
    @param:ApplicationScope private val externalScope: CoroutineScope
) : ViewModel() {
    init {
        triggerBackgroundSync()
    }
    private fun triggerBackgroundSync() {
        // ✅ 2. Use externalScope to run sync
        // This coroutine will NOT be cancelled when MainActivity finishes.
        externalScope.launch {
            try {
                appDataSyncManager.syncAllData()
            } catch (e: Exception) {
                Log.e("MainViewModel", "Background sync failed", e)
            }
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