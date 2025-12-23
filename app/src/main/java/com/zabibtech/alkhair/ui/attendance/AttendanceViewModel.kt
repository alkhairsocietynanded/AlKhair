package com.zabibtech.alkhair.ui.attendance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zabibtech.alkhair.data.manager.AttendanceRepoManager
import com.zabibtech.alkhair.data.manager.UserRepoManager
import com.zabibtech.alkhair.data.models.Attendance
import com.zabibtech.alkhair.data.models.User
import com.zabibtech.alkhair.utils.DateUtils
import com.zabibtech.alkhair.utils.Roles
import com.zabibtech.alkhair.utils.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class AttendanceUiModel(
    val user: User,
    val status: String? = null
)

@HiltViewModel
class AttendanceViewModel @Inject constructor(
    private val attendanceRepoManager: AttendanceRepoManager,
    private val userRepoManager: UserRepoManager
) : ViewModel() {

    private val _selectedDate = MutableStateFlow<String>(DateUtils.formatDate(Calendar.getInstance()))
    private val _classIdFilter = MutableStateFlow<String?>(null)
    private val _roleFilter = MutableStateFlow<String>(Roles.STUDENT)
    private val _shiftFilter = MutableStateFlow<String>("All")
    private val _editCache = MutableStateFlow<Map<String, String>>(emptyMap())

    /* ============================================================
       🔹 ACTIONS
       ============================================================ */

    fun setFilters(classId: String?, role: String) {
        _classIdFilter.value = classId
        _roleFilter.value = role
    }

    fun setDate(calendar: Calendar) {
        val newDate = DateUtils.formatDate(calendar)
        if (_selectedDate.value != newDate) {
            _selectedDate.value = newDate
            _editCache.value = emptyMap()
        }
    }

    fun setShift(shift: String) {
        _shiftFilter.value = shift
    }

    fun markAttendance(studentId: String, status: String) {
        val current = _editCache.value.toMutableMap()
        if (status.isBlank()) current.remove(studentId)
        else current[studentId] = status
        _editCache.value = current
    }

    /* ============================================================
       📦 REACTIVE UI STATE (FIXED)
       ============================================================ */

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<UiState<List<AttendanceUiModel>>> =
        combine(
            _selectedDate,
            _classIdFilter,
            _roleFilter,
            _shiftFilter,
            _editCache
        ) { date, classId, role, shift, edits ->
            Params(date, classId, role, shift, edits)
        }.flatMapLatest { params ->
            val usersFlow = userRepoManager.observeUsersByRole(params.role)
            val attendanceFlow = attendanceRepoManager.observeAttendanceByDate(params.date)

            combine(usersFlow, attendanceFlow) { users, attendanceRecords ->
                val filteredUsers = users.filter { user ->
                    val matchClass = params.classId == null || user.classId == params.classId
                    val matchShift = params.shift == "All" || user.shift.equals(params.shift, ignoreCase = true)
                    matchClass && matchShift
                }

                val uiList = filteredUsers.map { user ->
                    val dbRecord = attendanceRecords.find { it.studentId == user.uid }
                    val localStatus = params.edits[user.uid]

                    AttendanceUiModel(
                        user = user,
                        status = localStatus ?: dbRecord?.status
                    )
                }

                // 🔧 FIX: Explicitly cast to the Parent Sealed Class (UiState)
                // This tells the compiler "Hey, this flow can return Success OR Loading OR Error"
                UiState.Success(uiList) as UiState<List<AttendanceUiModel>>
            }
        }
            .onStart { emit(UiState.Loading) } // No more error here
            .catch { emit(UiState.Error(it.message ?: "Failed to load data")) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                UiState.Idle
            )

    /* ============================================================
       ✍️ SAVE LOGIC
       ============================================================ */

    private val _saveState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val saveState: StateFlow<UiState<Unit>> = _saveState

    fun saveCurrentAttendance() {
        val currentState = uiState.value
        if (currentState !is UiState.Success) return

        val classId = _classIdFilter.value
        val date = _selectedDate.value
        val list = currentState.data

        if (classId.isNullOrBlank()) {
            _saveState.value = UiState.Error("Class ID is missing.")
            return
        }

        _saveState.value = UiState.Loading
        viewModelScope.launch {
            val attendanceEntities = list
                .filter { !it.status.isNullOrBlank() }
                .map {
                    Attendance(
                        studentId = it.user.uid,
                        classId = classId,
                        date = date,
                        status = it.status!!
                    )
                }

            attendanceRepoManager.saveAttendance(classId, date, attendanceEntities).fold(
                onSuccess = {
                    _saveState.value = UiState.Success(Unit)
                    _editCache.value = emptyMap()
                },
                onFailure = {
                    _saveState.value = UiState.Error(it.message ?: "Failed to save")
                }
            )
        }
    }

    fun resetSaveState() {
        _saveState.value = UiState.Idle
    }

    private data class Params(
        val date: String,
        val classId: String?,
        val role: String,
        val shift: String,
        val edits: Map<String, String>
    )
}