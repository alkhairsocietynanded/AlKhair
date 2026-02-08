package com.aewsn.alkhair.ui.student.homework

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aewsn.alkhair.data.manager.HomeworkRepoManager
import com.aewsn.alkhair.data.manager.UserRepoManager
import com.aewsn.alkhair.data.models.Homework
import com.aewsn.alkhair.utils.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StudentHomeworkViewModel @Inject constructor(
    private val homeworkRepoManager: HomeworkRepoManager,
    private val userRepoManager: UserRepoManager
) : ViewModel() {

    private val _homeworkListState = MutableStateFlow<UiState<List<Homework>>>(UiState.Loading)
    val homeworkListState: StateFlow<UiState<List<Homework>>> = _homeworkListState.asStateFlow()

    init {
        fetchStudentHomework()
    }

    private fun fetchStudentHomework() {
        viewModelScope.launch {
            // Combine current user flow with homework observation
            userRepoManager.getCurrentUserFlow()
                .combine(homeworkRepoManager.observeLocal()) { user, homeworkList ->
                    if (user == null) {
                        return@combine UiState.Error("User not found")
                    }

                    // Filter homework for the student's class
                    // Note: If no class is assigned, user.classId might be null or empty
                    if (user.classId.isNullOrEmpty()) {
                         return@combine UiState.Success(emptyList())
                    }

                    val studentHomework = homeworkList.filter { homework ->
                        // Match Class ID
                        val isClassMatch = homework.classId == user.classId
                        
                        // Match Division (Optional: if homework has division, it must match user's division)
                        // If homework division is null/empty, it's for the whole class
                        val isDivisionMatch = homework.divisionName.isEmpty() ||
                                              homework.divisionName == user.divisionName

                        isClassMatch && isDivisionMatch
                    }.sortedByDescending { it.date } // Show latest first

                    UiState.Success(studentHomework)
                }
                .catch { e ->
                    emit(UiState.Error(e.message ?: "Unknown error"))
                }
                .collect { state ->
                    _homeworkListState.value = state
                }
        }
    }
}
