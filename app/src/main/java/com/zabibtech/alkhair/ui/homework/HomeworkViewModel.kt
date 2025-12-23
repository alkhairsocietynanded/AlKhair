package com.zabibtech.alkhair.ui.homework

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zabibtech.alkhair.data.manager.ClassDivisionRepoManager
import com.zabibtech.alkhair.data.manager.HomeworkRepoManager
import com.zabibtech.alkhair.data.models.ClassModel
import com.zabibtech.alkhair.data.models.DivisionModel
import com.zabibtech.alkhair.data.models.Homework
import com.zabibtech.alkhair.utils.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeworkViewModel @Inject constructor(
    private val homeworkRepoManager: HomeworkRepoManager,
    private val classDivisionRepoManager: ClassDivisionRepoManager
) : ViewModel() {

    /* ============================================================
       📘 CLASS & DIVISION DROPDOWNS (Reactive)
       ============================================================ */

    // Replaced manual loading with direct observation of the Repo Flows

    val classesState: StateFlow<UiState<List<ClassModel>>> =
        classDivisionRepoManager.observeClasses()
            .map<List<ClassModel>, UiState<List<ClassModel>>> { UiState.Success(it) }
            .onStart { emit(UiState.Loading) }
            .catch { emit(UiState.Error(it.message ?: "Failed to load classes")) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                UiState.Idle
            )

    val divisionsState: StateFlow<UiState<List<DivisionModel>>> =
        classDivisionRepoManager.observeDivisions()
            .map<List<DivisionModel>, UiState<List<DivisionModel>>> { UiState.Success(it) }
            .onStart { emit(UiState.Loading) }
            .catch { emit(UiState.Error(it.message ?: "Failed to load divisions")) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                UiState.Idle
            )

    /* ============================================================
       🔹 FILTER STATE
       ============================================================ */

    private val classFilter = MutableStateFlow<String?>(null)
    private val divisionFilter = MutableStateFlow<String?>(null)

    fun setFilters(className: String?, division: String?) {
        classFilter.value = className
        divisionFilter.value = division
    }

    /* ============================================================
       📦 HOMEWORK LIST — SSOT
       ============================================================ */

    @OptIn(ExperimentalCoroutinesApi::class)
    val homeworkListState: StateFlow<UiState<List<Homework>>> =
        combine(classFilter, divisionFilter) { c, d -> c to d }
            .flatMapLatest { (c, d) ->
                homeworkRepoManager.observeFiltered(c, d)
            }
            .map<List<Homework>, UiState<List<Homework>>> {
                UiState.Success(it)
            }
            .onStart { emit(UiState.Loading) }
            .catch {
                emit(UiState.Error(it.message ?: "Failed to load homework"))
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                UiState.Idle
            )

    /* ============================================================
       ✍️ MUTATIONS
       ============================================================ */

    private val _mutationState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val mutationState: StateFlow<UiState<Unit>> = _mutationState

    /**
     * Core function — single source of mutation
     */
    fun saveHomework(
        homework: Homework,
        isEdit: Boolean,
        newAttachmentUri: Uri? = null
    ) {
        _mutationState.value = UiState.Loading

        viewModelScope.launch {
            val result = if (isEdit) {
                homeworkRepoManager.updateHomework(homework, newAttachmentUri)
            } else {
                homeworkRepoManager.createHomework(homework, newAttachmentUri)
            }

            _mutationState.value = result.fold(
                onSuccess = { UiState.Success(Unit) },
                onFailure = {
                    UiState.Error(it.message ?: "Operation Failed")
                }
            )
        }
    }

    /**
     * UI-facing function — no duplication, no business logic leak
     */
    fun createOrUpdateHomework(
        isEditMode: Boolean,
        existingHomework: Homework?,
        className: String,
        division: String,
        shift: String,
        subject: String,
        title: String,
        description: String,
        newAttachmentUri: Uri?
    ) {
        val now = System.currentTimeMillis()

        val homework = Homework(
            id = existingHomework?.id ?: "",
            className = className,
            division = division,
            shift = shift,
            subject = subject,
            title = title,
            description = description,
            attachmentUrl = existingHomework?.attachmentUrl,
            updatedAt = now
        )

        saveHomework(homework, isEditMode, newAttachmentUri)
    }

    fun deleteHomework(id: String) {
        _mutationState.value = UiState.Loading
        viewModelScope.launch {
            _mutationState.value = homeworkRepoManager.deleteHomework(id)
                .fold(
                    onSuccess = { UiState.Success(Unit) },
                    onFailure = { UiState.Error(it.message ?: "Delete failed") }
                )
        }
    }

    fun resetMutationState() {
        _mutationState.value = UiState.Idle
    }
}