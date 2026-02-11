package com.aewsn.alkhair.ui.student

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aewsn.alkhair.data.manager.AnnouncementRepoManager
import com.aewsn.alkhair.data.manager.AttendanceRepoManager
import com.aewsn.alkhair.data.manager.UserRepoManager
import com.aewsn.alkhair.data.models.User
import com.aewsn.alkhair.utils.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StudentViewModel @Inject constructor(
    private val userRepoManager: UserRepoManager,
    private val attendanceRepoManager: AttendanceRepoManager,
    private val announcementRepoManager: AnnouncementRepoManager,
    private val appDataSyncManager: com.aewsn.alkhair.data.manager.AppDataSyncManager,
    private val leaveRepoManager: com.aewsn.alkhair.data.manager.LeaveRepoManager
) : ViewModel() {

    private val _leaveSubmissionState = MutableStateFlow<UiState<Boolean>>(UiState.Idle)
    val leaveSubmissionState: StateFlow<UiState<Boolean>> = _leaveSubmissionState.asStateFlow()

    private val _currentUser = MutableStateFlow<UiState<User>>(UiState.Loading)
    val currentUser: StateFlow<UiState<User>> = _currentUser.asStateFlow()

    private val _attendancePercent = MutableStateFlow<UiState<Int>>(UiState.Loading)
    val attendancePercent: StateFlow<UiState<Int>> = _attendancePercent.asStateFlow()

    private val _announcements =
        MutableStateFlow<UiState<List<com.aewsn.alkhair.data.models.Announcement>>>(UiState.Loading)
    val announcements: StateFlow<UiState<List<com.aewsn.alkhair.data.models.Announcement>>> =
        _announcements.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    init {
        fetchCurrentUser()
        fetchAnnouncements()
        triggerSync()
    }

    private fun triggerSync() {
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                appDataSyncManager.syncAllData()
            } catch (_: Exception) {
                // Log.e("StudentViewModel", "Sync failed", e)
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun refreshData() {
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                fetchCurrentUser()
                fetchAnnouncements()
                appDataSyncManager.syncAllData()
            } catch (_: Exception) {
               // Log.e("StudentViewModel", "Sync failed", e)
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun fetchCurrentUser() {
        viewModelScope.launch {
            _currentUser.value = UiState.Loading
            try {
                val user = userRepoManager.getCurrentUser()
                if (user != null) {
                    _currentUser.value = UiState.Success(user)
                    fetchAttendance(user.uid)
                } else {
                    _currentUser.value = UiState.Error("User not logged in or profile missing")
                }
            } catch (e: Exception) {
                _currentUser.value = UiState.Error(e.message ?: "Failed to load profile")
            }
        }
    }

    private fun fetchAttendance(studentId: String) {
        viewModelScope.launch {
            attendanceRepoManager.observeAttendanceByStudent(studentId).collect { list ->
                if (list.isNotEmpty()) {
                    val totalDays = list.size
                    val presentDays = list.count { it.status.equals("Present", ignoreCase = true) }
                    val percent = (presentDays * 100) / totalDays
                    _attendancePercent.value = UiState.Success(percent)
                } else {
                    _attendancePercent.value = UiState.Success(0)
                }
            }
        }
    }

    private fun fetchAnnouncements() {
        viewModelScope.launch {
            announcementRepoManager.observeLatestAnnouncements().collect { list ->
                android.util.Log.d("StudentViewModel", "Announcements observed: ${list.size}")
                if (list.isNotEmpty()) {
                    _announcements.value = UiState.Success(list)
                } else {
                    _announcements.value = UiState.Success(emptyList())
                }
            }
        }
    }



    fun applyLeave(startDate: String, endDate: String, reason: String) {
        viewModelScope.launch {
            val user = (_currentUser.value as? UiState.Success)?.data ?: return@launch
            _leaveSubmissionState.value = UiState.Loading
            
            val leave = com.aewsn.alkhair.data.models.Leave(
                studentId = user.uid,
                startDate = startDate,
                endDate = endDate,
                reason = reason
            )

            val result = leaveRepoManager.applyLeave(leave)
            result.onSuccess {
                _leaveSubmissionState.value = UiState.Success(true)
            }.onFailure { e ->
                _leaveSubmissionState.value = UiState.Error(e.message ?: "Failed to submit leave")
            }
        }
    }

    fun updateLeave(leave: com.aewsn.alkhair.data.models.Leave) {
        viewModelScope.launch {
            _leaveSubmissionState.value = UiState.Loading
            val result = leaveRepoManager.updateLeave(leave)
            result.onSuccess {
                _leaveSubmissionState.value = UiState.Success(true)
            }.onFailure { e ->
                _leaveSubmissionState.value = UiState.Error(e.message ?: "Failed to update leave")
            }
        }
    }

    fun resetLeaveState() {
        _leaveSubmissionState.value = UiState.Idle
    }
}
