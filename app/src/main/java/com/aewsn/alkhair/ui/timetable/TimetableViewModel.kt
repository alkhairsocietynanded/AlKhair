package com.aewsn.alkhair.ui.timetable

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aewsn.alkhair.data.manager.ClassDivisionRepoManager
import com.aewsn.alkhair.data.manager.SubjectRepoManager
import com.aewsn.alkhair.data.manager.TimetableRepoManager
import com.aewsn.alkhair.data.manager.UserRepoManager
import com.aewsn.alkhair.data.models.ClassModel
import com.aewsn.alkhair.data.models.Subject
import com.aewsn.alkhair.data.models.Timetable
import com.aewsn.alkhair.data.models.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TimetableViewModel @Inject constructor(
    private val timetableRepoManager: TimetableRepoManager,
    private val classRepoManager: ClassDivisionRepoManager,
    private val subjectRepoManager: SubjectRepoManager,
    private val userRepoManager: UserRepoManager
) : ViewModel() {

    // Metadata for Pickers
    val classes: StateFlow<List<ClassModel>> = classRepoManager.observeClasses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val subjects: StateFlow<List<Subject>> = subjectRepoManager.observeLocal()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Observed Teachers List for Dropdown
    val teachers: StateFlow<List<User>> = userRepoManager.observeUsersByRole("teacher")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    // Combining flows to populate names in Timetable objects
    private val _selectedClassId = MutableStateFlow<String?>(null)
    private val _selectedTeacherId = MutableStateFlow<String?>(null)
    
    val timetableEntries = combine(
        timetableRepoManager.observeLocal(),
        subjectRepoManager.observeLocal(),
        userRepoManager.observeUsersByRole("teacher"),
         _selectedClassId,
         _selectedTeacherId
    ) { timetables, subjectList, userList, selectedClassId, selectedTeacherId ->
        val filtered = when {
            selectedTeacherId != null -> timetables.filter { it.teacherId == selectedTeacherId }
            selectedClassId != null -> timetables.filter { it.classId == selectedClassId }
            else -> timetables 
        }
        
        filtered.map { entry ->
            val subjectName = subjectList.find { it.id == entry.subjectId }?.name ?: "Unknown"
            val teacherName = userList.find { it.uid == entry.teacherId }?.name ?: "Unknown"
            entry.copy(subjectName = subjectName, teacherName = teacherName)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ... uiState ...

    fun selectClass(classId: String) {
        _selectedClassId.value = classId
        _selectedTeacherId.value = null // Reset teacher filter
    }
    
    fun selectTeacher(teacherId: String) {
        _selectedTeacherId.value = teacherId
        _selectedClassId.value = null // Reset class filter
    }

    fun initializeForCurrentUser() {
        viewModelScope.launch {
            val user = userRepoManager.getCurrentUser()
            if (user != null) {
                if (user.role == "teacher") {
                    selectTeacher(user.uid)
                } else if (user.role == "student") {
                    val userClassId = user.classId
                    if (!userClassId.isNullOrEmpty()) {
                        selectClass(userClassId)
                    }
                }
            }
        }
    }

    private val _uiState = MutableStateFlow<TimetableUiState>(TimetableUiState.Idle)
    val uiState: StateFlow<TimetableUiState> = _uiState

    fun addTimetableEntry(entry: Timetable) {
        viewModelScope.launch {
            _uiState.value = TimetableUiState.Loading
            val result = timetableRepoManager.addTimetable(entry)
             if (result.isSuccess) {
                _uiState.value = TimetableUiState.Success("Entry added")
            } else {
                _uiState.value = TimetableUiState.Error(result.exceptionOrNull()?.message ?: "Error")
            }
        }
    }

    fun deleteTimetableEntry(id: String) {
        viewModelScope.launch {
             _uiState.value = TimetableUiState.Loading
            val result = timetableRepoManager.deleteTimetable(id)
             if (result.isSuccess) {
                _uiState.value = TimetableUiState.Success("Entry deleted")
            } else {
                _uiState.value = TimetableUiState.Error(result.exceptionOrNull()?.message ?: "Error")
            }
        }
    }
    
    fun resetUiState() {
        _uiState.value = TimetableUiState.Idle
    }
}

sealed class TimetableUiState {
    object Idle : TimetableUiState()
    object Loading : TimetableUiState()
    data class Success(val message: String) : TimetableUiState()
    data class Error(val message: String) : TimetableUiState()
}
