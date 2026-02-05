package com.aewsn.alkhair.ui.user.fragments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aewsn.alkhair.data.manager.AttendanceRepoManager
import com.aewsn.alkhair.data.models.Attendance
import com.aewsn.alkhair.utils.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UserAttendanceViewModel @Inject constructor(
    private val attendanceRepoManager: AttendanceRepoManager,
    private val leaveRepoManager: com.aewsn.alkhair.data.manager.LeaveRepoManager
) : ViewModel() {

    private val _userIdFilter = MutableStateFlow<String?>(null)

    /**
     * Sets the user ID to observe.
     * This triggers the flow pipeline below.
     */
    fun loadAttendanceForUser(userId: String) {
        _userIdFilter.value = userId
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val userAttendance: StateFlow<UiState<List<Attendance>>> = _userIdFilter
        .flatMapLatest { userId ->
            if (userId.isNullOrBlank()) {
                flowOf(UiState.Idle)
            } else {
                // Subscribe to the Repo Flow (SSOT)
                attendanceRepoManager.observeAttendanceByStudent(userId)
                    .map { list ->
                        UiState.Success(list) as UiState<List<Attendance>>
                    }
                    .onStart { emit(UiState.Loading) }
                    .catch { emit(UiState.Error(it.message ?: "Failed to load user attendance")) }
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            UiState.Idle
        )
        
    @OptIn(ExperimentalCoroutinesApi::class)
    val userLeaves: StateFlow<UiState<List<com.aewsn.alkhair.data.models.Leave>>> = _userIdFilter
        .flatMapLatest { userId ->
            if (userId.isNullOrBlank()) {
                flowOf(UiState.Idle)
            } else {
                leaveRepoManager.getLeavesForStudent(userId)
                    .map { list ->
                         UiState.Success(list) as UiState<List<com.aewsn.alkhair.data.models.Leave>>
                    }
                    .onStart { emit(UiState.Loading) }
                    .catch { emit(UiState.Error(it.message ?: "Failed to load leaves")) }
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            UiState.Idle
        )

    fun deleteLeave(leave: com.aewsn.alkhair.data.models.Leave) {
        viewModelScope.launch {
            leaveRepoManager.deleteLeave(leave.id)
                .onSuccess {
                    // Refresh (handled by flow automatically)
                }
                .onFailure {
                    // Handle error (maybe show toast via event channel)
                }
        }
    }

    fun updateLeave(leave: com.aewsn.alkhair.data.models.Leave) {
        viewModelScope.launch {
            leaveRepoManager.updateLeave(leave)
        }
    }
}