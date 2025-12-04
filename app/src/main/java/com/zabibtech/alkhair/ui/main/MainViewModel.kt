package com.zabibtech.alkhair.ui.main

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zabibtech.alkhair.data.datastore.UserStore
import com.zabibtech.alkhair.data.manager.AttendanceRepoManager
import com.zabibtech.alkhair.data.manager.ClassDivisionRepoManager
import com.zabibtech.alkhair.data.models.ClassModel
import com.zabibtech.alkhair.data.models.DivisionModel
import com.zabibtech.alkhair.data.models.User
import com.zabibtech.alkhair.data.repository.AuthRepository
import com.zabibtech.alkhair.data.repository.UserRepository
import com.zabibtech.alkhair.utils.Roles
import com.zabibtech.alkhair.utils.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

// Data class to hold all dashboard statistics together
data class DashboardStats(
    val studentsCount: Int = 0,
    val teachersCount: Int = 0,
    val classesCount: Int = 0,
    val attendancePercentage: Int = 0,
    val presentCount: Int = 0,
    val absentCount: Int = 0
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val authRepo: AuthRepository,
    private val userRepo: UserRepository, // Note: This is still an old repository
    private val attendanceRepoManager: AttendanceRepoManager,
    private val classDivisionRepoManager: ClassDivisionRepoManager,
    private val sessionManager: UserStore
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<User?>>(UiState.Idle)
    val state: StateFlow<UiState<User?>> = _state

    // Single StateFlow for all dashboard stats
    private val _dashboardState = MutableStateFlow<UiState<DashboardStats>>(UiState.Idle)
    val dashboardState: StateFlow<UiState<DashboardStats>> = _dashboardState

    private val _divisions = MutableStateFlow<List<DivisionModel>>(emptyList())
    val divisions: StateFlow<List<DivisionModel>> = _divisions

    private val _classes = MutableStateFlow<List<ClassModel>>(emptyList())
    val classes: StateFlow<List<ClassModel>> = _classes

    init {
        // Reactively observe classes and divisions from the local database
        viewModelScope.launch {
            classDivisionRepoManager.getAllClasses().catch { /* Handle error if needed */ }
                .collect { _classes.value = it }
        }
        viewModelScope.launch {
            classDivisionRepoManager.getAllDivisions().catch { /* Handle error if needed */ }
                .collect { _divisions.value = it }
        }
    }

    // ================================
    // User session management
    // ================================
    fun checkUser() {
        _state.value = UiState.Loading
        viewModelScope.launch {
            try {
                val uid = authRepo.currentUserUid()
                if (uid == null) {
                    _state.value = UiState.Success(null)
                } else {
                    val user = userRepo.getUserById(uid)
                    if (user != null) {
                        sessionManager.saveUser(user)
                    }
                    _state.value = UiState.Success(user)
                }
            } catch (e: Exception) {
                Log.d("MainViewModel.checkUser", "checkUser: ${e.message}")
                val localUser = sessionManager.getUser()
                _state.value = UiState.Success(localUser) // On error, fallback to local user
            }
        }
    }

    // ================================
    // Dashboard Statistics
    // ================================
    fun loadDashboardStats() {
        _dashboardState.value = UiState.Loading
        viewModelScope.launch {
            try {
                // Fetch all data required for the dashboard
                val users = userRepo.getAllUsers() // This still uses the old repository
                val classList = _classes.value // Use the already observed value

                // Fetch attendance data for the current date using the new manager
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val currentDate = sdf.format(Date())

                // Use the new "smart get" function which returns a Result
                val attendanceResult = attendanceRepoManager.getAttendanceForDate(currentDate)
                val attendanceList = attendanceResult.getOrThrow() // Let the outer try-catch handle failure

                val present =
                    attendanceList.count { it.status.equals("Present", ignoreCase = true) }
                val absent =
                    attendanceList.count { !it.status.equals("Present", ignoreCase = true) }

                val totalAttendance = present + absent
                val attendancePercentage =
                    if (totalAttendance > 0) (present * 100) / totalAttendance else 0

                // Create a single stats object
                val stats = DashboardStats(
                    studentsCount = users.count { it.role == Roles.STUDENT },
                    teachersCount = users.count { it.role == Roles.TEACHER },
                    classesCount = classList.size,
                    presentCount = present,
                    absentCount = absent,
                    attendancePercentage = attendancePercentage
                )

                // Emit the success state with the combined data
                _dashboardState.value = UiState.Success(stats)

            } catch (e: Exception) {
                // Emit the error state
                _dashboardState.value =
                    UiState.Error(e.localizedMessage ?: "Failed to load dashboard stats")
            }
        }
    }
}