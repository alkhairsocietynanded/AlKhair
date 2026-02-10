package com.aewsn.alkhair.ui.syllabus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aewsn.alkhair.data.manager.SyllabusRepoManager
import com.aewsn.alkhair.data.manager.UserRepoManager
import com.aewsn.alkhair.data.models.Syllabus
import com.aewsn.alkhair.data.models.User
import com.aewsn.alkhair.utils.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SyllabusViewModel @Inject constructor(
    private val syllabusRepoManager: SyllabusRepoManager,
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
    val syllabusListState: StateFlow<UiState<List<Syllabus>>> = combine(_currentUser, _userClassId) { user, classId ->
        Pair(user, classId)
    }.flatMapLatest { (user, classId) ->
        if (user?.role == "admin") {
            // Admin sees ALL syllabus items
            syllabusRepoManager.observeLocal()
        } else if (classId != null) {
            // Student/Teacher sees class-specific syllabus
            syllabusRepoManager.observeClassSyllabus(classId)
        } else {
            // Fallback (e.g. Teacher with no class assigned yet)
            flowOf(emptyList())
        }
    }
    .map<List<Syllabus>, UiState<List<Syllabus>>> { UiState.Success(it) }
    .onStart { emit(UiState.Loading) }
    .catch { emit(UiState.Error(it.message ?: "Failed to load syllabus")) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Idle)

    fun createSyllabus(subject: String, topic: String, description: String, classId: String, attachmentUri: android.net.Uri?) {
        viewModelScope.launch {
            val syllabus = Syllabus(
                classId = classId,
                subject = subject,
                topic = topic,
                description = description,
                // attachmentUrl set in RepoManager
            )
            
            // We don't expose result state for now, just toast in UI if needed or assume success/worker handles it
            val result = syllabusRepoManager.createSyllabus(syllabus, attachmentUri)
            if (result.isFailure) {
                // Handle error (expose via sharedflow if needed)
                android.util.Log.e("SyllabusViewModel", "Create failed", result.exceptionOrNull())
            }
        }
    }

    fun updateSyllabus(
        id: String,
        subject: String,
        topic: String,
        description: String,
        classId: String,
        currentAttachmentUrl: String?,
        newAttachmentUri: android.net.Uri?
    ) {
        viewModelScope.launch {
            val syllabus = Syllabus(
                id = id,
                classId = classId,
                subject = subject,
                topic = topic,
                description = description,
                attachmentUrl = currentAttachmentUrl, // specific field for update
                updatedAt = System.currentTimeMillis(),
                isSynced = false // Force sync
            )
            
            val result = syllabusRepoManager.updateSyllabus(syllabus, newAttachmentUri)
             if (result.isFailure) {
                android.util.Log.e("SyllabusViewModel", "Update failed", result.exceptionOrNull())
            }
        }
    }

    fun deleteSyllabus(id: String) {
        viewModelScope.launch {
            val result = syllabusRepoManager.deleteSyllabus(id)
            if (result.isFailure) {
                 android.util.Log.e("SyllabusViewModel", "Delete failed", result.exceptionOrNull())
            }
        }
    }


}
