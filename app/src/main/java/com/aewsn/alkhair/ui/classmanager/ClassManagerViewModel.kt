package com.aewsn.alkhair.ui.classmanager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aewsn.alkhair.data.manager.ClassDivisionRepoManager
import com.aewsn.alkhair.data.models.ClassModel
import com.aewsn.alkhair.data.models.DivisionModel
import com.aewsn.alkhair.utils.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ClassManagerUiData(
    val divisions: List<DivisionModel>,
    val classes: List<ClassModel>
)

@HiltViewModel
class ClassManagerViewModel @Inject constructor(
    private val repoManager: ClassDivisionRepoManager
) : ViewModel() {

    /* ============================================================
       📦 REACTIVE UI STATE
       ============================================================ */

    // Combines Classes and Divisions into a single UI State object
    val uiState: StateFlow<UiState<ClassManagerUiData>> =
        combine(
            repoManager.observeDivisions(),
            repoManager.observeClasses()
        ) { divisions, classes ->
            ClassManagerUiData(divisions, classes)
        }
            .map<ClassManagerUiData, UiState<ClassManagerUiData>> {
                UiState.Success(it)
            }
            .onStart { emit(UiState.Loading) }
            .catch { emit(UiState.Error(it.message ?: "Failed to load classes")) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                UiState.Idle
            )

    /* ============================================================
       ✍️ OPERATIONS (Add / Update / Delete)
       ============================================================ */

    private val _operationState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val operationState: StateFlow<UiState<Unit>> = _operationState

    // --- DIVISIONS ---
    fun addDivision(name: String) {
        if (name.isBlank()) {
            _operationState.value = UiState.Error("Division name cannot be empty.")
            return
        }
        launchOperation { repoManager.addDivision(DivisionModel(name = name)) }
    }

    fun deleteDivision(divisionId: String) {
        launchOperation { repoManager.deleteDivision(divisionId) }
    }

    // --- CLASSES ---
    fun addClass(className: String, divisionName: String) {
        if (className.isBlank() || divisionName.isBlank()) {
            _operationState.value = UiState.Error("Invalid input.")
            return
        }
        val newClass = ClassModel(className = className, divisionName = divisionName)
        launchOperation { repoManager.addClass(newClass) }
    }

    fun updateClass(classModel: ClassModel) {
        launchOperation { repoManager.updateClass(classModel) }
    }

    fun deleteClass(classId: String) {
        launchOperation { repoManager.deleteClass(classId) }
    }

    // Helper to reduce boilerplate
    private fun <T> launchOperation(block: suspend () -> Result<T>) {
        _operationState.value = UiState.Loading
        viewModelScope.launch {
            block().fold(
                onSuccess = { _operationState.value = UiState.Success(Unit) },
                onFailure = { _operationState.value = UiState.Error(it.message ?: "Operation failed") }
            )
        }
    }

    fun resetOperationState() {
        _operationState.value = UiState.Idle
    }
}