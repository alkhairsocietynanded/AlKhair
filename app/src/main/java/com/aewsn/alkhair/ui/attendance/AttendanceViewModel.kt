package com.aewsn.alkhair.ui.attendance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aewsn.alkhair.data.manager.AttendanceRepoManager
import com.aewsn.alkhair.data.manager.AuthRepoManager
import com.aewsn.alkhair.data.manager.UserRepoManager
import com.aewsn.alkhair.data.models.Attendance
import com.aewsn.alkhair.data.models.User
import com.aewsn.alkhair.utils.Constants
import com.aewsn.alkhair.utils.DateUtils
import com.aewsn.alkhair.utils.Roles
import com.aewsn.alkhair.utils.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class AttendanceUiModel(
    val user: User,
    val status: String? = null,
    val time: String? = null // ✅ Added Time
)

@HiltViewModel
class AttendanceViewModel @Inject constructor(
    private val attendanceRepoManager: AttendanceRepoManager,
    private val userRepoManager: UserRepoManager,
    private val authRepoManager: AuthRepoManager
) : ViewModel() {

    private val _selectedDate = MutableStateFlow(DateUtils.formatDate(Calendar.getInstance()))
    private val _classIdFilter = MutableStateFlow<String?>(null)
    private val _roleFilter = MutableStateFlow(Roles.STUDENT)
    private val _shiftFilter = MutableStateFlow("All")
    private val _editCache = MutableStateFlow<Map<String, String>>(emptyMap())
    private val _saveState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val saveState: StateFlow<UiState<Unit>> = _saveState

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

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<UiState<List<AttendanceUiModel>>> =
        combine(_selectedDate, _classIdFilter, _roleFilter, _shiftFilter, _editCache) { date, classId, role, shift, edits ->
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
                        status = localStatus ?: dbRecord?.status,
                        time = dbRecord?.time // ✅ Map Time
                    )
                }
                UiState.Success(uiList) as UiState<List<AttendanceUiModel>>
            }
        }.onStart { emit(UiState.Loading) }
            .catch { emit(UiState.Error(it.message ?: "Failed to load data")) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState.Idle)


    /* ============================================================
       ✍️ SAVE LOGIC (UPDATED WITH SHIFT)
       ============================================================ */

    fun saveCurrentAttendance() {
        val currentState = uiState.value
        if (currentState !is UiState.Success) return

        val role = _roleFilter.value
        val date = _selectedDate.value
        val shift = _shiftFilter.value // ✅ Get Current Shift
        val list = currentState.data

        // For Teachers: Use STAFF_CLASS_ID when classId is null
        val classId = _classIdFilter.value ?: if (role == Roles.TEACHER) Constants.STAFF_CLASS_ID else null

        if (classId.isNullOrBlank()) {
            _saveState.value = UiState.Error("Class ID is missing.")
            return
        }

        _saveState.value = UiState.Loading
        viewModelScope.launch {
            val attendanceEntities = list
                .filter { !it.status.isNullOrBlank() }
                .map { uiModel ->
                    Attendance(
                        studentId = uiModel.user.uid,
                        classId = classId,
                        date = date,
                        status = uiModel.status!!,
                        shift = shift, // ✅ Add Shift to Model
                        // time = null, // Manual attendance doesn't have specific time usually, or we can add it if needed
                        updatedAt = System.currentTimeMillis()
                    )
                }

            // ✅ Pass Shift to Repo
            attendanceRepoManager.saveAttendance(classId, date, shift, attendanceEntities).fold(
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

    fun markSelfPresent() {
        _saveState.value = UiState.Loading
        viewModelScope.launch {
            val currentUid = authRepoManager.getCurrentUserUid()
            if (currentUid == null) {
                _saveState.value = UiState.Error("User not logged in")
                return@launch
            }

            val currentUser = userRepoManager.getUserById(currentUid)
            val todayDate = DateUtils.formatDate(Calendar.getInstance())

            // ✅ Capture Current Time
            val currentTime = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date())

            // Teacher details fetch karein
            val targetClassId = currentUser?.classId?.takeIf { it.isNotBlank() } ?: Constants.STAFF_CLASS_ID
            val targetShift = currentUser?.shift ?: "General" // ✅ Get Teacher's Shift

            val attendanceRecord = Attendance(
                studentId = currentUid,
                classId = targetClassId,
                date = todayDate,
                status = "Present",
                shift = targetShift, // ✅ Save Teacher's Shift
                time = currentTime, // ✅ Save Time
                updatedAt = System.currentTimeMillis()
            )

            // ✅ Pass Shift to Repo
            attendanceRepoManager.saveAttendance(
                classId = targetClassId,
                date = todayDate,
                shift = targetShift,
                attendanceList = listOf(attendanceRecord)
            ).fold(
                onSuccess = { _saveState.value = UiState.Success(Unit) },
                onFailure = { _saveState.value = UiState.Error(it.message ?: "Failed") }
            )
        }
    }

    fun resetSaveState() { _saveState.value = UiState.Idle }

    private data class Params(val date: String, val classId: String?, val role: String, val shift: String, val edits: Map<String, String>)
}