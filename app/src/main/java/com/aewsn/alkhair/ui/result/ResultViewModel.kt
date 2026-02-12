package com.aewsn.alkhair.ui.result

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aewsn.alkhair.data.manager.AuthRepoManager
import com.aewsn.alkhair.data.manager.ResultRepoManager
import com.aewsn.alkhair.data.models.Exam
import com.aewsn.alkhair.data.models.Result
import com.aewsn.alkhair.utils.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ResultViewModel @Inject constructor(
    private val resultRepoManager: ResultRepoManager,
    private val authRepoManager: AuthRepoManager,
    private val userRepoManager: com.aewsn.alkhair.data.manager.UserRepoManager, // Injected
    private val subjectDao: com.aewsn.alkhair.data.local.dao.SubjectDao
) : ViewModel() {

    private val _exams = MutableStateFlow<UiState<List<Exam>>>(UiState.Loading)
    val exams: StateFlow<UiState<List<Exam>>> = _exams

    data class ResultUiModel(
        val result: Result,
        val subjectName: String,
        val studentName: String = "" // Added
    )

    private val _results = MutableStateFlow<UiState<List<ResultUiModel>>>(UiState.Loading)
    val results: StateFlow<UiState<List<ResultUiModel>>> = _results


    init {
        fetchExams()
    }

    fun fetchExams() {
        viewModelScope.launch {
            _exams.value = UiState.Loading
            resultRepoManager.observeAllExams()
                .catch { e -> _exams.value = UiState.Error(e.message ?: "Unknown error") }
                .collect { list ->
                    _exams.value = UiState.Success(list)
                }
        }
    }

    fun fetchResultsForExam(examId: String) {
        viewModelScope.launch {
            _results.value = UiState.Loading
            
            // 1. Get Current User & Role
            val currentUser = userRepoManager.getCurrentUser()
            if (currentUser == null) {
                _results.value = UiState.Error("User not found")
                return@launch
            }
            
            val role = currentUser.role.trim()
            val isStudent = role.equals(com.aewsn.alkhair.utils.Roles.STUDENT, ignoreCase = true)

            // 2. Select Source based on Role
            val resultsFlow = if (isStudent) {
                resultRepoManager.observeResultsForStudent(examId, currentUser.uid)
            } else {
                resultRepoManager.observeResultsForExam(examId)
            }
            
            // 3. Prepare Metadata Flows
            val subjectsFlow = subjectDao.getAllSubjects()
            // We need users to map names if Teacher/Admin
            val usersFlow = if (!isStudent) userRepoManager.observeLocal() else kotlinx.coroutines.flow.flowOf(emptyList())

            // 4. Combine
            combine(resultsFlow, subjectsFlow, usersFlow) { results, subjects, users ->
                val subjectMap = subjects.associateBy { it.id }
                val userMap = users.associateBy { it.uid } // Map by UID
                
                results.map { result ->
                    val studentName = if (isStudent) {
                        "" // Don't show name for own results
                    } else {
                        userMap[result.studentId]?.name ?: "Unknown Student"
                    }
                    
                    ResultUiModel(
                        result = result,
                        subjectName = subjectMap[result.subjectId]?.name ?: "Unknown Subject",
                        studentName = studentName
                    )
                }.sortedWith(compareBy({ it.studentName }, { it.subjectName }))
            }
            .catch { e -> _results.value = UiState.Error(e.message ?: "Unknown error") }
            .collect { list ->
                _results.value = UiState.Success(list)
            }
        }
    }
}
