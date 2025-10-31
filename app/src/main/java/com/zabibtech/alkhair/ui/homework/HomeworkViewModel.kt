package com.zabibtech.alkhair.ui.homework

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zabibtech.alkhair.data.models.Homework
import com.zabibtech.alkhair.data.repository.HomeworkRepository
import com.zabibtech.alkhair.utils.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeworkViewModel @Inject constructor(
    private val repository: HomeworkRepository
) : ViewModel() {

    // State for loading the list of homework
    private val _homeworkState = MutableStateFlow<UiState<List<Homework>>>(UiState.Idle)
    val homeworkState: StateFlow<UiState<List<Homework>>> = _homeworkState

    // State for add, update, or delete operations
    private val _mutationState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val mutationState: StateFlow<UiState<Unit>> = _mutationState

    /**
     * Loads homework for a specific class and division.
     * The result is emitted via the [homeworkState].
     */
    fun loadHomework(className: String, division: String) {
        viewModelScope.launch {
            _homeworkState.value = UiState.Loading
            try {
                val homeworkList = repository.getHomeworkListByClass(className, division)
                _homeworkState.value = UiState.Success(homeworkList)
            } catch (e: Exception) {
                _homeworkState.value =
                    UiState.Error(e.localizedMessage ?: "Failed to load homework")
            }
        }
    }

    /**
     * Loads all homework from all classes and divisions.
     * The result is emitted via the [homeworkState].
     */
    fun loadAllHomework() {
        viewModelScope.launch {
            _homeworkState.value = UiState.Loading
            try {
                val allHomework = repository.getAllHomeworkList()
                _homeworkState.value = UiState.Success(allHomework)
            } catch (e: Exception) {
                _homeworkState.value =
                    UiState.Error(e.localizedMessage ?: "Failed to load all homework")
            }
        }
    }

    /**
     * Adds a new homework item.
     * The result of the operation is emitted via the [mutationState].
     */
    fun addHomework(homework: Homework) {
        viewModelScope.launch {
            _mutationState.value = UiState.Loading
            try {
                repository.addHomework(homework)
                _mutationState.value = UiState.Success(Unit)
            } catch (e: Exception) {
                _mutationState.value = UiState.Error(e.localizedMessage ?: "Failed to add homework")
            }
        }
    }

    /**
     * Updates an existing homework item.
     * The result of the operation is emitted via the [mutationState].
     */
    fun updateHomework(homework: Homework) {
        viewModelScope.launch {
            _mutationState.value = UiState.Loading
            try {
                repository.updateHomework(homework)
                _mutationState.value = UiState.Success(Unit)
            } catch (e: Exception) {
                _mutationState.value =
                    UiState.Error(e.localizedMessage ?: "Failed to update homework")
            }
        }
    }

    /**
     * Deletes a homework item.
     * The result of the operation is emitted via the [mutationState].
     */
    fun deleteHomework(homework: Homework) {
        viewModelScope.launch {
            _mutationState.value = UiState.Loading
            try {
                repository.deleteHomework(homework)
                _mutationState.value = UiState.Success(Unit)
            } catch (e: Exception) {
                _mutationState.value =
                    UiState.Error(e.localizedMessage ?: "Failed to delete homework")
            }
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