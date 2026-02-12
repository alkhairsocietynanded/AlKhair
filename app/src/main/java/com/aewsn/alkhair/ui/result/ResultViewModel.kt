package com.aewsn.alkhair.ui.result

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aewsn.alkhair.data.manager.AuthRepoManager
import com.aewsn.alkhair.data.manager.ClassDivisionRepoManager
import com.aewsn.alkhair.data.manager.ResultRepoManager
import com.aewsn.alkhair.data.manager.UserRepoManager
import com.aewsn.alkhair.data.local.dao.SubjectDao
import com.aewsn.alkhair.data.models.ClassModel
import com.aewsn.alkhair.data.models.DivisionModel
import com.aewsn.alkhair.data.models.Exam
import com.aewsn.alkhair.data.models.Result
import com.aewsn.alkhair.data.models.User
import com.aewsn.alkhair.utils.Roles
import com.aewsn.alkhair.utils.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ResultViewModel @Inject constructor(
    private val resultRepoManager: ResultRepoManager,
    private val authRepoManager: AuthRepoManager,
    private val userRepoManager: UserRepoManager,
    private val subjectDao: SubjectDao,
    private val classDivisionRepoManager: ClassDivisionRepoManager
) : ViewModel() {

    /* ============================================================
       📊 UI MODEL
       ============================================================ */

    data class ResultUiModel(
        val result: Result,
        val subjectName: String,
        val studentName: String = ""
    )

    /* ============================================================
       📘 CLASS & DIVISION DROPDOWNS (Admin ke liye)
       ============================================================ */

    val classesState: StateFlow<UiState<List<ClassModel>>> =
        classDivisionRepoManager.observeClasses()
            .map<List<ClassModel>, UiState<List<ClassModel>>> { UiState.Success(it) }
            .onStart { emit(UiState.Loading) }
            .catch { emit(UiState.Error(it.message ?: "Failed to load classes")) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState.Idle)

    val divisionsState: StateFlow<UiState<List<DivisionModel>>> =
        classDivisionRepoManager.observeDivisions()
            .map<List<DivisionModel>, UiState<List<DivisionModel>>> { UiState.Success(it) }
            .onStart { emit(UiState.Loading) }
            .catch { emit(UiState.Error(it.message ?: "Failed to load divisions")) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState.Idle)

    /* ============================================================
       📦 EXAMS LIST — Role-Based
       ============================================================ */

    private val _exams = MutableStateFlow<UiState<List<Exam>>>(UiState.Loading)
    val exams: StateFlow<UiState<List<Exam>>> = _exams

    // Cached user for role checks
    private var cachedUser: User? = null

    init {
        fetchExams()
    }

    fun fetchExams() {
        viewModelScope.launch {
            _exams.value = UiState.Loading

            val currentUser = userRepoManager.getCurrentUser()
            if (currentUser == null) {
                _exams.value = UiState.Error("User not found")
                return@launch
            }
            cachedUser = currentUser
            val role = currentUser.role.trim()

            val examsFlow = when {
                role.equals(Roles.STUDENT, ignoreCase = true) -> {
                    // Student: sirf published exams apni class ke
                    val classId = currentUser.classId
                    if (classId.isNullOrBlank()) {
                        _exams.value = UiState.Error("Class not assigned")
                        return@launch
                    }
                    resultRepoManager.observePublishedExamsByClassId(classId)
                }
                role.equals(Roles.TEACHER, ignoreCase = true) -> {
                    // Teacher: apni class ke sab exams
                    val classId = currentUser.classId
                    if (classId.isNullOrBlank()) {
                        _exams.value = UiState.Error("Class not assigned")
                        return@launch
                    }
                    resultRepoManager.observeExamsByClassId(classId)
                }
                else -> {
                    // Admin: sab exams
                    resultRepoManager.observeAllExams()
                }
            }

            examsFlow
                .catch { e -> _exams.value = UiState.Error(e.message ?: "Unknown error") }
                .collect { list -> _exams.value = UiState.Success(list) }
        }
    }

    /* ============================================================
       📋 RESULTS FOR EXAM — Role-Based
       ============================================================ */

    private val _results = MutableStateFlow<UiState<List<ResultUiModel>>>(UiState.Loading)
    val results: StateFlow<UiState<List<ResultUiModel>>> = _results

    fun fetchResultsForExam(examId: String) {
        viewModelScope.launch {
            _results.value = UiState.Loading

            val currentUser = cachedUser ?: userRepoManager.getCurrentUser()
            if (currentUser == null) {
                _results.value = UiState.Error("User not found")
                return@launch
            }
            cachedUser = currentUser

            val role = currentUser.role.trim()
            val isStudent = role.equals(Roles.STUDENT, ignoreCase = true)

            val resultsFlow = if (isStudent) {
                resultRepoManager.observeResultsForStudent(examId, currentUser.uid)
            } else {
                resultRepoManager.observeResultsForExam(examId)
            }

            val subjectsFlow = subjectDao.getAllSubjects()
            val usersFlow = if (!isStudent) userRepoManager.observeLocal() else flowOf(emptyList())

            combine(resultsFlow, subjectsFlow, usersFlow) { results, subjects, users ->
                val subjectMap = subjects.associateBy { it.id }
                val userMap = users.associateBy { it.uid }

                results.map { result ->
                    val studentName = if (isStudent) "" else {
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
                .collect { list -> _results.value = UiState.Success(list) }
        }
    }

    /* ============================================================
       ✍️ EXAM MUTATIONS (Create / Update / Delete)
       ============================================================ */

    private val _examMutationState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val examMutationState: StateFlow<UiState<Unit>> = _examMutationState

    fun createOrUpdateExam(
        isEditMode: Boolean,
        existingExam: Exam?,
        title: String,
        startDate: Long,
        endDate: Long,
        session: String?,
        classId: String?,
        isPublished: Boolean
    ) {
        _examMutationState.value = UiState.Loading

        val now = System.currentTimeMillis()
        val exam = Exam(
            id = existingExam?.id ?: "",
            title = title,
            startDate = startDate,
            endDate = endDate,
            session = session,
            classId = classId,
            isPublished = isPublished,
            updatedAt = now
        )

        viewModelScope.launch {
            val result = if (isEditMode) {
                resultRepoManager.updateExam(exam)
            } else {
                resultRepoManager.createExam(exam).map { }
            }

            _examMutationState.value = result.fold(
                onSuccess = { UiState.Success(Unit) },
                onFailure = { UiState.Error(it.message ?: "Operation Failed") }
            )
        }
    }

    fun deleteExam(examId: String) {
        _examMutationState.value = UiState.Loading
        viewModelScope.launch {
            _examMutationState.value = resultRepoManager.deleteExam(examId).fold(
                onSuccess = { UiState.Success(Unit) },
                onFailure = { UiState.Error(it.message ?: "Delete failed") }
            )
        }
    }

    fun resetExamMutationState() {
        _examMutationState.value = UiState.Idle
    }

    /* ============================================================
       ✍️ RESULT MUTATIONS (Batch Save)
       ============================================================ */

    private val _resultMutationState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val resultMutationState: StateFlow<UiState<Unit>> = _resultMutationState

    fun saveResults(examId: String, subjectId: String, totalMarks: Int, entries: List<StudentMarkEntry>) {
        _resultMutationState.value = UiState.Loading

        val now = System.currentTimeMillis()
        val results = entries.filter { it.marksObtained >= 0 }.map { entry ->
            Result(
                id = entry.existingResultId.ifEmpty { UUID.randomUUID().toString() },
                examId = examId,
                studentId = entry.studentId,
                subjectId = subjectId,
                marksObtained = entry.marksObtained.toDouble(),
                totalMarks = totalMarks.toDouble(),
                grade = calculateGrade(entry.marksObtained, totalMarks),
                remarks = entry.remarks,
                updatedAt = now,
                isSynced = false
            )
        }

        viewModelScope.launch {
            _resultMutationState.value = resultRepoManager.saveResults(results).fold(
                onSuccess = { UiState.Success(Unit) },
                onFailure = { UiState.Error(it.message ?: "Failed to save results") }
            )
        }
    }

    fun resetResultMutationState() {
        _resultMutationState.value = UiState.Idle
    }

    /* ============================================================
       🧮 GRADE CALCULATION
       ============================================================ */

    private fun calculateGrade(obtained: Int, total: Int): String {
        if (total <= 0) return "N/A"
        val percentage = (obtained.toFloat() / total * 100).toInt()
        return when {
            percentage >= 90 -> "A+"
            percentage >= 80 -> "A"
            percentage >= 70 -> "B+"
            percentage >= 60 -> "B"
            percentage >= 50 -> "C"
            percentage >= 40 -> "D"
            else -> "F"
        }
    }

    /* ============================================================
       👤 HELPER: Get Users for Result Entry
       ============================================================ */

    fun getCurrentUser(): User? = cachedUser

    suspend fun fetchCurrentUser(): User? {
        val user = userRepoManager.getCurrentUser()
        cachedUser = user
        return user
    }

    /* ============================================================
       📊 DATA CLASS for Result Entry
       ============================================================ */

    data class StudentMarkEntry(
        val studentId: String,
        val studentName: String,
        val marksObtained: Int = -1, // -1 = not entered
        val remarks: String = "",
        val existingResultId: String = ""
    )
}
