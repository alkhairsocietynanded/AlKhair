package com.zabibtech.alkhair.ui.dashboard

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zabibtech.alkhair.data.manager.*
import com.zabibtech.alkhair.data.models.DashboardStats
import com.zabibtech.alkhair.di.ApplicationScope
import com.zabibtech.alkhair.ui.main.Temp
import com.zabibtech.alkhair.utils.DateUtils
import com.zabibtech.alkhair.utils.Roles
import com.zabibtech.alkhair.utils.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
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

    // In your Activity or ViewModel
    fun runMigrationScript() {
        viewModelScope.launch {
            Log.d("Migration", "Starting User Migration...")
            Temp.runFullSystemMigration()
        }
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
        ) { users,  attendanceRecords, allFees ->

            // --- ATTENDANCE & STRENGTH ---
            val myStudents = users.filter { it.role == Roles.STUDENT }

            val present = attendanceRecords.count { it.status == "Present" }
            val absent = attendanceRecords.count { it.status == "Absent" }
            val leave = attendanceRecords.count { it.status == "Leave" }
            val totalMarked = present + absent + leave
            val attendancePercentage = if (totalMarked > 0) (present * 100) / totalMarked else 0

            // --- FEES LOGIC (Current Month Only) ---
            val myStudentIds = myStudents.map { it.uid }.toSet()

            // 1. Current Month Format (e.g., "2025-Jan")
            // Ensure DateUtils.currentMonthYear() returns format matching FeesModel (e.g. "YYYY-MMM")
            // Agar aapka format "2025-01" hai to waisa banayein.
            val currentMonthFilter = DateUtils.getCurrentMonthForFee()

            // 2. Filter Fees: Sirf Meri Class + Sirf Is Mahine ki Fees
            val currentMonthFees = allFees.filter {
                it.studentId in myStudentIds && it.monthYear == currentMonthFilter
            }

            // 3. Total Collected (Only for this month)
            val totalCollectedThisMonth = currentMonthFees.sumOf { it.paidAmount }

            // 4. Total Expected (Monthly rate of all active students)
            // Note: Inactive students ko filter kar sakte hain agar chahein
            val totalExpectedMonthly = myStudents.sumOf { student ->
                student.totalFees.toDoubleOrNull() ?: 0.0
            }

            // 5. Pending (For this month)
            val totalPendingThisMonth =
                (totalExpectedMonthly - totalCollectedThisMonth).coerceAtLeast(0.0)

            // 6. Percentage
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

                // ✅ Updated Fee Stats
                totalFeeCollected = totalCollectedThisMonth,
                totalFeePending = totalPendingThisMonth,
                feePercentage = feePerc
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