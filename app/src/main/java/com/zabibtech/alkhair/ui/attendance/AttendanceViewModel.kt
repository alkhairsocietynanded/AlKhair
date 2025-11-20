package com.zabibtech.alkhair.ui.attendance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zabibtech.alkhair.data.repository.AttendanceRepository
import com.zabibtech.alkhair.utils.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AttendanceViewModel @Inject constructor(
    private val attendanceRepo: AttendanceRepository
) : ViewModel() {

    private val _attendanceState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val attendanceState: StateFlow<UiState<Unit>> = _attendanceState

    private val _attendanceLoadState = MutableStateFlow<UiState<Map<String, String>>>(UiState.Idle)
    val attendanceLoadState: StateFlow<UiState<Map<String, String>>> = _attendanceLoadState

    private val _userAttendance = MutableStateFlow<UiState<Map<String, Map<String, String>>>>(UiState.Idle)
    val userAttendance: StateFlow<UiState<Map<String, Map<String, String>>>> = _userAttendance


    // ✅ selectedDate ke hisaab se save
    fun saveAttendanceForClass(classId: String?, date: String, attendanceMap: Map<String, String>) {
        if (classId == null || attendanceMap.isEmpty()) return

        _attendanceState.value = UiState.Loading
        viewModelScope.launch {
            try {
                attendanceRepo.saveAttendanceForClass(classId, date, attendanceMap)
                _attendanceState.value = UiState.Success(Unit)
            } catch (e: Exception) {
                _attendanceState.value =
                    UiState.Error(e.localizedMessage ?: "Failed to save attendance")
            }
        }
    }

    fun loadAttendanceForClass(classId: String?, date: String) {
        if (classId == null) return
        _attendanceLoadState.value = UiState.Loading
        viewModelScope.launch {
            try {
                val data = attendanceRepo.getAttendanceForClass(classId, date)
                _attendanceLoadState.value = UiState.Success(data)
            } catch (e: Exception) {
                _attendanceLoadState.value =
                    UiState.Error(e.localizedMessage ?: "Failed to load attendance")
            }
        }
    }

    fun loadAttendanceForUser(userId: String) {
        _userAttendance.value = UiState.Loading
        viewModelScope.launch {
            try {
                val data = attendanceRepo.getAttendanceForUser(userId)
                _userAttendance.value = UiState.Success(data)
            } catch (e: Exception) {
                _userAttendance.value = UiState.Error(e.localizedMessage ?: "Failed to load user attendance")
            }
        }
    }
}
