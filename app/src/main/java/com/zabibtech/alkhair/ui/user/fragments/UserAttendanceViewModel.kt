package com.zabibtech.alkhair.ui.user.fragments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zabibtech.alkhair.data.manager.AttendanceRepoManager
import com.zabibtech.alkhair.data.models.Attendance
import com.zabibtech.alkhair.utils.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UserAttendanceViewModel @Inject constructor(
    private val attendanceRepoManager: AttendanceRepoManager
) : ViewModel() {

    // The data type is now aligned with what the repository provides: a List of Attendance objects.
    private val _userAttendance = MutableStateFlow<UiState<List<Attendance>>>(UiState.Idle)
    val userAttendance: StateFlow<UiState<List<Attendance>>> = _userAttendance

    /**
     * Fetches the entire attendance history for a single user using an offline-first strategy.
     * Note: This may still rely on an inefficient Firebase query if the local cache is stale.
     */
    fun loadAttendanceForUser(userId: String) {
        if (userId.isBlank()) {
            _userAttendance.value = UiState.Error("User ID is missing.")
            return
        }

        _userAttendance.value = UiState.Loading
        viewModelScope.launch {
            // Use the correct repository function and handle the new return type.
            attendanceRepoManager.getAttendanceForStudent(userId).fold(
                onSuccess = { attendanceList ->
                    _userAttendance.value = UiState.Success(attendanceList)
                },
                onFailure = { error ->
                    _userAttendance.value = UiState.Error(error.localizedMessage ?: "Failed to load user attendance")
                }
            )
        }
    }
}