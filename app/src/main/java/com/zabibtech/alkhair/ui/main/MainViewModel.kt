package com.zabibtech.alkhair.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zabibtech.alkhair.data.datastore.ClassDivisionStore
import com.zabibtech.alkhair.data.datastore.UserStore
import com.zabibtech.alkhair.data.models.ClassModel
import com.zabibtech.alkhair.data.models.DivisionModel
import com.zabibtech.alkhair.data.models.User
import com.zabibtech.alkhair.data.repository.AuthRepository
import com.zabibtech.alkhair.data.repository.AttendanceRepository
import com.zabibtech.alkhair.data.repository.UserRepository
import com.zabibtech.alkhair.utils.Roles
import com.zabibtech.alkhair.utils.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
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
    private val userRepo: UserRepository,
    private val attendanceRepo: AttendanceRepository,
    private val sessionManager: UserStore,
    private val classDivisionStore: ClassDivisionStore
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
                val localUser = sessionManager.getUser()
                if (localUser != null) {
                    _state.value = UiState.Success(localUser)
                } else {
                    _state.value = UiState.Error(e.localizedMessage ?: "Failed to fetch user")
                }
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
                val users = userRepo.getAllUsers()
                val classList = classDivisionStore.getOrFetchClassList()
                
                // Keep the classes list updated for any other observers
                _classes.value = classList

                // Fetch attendance data for the current date
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val currentDate = sdf.format(Date())
                val attendanceData = attendanceRepo.getAttendanceForDate(currentDate)

                var present = 0
                var absent = 0
                attendanceData.values.forEach { classAttendance ->
                    classAttendance.values.forEach { status ->
                        if (status.equals("Present", ignoreCase = true)) {
                            present++
                        } else {
                            absent++
                        }
                    }
                }

                val totalAttendance = present + absent
                val attendancePercentage = if (totalAttendance > 0) (present * 100) / totalAttendance else 0

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
                _dashboardState.value = UiState.Error(e.localizedMessage ?: "Failed to load dashboard stats")
            }
        }
    }


    // ================================
    // Divisions only
    // ================================
    fun loadDivisions() {
        viewModelScope.launch {
            val list = classDivisionStore.getOrFetchDivisionList()
            _divisions.value = list
        }
    }

    // ================================
    // Classes only
    // ================================
    fun loadClasses() {
        viewModelScope.launch {
            val list = classDivisionStore.getOrFetchClassList()
            _classes.value = list
        }
    }

    // ================================
    // Combined: load both divisions + classes
    // ================================
    fun loadDivisionsAndClasses() {
        viewModelScope.launch {
            val divisionsList = classDivisionStore.getOrFetchDivisionList()
            val classesList = classDivisionStore.getOrFetchClassList()

            _divisions.value = divisionsList
            _classes.value = classesList
        }
    }
}
