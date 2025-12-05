package com.zabibtech.alkhair.ui.homework

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zabibtech.alkhair.data.manager.ClassDivisionRepoManager // Import ClassDivisionRepoManager
import com.zabibtech.alkhair.data.manager.HomeworkRepoManager
import com.zabibtech.alkhair.data.models.ClassModel
import com.zabibtech.alkhair.data.models.DivisionModel
import com.zabibtech.alkhair.data.models.Homework
import com.zabibtech.alkhair.utils.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeworkViewModel @Inject constructor(
    private val homeworkRepoManager: HomeworkRepoManager,
    private val classDivisionRepoManager: ClassDivisionRepoManager // Inject ClassDivisionRepoManager
) : ViewModel() {

    // State for loading the list of homework
    private val _homeworkState = MutableStateFlow<UiState<List<Homework>>>(UiState.Idle)
    val homeworkState: StateFlow<UiState<List<Homework>>> = _homeworkState

    // State for add, update, or delete operations
    private val _mutationState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val mutationState: StateFlow<UiState<Unit>> = _mutationState

    // New: State for loading classes and divisions
    private val _classesState = MutableStateFlow<UiState<List<ClassModel>>>(UiState.Idle)
    val classesState: StateFlow<UiState<List<ClassModel>>> = _classesState

    private val _divisionsState = MutableStateFlow<UiState<List<DivisionModel>>>(UiState.Idle)
    val divisionsState: StateFlow<UiState<List<DivisionModel>>> = _divisionsState

    init {
        loadClassesAndDivisions() // Load classes and divisions on init
    }

    fun loadClassesAndDivisions() {
        viewModelScope.launch {
            // Observe local database for classes
            classDivisionRepoManager.getAllClasses().catch {
                _classesState.value = UiState.Error(it.localizedMessage ?: "Error loading classes")
            }.collect {
                _classesState.value = UiState.Success(it)
            }
        }
        viewModelScope.launch {
            // Observe local database for divisions
            classDivisionRepoManager.getAllDivisions().catch {
                _divisionsState.value = UiState.Error(it.localizedMessage ?: "Error loading divisions")
            }.collect {
                _divisionsState.value = UiState.Success(it)
            }
        }
        // Initial data refresh from remote
        viewModelScope.launch {
            classDivisionRepoManager.refreshClasses()
            classDivisionRepoManager.refreshDivisions()
        }
    }

    /**
     * Loads homework for a specific class and division.
     * The result is emitted via the [homeworkState].
     */
    fun loadHomework(className: String, division: String) {
        viewModelScope.launch {
            _homeworkState.value = UiState.Loading
            homeworkRepoManager.getHomeworkByClass(className, division).fold(
                onSuccess = { homeworkList ->
                    _homeworkState.value = UiState.Success(homeworkList)
                },
                onFailure = { error ->
                    _homeworkState.value =
                        UiState.Error(error.localizedMessage ?: "Failed to load homework")
                }
            )
        }
    }

    /**
     * Loads all homework from all classes and divisions.
     * The result is emitted via the [homeworkState].
     */
    fun loadAllHomework() {
        viewModelScope.launch {
            _homeworkState.value = UiState.Loading
            homeworkRepoManager.getAllHomework().fold(
                onSuccess = { allHomework ->
                    _homeworkState.value = UiState.Success(allHomework)
                },
                onFailure = { error ->
                    _homeworkState.value =
                        UiState.Error(error.localizedMessage ?: "Failed to load all homework")
                }
            )
        }
    }

    /**
     * Adds a new homework item.
     * The result of the operation is emitted via the [mutationState].
     */
    fun addHomework(homework: Homework) {
        viewModelScope.launch {
            _mutationState.value = UiState.Loading
            homeworkRepoManager.createHomework(homework).fold(
                onSuccess = { _ ->
                    _mutationState.value = UiState.Success(Unit)
                },
                onFailure = { error ->
                    _mutationState.value = UiState.Error(error.localizedMessage ?: "Failed to add homework")
                }
            )
        }
    }

    /**
     * Updates an existing homework item.
     * The result of the operation is emitted via the [mutationState].
     */
    fun updateHomework(homework: Homework) {
        viewModelScope.launch {
            _mutationState.value = UiState.Loading
            homeworkRepoManager.updateHomework(homework).fold(
                onSuccess = { _ ->
                    _mutationState.value = UiState.Success(Unit)
                },
                onFailure = { error ->
                    _mutationState.value =
                        UiState.Error(error.localizedMessage ?: "Failed to update homework")
                }
            )
        }
    }

    /**
     * Deletes a homework item.
     * The result of the operation is emitted via the [mutationState].
     */
    fun deleteHomework(homework: Homework) {
        viewModelScope.launch {
            _mutationState.value = UiState.Loading
            homeworkRepoManager.deleteHomework(homework.id).fold(
                onSuccess = { _ ->
                    _mutationState.value = UiState.Success(Unit)
                },
                onFailure = { error ->
                    _mutationState.value =
                        UiState.Error(error.localizedMessage ?: "Failed to delete homework")
                }
            )
        }
    }

    /**
     * Resets the mutation state to Idle. Call this after consuming a
     * Success or Error event in the UI to prevent it from being re-triggered.
     */
    fun resetMutationState() {
        _mutationState.value = UiState.Idle
    }
}
