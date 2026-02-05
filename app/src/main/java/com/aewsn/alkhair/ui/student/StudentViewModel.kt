package com.aewsn.alkhair.ui.student

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aewsn.alkhair.data.manager.AnnouncementRepoManager
import com.aewsn.alkhair.data.manager.AttendanceRepoManager
import com.aewsn.alkhair.data.manager.AuthRepoManager
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
    private val authRepoManager: AuthRepoManager,
    private val userRepoManager: UserRepoManager,
    private val attendanceRepoManager: AttendanceRepoManager,
    private val announcementRepoManager: AnnouncementRepoManager,
    private val appDataSyncManager: com.aewsn.alkhair.data.manager.AppDataSyncManager
) : ViewModel() {

    private val _currentUser = MutableStateFlow<UiState<User>>(UiState.Loading)
    val currentUser: StateFlow<UiState<User>> = _currentUser.asStateFlow()

    private val _attendancePercent = MutableStateFlow<UiState<Int>>(UiState.Loading)
    val attendancePercent: StateFlow<UiState<Int>> = _attendancePercent.asStateFlow()

    private val _announcements =
        MutableStateFlow<UiState<List<com.aewsn.alkhair.data.models.Announcement>>>(UiState.Loading)
    val announcements: StateFlow<UiState<List<com.aewsn.alkhair.data.models.Announcement>>> =
        _announcements.asStateFlow()

    init {
        fetchCurrentUser()
        fetchAnnouncements()
        triggerSync()
    }

    private fun triggerSync() {
        viewModelScope.launch {
            appDataSyncManager.syncAllData()
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
                    val percent = if (totalDays > 0) (presentDays * 100) / totalDays else 0
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

    fun logout() {
        viewModelScope.launch {
            authRepoManager.logout()
        }
    }
}
