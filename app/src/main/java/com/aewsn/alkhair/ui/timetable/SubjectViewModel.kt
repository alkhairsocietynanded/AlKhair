package com.aewsn.alkhair.ui.timetable

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aewsn.alkhair.data.manager.SubjectRepoManager
import com.aewsn.alkhair.data.models.Subject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SubjectViewModel @Inject constructor(
    private val subjectRepoManager: SubjectRepoManager
) : ViewModel() {

    val subjects: StateFlow<List<Subject>> = subjectRepoManager.observeLocal()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow<SubjectUiState>(SubjectUiState.Idle)
    val uiState: StateFlow<SubjectUiState> = _uiState.asStateFlow()

    fun addSubject(name: String, code: String) {
        viewModelScope.launch {
            _uiState.value = SubjectUiState.Loading
            val result = subjectRepoManager.addSubject(Subject(name = name, code = code))
            if (result.isSuccess) {
                _uiState.value = SubjectUiState.Success("Subject added successfully")
            } else {
                _uiState.value = SubjectUiState.Error(result.exceptionOrNull()?.message ?: "Unknown error")
            }
        }
    }

    fun updateSubject(subject: Subject) {
        viewModelScope.launch {
            _uiState.value = SubjectUiState.Loading
            val result = subjectRepoManager.updateSubject(subject)
            if (result.isSuccess) {
                _uiState.value = SubjectUiState.Success("Subject updated successfully")
            } else {
                _uiState.value = SubjectUiState.Error(result.exceptionOrNull()?.message ?: "Unknown error")
            }
        }
    }

    fun deleteSubject(id: String) {
        viewModelScope.launch {
            _uiState.value = SubjectUiState.Loading
            val result = subjectRepoManager.deleteSubject(id)
            if (result.isSuccess) {
                _uiState.value = SubjectUiState.Success("Subject deleted successfully")
            } else {
                _uiState.value = SubjectUiState.Error(result.exceptionOrNull()?.message ?: "Unknown error")
            }
        }
    }
    
    fun resetUiState() {
        _uiState.value = SubjectUiState.Idle
    }
}

sealed class SubjectUiState {
    object Idle : SubjectUiState()
    object Loading : SubjectUiState()
    data class Success(val message: String) : SubjectUiState()
    data class Error(val message: String) : SubjectUiState()
}
