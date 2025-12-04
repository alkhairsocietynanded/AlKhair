package com.zabibtech.alkhair.ui.classmanager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zabibtech.alkhair.data.manager.ClassDivisionRepoManager
import com.zabibtech.alkhair.data.models.ClassModel
import com.zabibtech.alkhair.data.models.DivisionModel
import com.zabibtech.alkhair.utils.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ClassManagerViewModel @Inject constructor(
    private val repoManager: ClassDivisionRepoManager
) : ViewModel() {

    // State for the list of divisions
    private val _divisions = MutableStateFlow<UiState<List<DivisionModel>>>(UiState.Loading)
    val divisions: StateFlow<UiState<List<DivisionModel>>> = _divisions

    // State for the list of classes
    private val _classes = MutableStateFlow<UiState<List<ClassModel>>>(UiState.Loading)
    val classes: StateFlow<UiState<List<ClassModel>>> = _classes

    // State for individual operations like add, update, delete
    private val _operationState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val operationState: StateFlow<UiState<Unit>> = _operationState

    init {
        // Observe local database for divisions
        viewModelScope.launch {
            repoManager.getAllDivisions().catch { e ->
                _divisions.value = UiState.Error(e.localizedMessage ?: "Error loading divisions")
            }.collect { divisionList ->
                _divisions.value = UiState.Success(divisionList)
            }
        }

        // Observe local database for classes
        viewModelScope.launch {
            repoManager.getAllClasses().catch { e ->
                _classes.value = UiState.Error(e.localizedMessage ?: "Error loading classes")
            }.collect { classList ->
                _classes.value = UiState.Success(classList)
            }
        }

        // Initial data refresh from remote
        refreshAll()
    }

    fun refreshAll() {
        viewModelScope.launch {
            _divisions.value = UiState.Loading
            repoManager.refreshDivisions()
            repoManager.refreshClasses()
        }
    }

    fun resetOperationState() {
        _operationState.value = UiState.Idle
    }

    // =============================
    // Division Operations
    // =============================
    fun addDivision(name: String) {
        if (name.isBlank()) {
            _operationState.value = UiState.Error("Division name cannot be empty.")
            return
        }
        _operationState.value = UiState.Loading
        viewModelScope.launch {
            val newDivision = DivisionModel(name = name)
            repoManager.addDivision(newDivision).fold(
                onSuccess = { _operationState.value = UiState.Success(Unit) },
                onFailure = { e -> _operationState.value = UiState.Error(e.localizedMessage ?: "Failed to add division") }
            )
        }
    }

    fun updateDivision(division: DivisionModel) {
        _operationState.value = UiState.Loading
        viewModelScope.launch {
            repoManager.updateDivision(division).fold(
                onSuccess = { _operationState.value = UiState.Success(Unit) },
                onFailure = { e -> _operationState.value = UiState.Error(e.localizedMessage ?: "Failed to update division") }
            )
        }
    }

    fun deleteDivision(divisionId: String) {
        _operationState.value = UiState.Loading
        viewModelScope.launch {
            repoManager.deleteDivision(divisionId).fold(
                onSuccess = { _operationState.value = UiState.Success(Unit) },
                onFailure = { e -> _operationState.value = UiState.Error(e.localizedMessage ?: "Failed to delete division") }
            )
        }
    }

    // =============================
    // Class Operations
    // =============================
    fun addClass(className: String, divisionName: String) {
        if (className.isBlank() || divisionName.isBlank()) {
            _operationState.value = UiState.Error("Class and division names cannot be empty.")
            return
        }
        _operationState.value = UiState.Loading
        viewModelScope.launch {
            val newClass = ClassModel(className = className, division = divisionName)
            repoManager.addClass(newClass).fold(
                onSuccess = { _operationState.value = UiState.Success(Unit) },
                onFailure = { e -> _operationState.value = UiState.Error(e.localizedMessage ?: "Failed to add class") }
            )
        }
    }

    fun updateClass(classModel: ClassModel) {
        _operationState.value = UiState.Loading
        viewModelScope.launch {
            repoManager.updateClass(classModel).fold(
                onSuccess = { _operationState.value = UiState.Success(Unit) },
                onFailure = { e -> _operationState.value = UiState.Error(e.localizedMessage ?: "Failed to update class") }
            )
        }
    }

    fun deleteClass(classId: String) {
        _operationState.value = UiState.Loading
        viewModelScope.launch {
            repoManager.deleteClass(classId).fold(
                onSuccess = { _operationState.value = UiState.Success(Unit) },
                onFailure = { e -> _operationState.value = UiState.Error(e.localizedMessage ?: "Failed to delete class") }
            )
        }
    }
}