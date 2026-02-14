package com.aewsn.alkhair.ui.studymaterial

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aewsn.alkhair.data.manager.StudyMaterialRepoManager
import com.aewsn.alkhair.data.manager.UserRepoManager
import com.aewsn.alkhair.data.models.StudyMaterial
import com.aewsn.alkhair.data.models.User
import com.aewsn.alkhair.utils.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StudyMaterialViewModel @Inject constructor(
    private val studyMaterialRepoManager: StudyMaterialRepoManager,
    private val userRepoManager: UserRepoManager,
    private val classRepo: com.aewsn.alkhair.data.local.local_repos.LocalClassRepository
) : ViewModel() {

    private val _userClassId = MutableStateFlow<String?>(null)
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    val allClasses = classRepo.getAllClasses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        fetchUserAndClass()
    }

    private fun fetchUserAndClass() {
        viewModelScope.launch {
            userRepoManager.getCurrentUserFlow().collectLatest { user ->
                _currentUser.value = user
                _userClassId.value = user?.classId
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val materialListState: StateFlow<UiState<List<StudyMaterial>>> = combine(_currentUser, _userClassId) { user, classId ->
        Pair(user, classId)
    }.flatMapLatest { (user, classId) ->
        if (user?.role == "admin") {
            // Admin sees ALL study materials
            studyMaterialRepoManager.observeLocal()
        } else if (classId != null) {
            // Student/Teacher sees class-specific materials
            studyMaterialRepoManager.observeClassMaterials(classId)
        } else {
            // Fallback (e.g. Teacher with no class assigned yet)
            flowOf(emptyList())
        }
    }
    .map<List<StudyMaterial>, UiState<List<StudyMaterial>>> { UiState.Success(it) }
    .onStart { emit(UiState.Loading) }
    .catch { emit(UiState.Error(it.message ?: "Failed to load study materials")) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Idle)

    fun createMaterial(
        subject: String,
        title: String,
        description: String,
        materialType: String,
        classId: String,
        attachmentUri: android.net.Uri?,
        linkUrl: String? = null
    ) {
        viewModelScope.launch {
            val material = StudyMaterial(
                classId = classId,
                subject = subject,
                title = title,
                description = description,
                materialType = materialType,
                teacherId = _currentUser.value?.uid,
                attachmentUrl = if (materialType == "Link") linkUrl else null
            )

            if (materialType == "Link") {
                // Link type: save directly without file upload
                val result = studyMaterialRepoManager.createMaterial(material, null)
                if (result.isFailure) {
                    android.util.Log.e("StudyMaterialVM", "Create failed", result.exceptionOrNull())
                }
            } else {
                val result = studyMaterialRepoManager.createMaterial(material, attachmentUri)
                if (result.isFailure) {
                    android.util.Log.e("StudyMaterialVM", "Create failed", result.exceptionOrNull())
                }
            }
        }
    }

    fun updateMaterial(
        id: String,
        subject: String,
        title: String,
        description: String,
        materialType: String,
        classId: String,
        currentAttachmentUrl: String?,
        newAttachmentUri: android.net.Uri?
    ) {
        viewModelScope.launch {
            val material = StudyMaterial(
                id = id,
                classId = classId,
                subject = subject,
                title = title,
                description = description,
                materialType = materialType,
                attachmentUrl = currentAttachmentUrl,
                teacherId = _currentUser.value?.uid,
                updatedAt = System.currentTimeMillis(),
                isSynced = false
            )

            val result = studyMaterialRepoManager.updateMaterial(material, newAttachmentUri)
            if (result.isFailure) {
                android.util.Log.e("StudyMaterialVM", "Update failed", result.exceptionOrNull())
            }
        }
    }

    fun deleteMaterial(id: String) {
        viewModelScope.launch {
            val result = studyMaterialRepoManager.deleteMaterial(id)
            if (result.isFailure) {
                android.util.Log.e("StudyMaterialVM", "Delete failed", result.exceptionOrNull())
            }
        }
    }
}
