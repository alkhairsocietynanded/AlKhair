package com.zabibtech.alkhair.ui.attendance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zabibtech.alkhair.data.manager.AttendanceRepoManager
import com.zabibtech.alkhair.utils.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AttendanceViewModel @Inject constructor(
    private val attendanceRepoManager: AttendanceRepoManager
) : ViewModel() {

    // For saving attendance operation
    private val _saveState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val saveState: StateFlow<UiState<Unit>> = _saveState

    // For loading attendance data for the UI. The Map represents <StudentId, Status>
    private val _attendanceState = MutableStateFlow<UiState<Map<String, String>>>(UiState.Idle)
    val attendanceState: StateFlow<UiState<Map<String, String>>> = _attendanceState

    /**
     * Gets the attendance for a given class and date using an offline-first approach.
     * It will first try to get fresh data from the local cache.
     * If the cache is stale or empty, it will fetch from the remote and update the cache.
     */
    fun getAttendance(classId: String, date: String) {
        _attendanceState.value = UiState.Loading
        viewModelScope.launch {
            attendanceRepoManager.getAttendanceForClassOnDate(classId, date).fold(
                onSuccess = { attendanceList ->
                    // The UI expects a Map<studentId, status>, so we convert our List<Attendance> to that.
                    val attendanceMap = attendanceList.associate { it.studentId to it.status }
                    _attendanceState.value = UiState.Success(attendanceMap)
                },
                onFailure = { error ->
                    _attendanceState.value = UiState.Error(error.localizedMessage ?: "Failed to get attendance")
                }
            )
        }
    }

    /**
     * Saves the attendance for a given class and date.
     */
    fun saveAttendance(classId: String, date: String, attendanceMap: Map<String, String>) {
        if (attendanceMap.isEmpty()) {
            _saveState.value = UiState.Error("Attendance list is empty.")
            return
        }

        _saveState.value = UiState.Loading
        viewModelScope.launch {
            attendanceRepoManager.saveAttendance(classId, date, attendanceMap).fold(
                onSuccess = {
                    _saveState.value = UiState.Success(Unit)
                },
                onFailure = { error ->
                    _saveState.value = UiState.Error(error.localizedMessage ?: "Failed to save attendance")
                }
            )
        }
    }

    fun resetSaveState() {
        _saveState.value = UiState.Idle
    }
}