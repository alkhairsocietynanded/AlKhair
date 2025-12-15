package com.zabibtech.alkhair.ui.main

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zabibtech.alkhair.data.manager.AppDataSyncManager
import com.zabibtech.alkhair.data.manager.AttendanceRepoManager
import com.zabibtech.alkhair.data.manager.AuthRepoManager
import com.zabibtech.alkhair.data.manager.ClassDivisionRepoManager
import com.zabibtech.alkhair.data.manager.UserRepoManager
import com.zabibtech.alkhair.data.models.ClassModel
import com.zabibtech.alkhair.data.models.DivisionModel
import com.zabibtech.alkhair.data.models.User
import com.zabibtech.alkhair.utils.Roles
import com.zabibtech.alkhair.utils.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    private val appDataSyncManager: AppDataSyncManager,
    private val authRepoManager: AuthRepoManager,
    private val userRepoManager: UserRepoManager,
    private val attendanceRepoManager: AttendanceRepoManager,
    private val classDivisionRepoManager: ClassDivisionRepoManager
) : ViewModel() {

    private val _userSessionState = MutableStateFlow<UiState<User?>>(UiState.Idle)
    val userSessionState: StateFlow<UiState<User?>> = _userSessionState

    // Single StateFlow for all dashboard stats
    private val _dashboardState = MutableStateFlow<UiState<DashboardStats>>(UiState.Idle)
    val dashboardState: StateFlow<UiState<DashboardStats>> = _dashboardState

    private val _divisions = MutableStateFlow<List<DivisionModel>>(emptyList())
    val divisions: StateFlow<List<DivisionModel>> = _divisions

    private val _classes = MutableStateFlow<List<ClassModel>>(emptyList())
    val classes: StateFlow<List<ClassModel>> = _classes

    init {
        // Load initial data
        loadClassesAndDivisions()
    }

    suspend fun syncData(forceRefresh: Boolean = false) {
        appDataSyncManager.syncAllData(forceRefresh)
    }

    fun loadClassesAndDivisions() {
        viewModelScope.launch {
            classDivisionRepoManager.getAllClasses().fold(
                onSuccess = { classList -> _classes.value = classList },
                onFailure = { e -> Log.e("MainViewModel", "Error loading classes", e) }
            )
        }
        viewModelScope.launch {
            classDivisionRepoManager.getAllDivisions().fold(
                onSuccess = { divisionList -> _divisions.value = divisionList },
                onFailure = { e -> Log.e("MainViewModel", "Error loading divisions", e) }
            )
        }
    }

    // ================================
    // User session management - Refactored
    // ================================
    fun checkUser() {
        _userSessionState.value = UiState.Loading
        viewModelScope.launch {
            userRepoManager.getCurrentUser().fold(
                onSuccess = { user ->
                    // user will be null if not logged in, which is the desired outcome
                    _userSessionState.value = UiState.Success(user)
                },
                onFailure = { e ->
                    Log.e("MainViewModel", "Failed to fetch current user", e)
                    // If fetching fails, we can log the user out as a safety measure
                    authRepoManager.logout()
                    _userSessionState.value = UiState.Success(null)
                }
            )
        }
    }

    // ================================
    // Dashboard Statistics - Refactored
    // ================================
    fun loadDashboardStats() {
        _dashboardState.value = UiState.Loading
        viewModelScope.launch {
            // Fetch all required data using the repo managers to avoid race conditions
            val usersResult = userRepoManager.getAllUsers()
            val classesResult = classDivisionRepoManager.getAllClasses()
            val attendanceResult = attendanceRepoManager.getAttendanceForDate(
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            )

            // Process results using .fold() for clean error handling
            usersResult.fold(
                onSuccess = { users ->
                    classesResult.fold(
                        onSuccess = { classList ->
                            attendanceResult.fold(
                                onSuccess = { attendanceList ->
                                    val present = attendanceList.count {
                                        it.status.equals(
                                            "Present",
                                            ignoreCase = true
                                        )
                                    }
                                    val absent = attendanceList.count {
                                        !it.status.equals(
                                            "Present",
                                            ignoreCase = true
                                        )
                                    }
                                    val totalAttendance = present + absent
                                    val attendancePercentage =
                                        if (totalAttendance > 0) (present * 100) / totalAttendance else 0

                                    val stats = DashboardStats(
                                        studentsCount = users.count { it.role == Roles.STUDENT },
                                        teachersCount = users.count { it.role == Roles.TEACHER },
                                        classesCount = classList.size,
                                        presentCount = present,
                                        absentCount = absent,
                                        attendancePercentage = attendancePercentage
                                    )
                                    _dashboardState.value = UiState.Success(stats)
                                },
                                onFailure = { e ->
                                    _dashboardState.value = UiState.Error(
                                        e.localizedMessage ?: "Failed to load attendance stats"
                                    )
                                }
                            )
                        },
                        onFailure = { e ->
                            _dashboardState.value =
                                UiState.Error(e.localizedMessage ?: "Failed to load class stats")
                        }
                    )
                },
                onFailure = { e ->
                    _dashboardState.value =
                        UiState.Error(e.localizedMessage ?: "Failed to load user stats")
                }
            )
        }
    }
}
