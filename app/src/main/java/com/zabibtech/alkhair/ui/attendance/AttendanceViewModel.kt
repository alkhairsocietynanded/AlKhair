package com.zabibtech.alkhair.ui.attendance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zabibtech.alkhair.data.manager.AttendanceRepoManager
import com.zabibtech.alkhair.data.manager.AuthRepoManager
import com.zabibtech.alkhair.data.manager.UserRepoManager
import com.zabibtech.alkhair.data.models.Attendance
import com.zabibtech.alkhair.data.models.User
import com.zabibtech.alkhair.utils.Constants
import com.zabibtech.alkhair.utils.DateUtils
import com.zabibtech.alkhair.utils.Roles
import com.zabibtech.alkhair.utils.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

// UI Model: User Details + Attendance Status
data class AttendanceUiModel(
    val user: User,
    val status: String? = null
)

@HiltViewModel
class AttendanceViewModel @Inject constructor(
    private val attendanceRepoManager: AttendanceRepoManager,
    private val userRepoManager: UserRepoManager,
    private val authRepoManager: AuthRepoManager
) : ViewModel() {

    // --- State Holders ---
    private val _selectedDate = MutableStateFlow(DateUtils.formatDate(Calendar.getInstance()))
    private val _classIdFilter = MutableStateFlow<String?>(null)
    private val _roleFilter = MutableStateFlow(Roles.STUDENT)
    private val _shiftFilter = MutableStateFlow("All")

    // Local Cache for unsaved edits (UI updates immediately, DB updates on Save)
    private val _editCache = MutableStateFlow<Map<String, String>>(emptyMap())

    // Save Operation State
    private val _saveState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val saveState: StateFlow<UiState<Unit>> = _saveState

    /* ============================================================
       🔹 ACTIONS (UI Events)
       ============================================================ */

    fun setFilters(classId: String?, role: String) {
        _classIdFilter.value = classId
        _roleFilter.value = role
    }

    fun setDate(calendar: Calendar) {
        val newDate = DateUtils.formatDate(calendar)
        if (_selectedDate.value != newDate) {
            _selectedDate.value = newDate
            // Date change hone par local edits clear karein
            _editCache.value = emptyMap()
        }
    }

    fun setShift(shift: String) {
        _shiftFilter.value = shift
    }

    fun markAttendance(studentId: String, status: String) {
        val current = _editCache.value.toMutableMap()
        if (status.isBlank()) {
            current.remove(studentId) // Uncheck (Revert to original state)
        } else {
            current[studentId] = status
        }
        _editCache.value = current
    }

    /* ============================================================
       📦 REACTIVE UI PIPELINE (SSOT Pattern)
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
            // 1. Observe Users from Local DB
            val usersFlow = userRepoManager.observeUsersByRole(params.role)

            // 2. Observe Attendance for specific date from Local DB
            val attendanceFlow = attendanceRepoManager.observeAttendanceByDate(params.date)

            combine(usersFlow, attendanceFlow) { users, attendanceRecords ->
                // 3. Filter Users in Memory (Class & Shift)
                val filteredUsers = users.filter { user ->
                    val matchClass = params.classId == null || user.classId == params.classId
                    // Note: Ensure 'Shift' spelling matches DB (e.g. "Subah" vs "subah")
                    val matchShift =
                        params.shift == "All" || user.shift.equals(params.shift, ignoreCase = true)
                    matchClass && matchShift
                }

                // 4. Merge Data (User + DB Status + Local Edits)
                val uiList = filteredUsers.map { user ->
                    // Priority: Local Edit > DB Record > Null
                    val dbRecord = attendanceRecords.find { it.studentId == user.uid }
                    val localStatus = params.edits[user.uid]

                    AttendanceUiModel(
                        user = user,
                        status = localStatus ?: dbRecord?.status
                    )
                }

                // Explicit Cast for Type Safety
                UiState.Success(uiList) as UiState<List<AttendanceUiModel>>
            }
        }
            .onStart { emit(UiState.Loading) }
            .catch { emit(UiState.Error(it.message ?: "Failed to load data")) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                UiState.Idle
            )

    /* ============================================================
       ✍️ SAVE LOGIC
       ============================================================ */

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
            // Convert UI Models to Entity Models
            val attendanceEntities = list
                .filter { !it.status.isNullOrBlank() } // Only save marked records
                .map { uiModel ->
                    Attendance(
                        studentId = uiModel.user.uid,
                        classId = classId,
                        date = date,
                        status = uiModel.status!!
                        // updatedAt will be set by RepoManager
                    )
                }

            // Pass the List directly to RepoManager
            attendanceRepoManager.saveAttendance(classId, date, attendanceEntities).fold(
                onSuccess = {
                    _saveState.value = UiState.Success(Unit)
                    _editCache.value = emptyMap() // Clear local edits after successful save
                },
                onFailure = {
                    _saveState.value = UiState.Error(it.message ?: "Failed to save")
                }
            )
        }
    }

    /* ============================================================
      ✅ TEACHER QR ATTENDANCE FUNCTION
      ============================================================ */
    fun markSelfPresent() {
        _saveState.value = UiState.Loading

        viewModelScope.launch {
            val currentUid = authRepoManager.getCurrentUserUid()
            if (currentUid == null) {
                _saveState.value = UiState.Error("User not logged in")
                return@launch
            }
            val todayDate = DateUtils.formatDate(Calendar.getInstance())

            val currentUser = userRepoManager.getUserById(currentUid)
            val targetClassId =
                if (!currentUser?.classId.isNullOrBlank()) currentUser.classId else Constants.STAFF_CLASS_ID

            // Teacher ki attendance ka object
            // Note: studentId field me Teacher ka UID jayega
            val attendanceRecord = Attendance(
                studentId = currentUid,
                classId = targetClassId,
                date = todayDate,
                status = "Present",
                updatedAt = System.currentTimeMillis()
            )

            // Repo expects a List, wrap it
            attendanceRepoManager.saveAttendance(
                classId = targetClassId,
                date = todayDate,
                attendanceList = listOf(attendanceRecord)
            ).fold(
                onSuccess = {
                    _saveState.value = UiState.Success(Unit)
                },
                onFailure = {
                    _saveState.value = UiState.Error(it.message ?: "Failed to mark attendance")
                }
            )
        }
    }

    fun resetSaveState() {
        _saveState.value = UiState.Idle
    }

    // Helper Data Class for Combine
    private data class Params(
        val date: String,
        val classId: String?,
        val role: String,
        val shift: String,
        val edits: Map<String, String>
    )
}