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
import javax.inject.Inject

@HiltViewModel
class UserAttendanceViewModel @Inject constructor(
    private val attendanceRepoManager: AttendanceRepoManager
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
}