package com.zabibtech.alkhair.ui.homework

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.storage.FirebaseStorage
import com.zabibtech.alkhair.data.manager.ClassDivisionRepoManager
import com.zabibtech.alkhair.data.manager.HomeworkRepoManager
import com.zabibtech.alkhair.data.manager.UserRepoManager
import com.zabibtech.alkhair.data.models.ClassModel
import com.zabibtech.alkhair.data.models.DivisionModel
import com.zabibtech.alkhair.data.models.Homework
import com.zabibtech.alkhair.utils.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class HomeworkViewModel @Inject constructor(
    private val homeworkRepoManager: HomeworkRepoManager,
    private val classDivisionRepoManager: ClassDivisionRepoManager,
    private val userRepoManager: UserRepoManager // Inject UserRepoManager
) : ViewModel() {

    // State for loading the list of homework
    private val _homeworkState = MutableStateFlow<UiState<List<Homework>>>(UiState.Idle)
    val homeworkState: StateFlow<UiState<List<Homework>>> = _homeworkState

    // State for add, update, or delete operations
    private val _mutationState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val mutationState: StateFlow<UiState<Unit>> = _mutationState

    private val _classesState = MutableStateFlow<UiState<List<ClassModel>>>(UiState.Idle)
    val classesState: StateFlow<UiState<List<ClassModel>>> = _classesState

    private val _divisionsState = MutableStateFlow<UiState<List<DivisionModel>>>(UiState.Idle)
    val divisionsState: StateFlow<UiState<List<DivisionModel>>> = _divisionsState

    init {
        loadClassesAndDivisions()
    }

    private fun loadClassesAndDivisions() {
        _classesState.value = UiState.Loading
        viewModelScope.launch {
            classDivisionRepoManager.getAllClasses().fold(
                onSuccess = { classList -> _classesState.value = UiState.Success(classList) },
                onFailure = { e -> _classesState.value = UiState.Error(e.localizedMessage ?: "Error loading classes") }
            )
        }

        _divisionsState.value = UiState.Loading
        viewModelScope.launch {
            classDivisionRepoManager.getAllDivisions().fold(
                onSuccess = { divisionList -> _divisionsState.value = UiState.Success(divisionList) },
                onFailure = { e -> _divisionsState.value = UiState.Error(e.localizedMessage ?: "Error loading divisions") }
            )
        }
    }

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
        _mutationState.value = UiState.Loading
        viewModelScope.launch {
            try {
                val currentUser = userRepoManager.getCurrentUser().getOrNull()
                if (currentUser == null) {
                    _mutationState.value = UiState.Error("User not logged in")
                    return@launch
                }

                val attachmentUrl = if (newAttachmentUri != null) {
                    uploadFile(newAttachmentUri)
                } else {
                    existingHomework?.attachmentUrl
                }

                val homework = Homework(
                    id = existingHomework?.id ?: UUID.randomUUID().toString(),
                    className = className,
                    division = division,
                    shift = shift,
                    subject = subject,
                    title = title,
                    description = description,
                    date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
                    teacherId = currentUser.uid,
                    attachmentUrl = attachmentUrl
                )

                val result = if (isEditMode) {
                    homeworkRepoManager.updateHomework(homework)
                } else {
                    homeworkRepoManager.createHomework(homework)
                }

                result.fold(
                    onSuccess = { _mutationState.value = UiState.Success(Unit) },
                    onFailure = { e -> _mutationState.value = UiState.Error(e.localizedMessage ?: "Submission failed") }
                )
            } catch (e: Exception) {
                _mutationState.value = UiState.Error(e.localizedMessage ?: "An unexpected error occurred")
            }
        }
    }

    private suspend fun uploadFile(uri: Uri): String {
        val storageRef = FirebaseStorage.getInstance().getReference("homework_attachments/${UUID.randomUUID()}")
        val uploadTask = storageRef.putFile(uri).await()
        return uploadTask.storage.downloadUrl.await().toString()
    }
    
    fun loadHomework(className: String, division: String) {
        viewModelScope.launch {
            _homeworkState.value = UiState.Loading
            homeworkRepoManager.getHomeworkByClass(className, division).fold(
                onSuccess = { homeworkList -> _homeworkState.value = UiState.Success(homeworkList) },
                onFailure = { error -> _homeworkState.value = UiState.Error(error.localizedMessage ?: "Failed to load homework") }
            )
        }
    }

    fun loadAllHomework() {
        viewModelScope.launch {
            _homeworkState.value = UiState.Loading
            homeworkRepoManager.getAllHomework().fold(
                onSuccess = { allHomework -> _homeworkState.value = UiState.Success(allHomework) },
                onFailure = { error -> _homeworkState.value = UiState.Error(error.localizedMessage ?: "Failed to load all homework") }
            )
        }
    }

    fun deleteHomework(homework: Homework) {
        viewModelScope.launch {
            _mutationState.value = UiState.Loading
            homeworkRepoManager.deleteHomework(homework.id).fold(
                onSuccess = { _mutationState.value = UiState.Success(Unit) },
                onFailure = { error -> _mutationState.value = UiState.Error(error.localizedMessage ?: "Failed to delete homework") }
            )
        }
    }

    fun resetMutationState() {
        _mutationState.value = UiState.Idle
    }
}
