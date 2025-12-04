package com.zabibtech.alkhair.ui.announcement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zabibtech.alkhair.data.manager.AnnouncementRepoManager
import com.zabibtech.alkhair.data.models.Announcement
import com.zabibtech.alkhair.utils.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AnnouncementViewModel @Inject constructor(
    private val announcementRepoManager: AnnouncementRepoManager
) : ViewModel() {

    private val _latestAnnouncementsState = MutableStateFlow<UiState<List<Announcement>>>(UiState.Idle)
    val latestAnnouncementsState: StateFlow<UiState<List<Announcement>>> = _latestAnnouncementsState

    private val _allAnnouncementsState = MutableStateFlow<UiState<List<Announcement>>>(UiState.Idle)
    val allAnnouncementsState: StateFlow<UiState<List<Announcement>>> = _allAnnouncementsState

    private val _addUpdateAnnouncementState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val addUpdateAnnouncementState: StateFlow<UiState<Unit>> = _addUpdateAnnouncementState

    private val _deleteAnnouncementState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val deleteAnnouncementState: StateFlow<UiState<Unit>> = _deleteAnnouncementState

    init {
        loadFiveLatestAnnouncements()
//        loadAllAnnouncements() // Initial load for all announcements
    }

    fun createAnnouncement(announcement: Announcement) {
        _addUpdateAnnouncementState.value = UiState.Loading
        viewModelScope.launch {
            if (announcement.title.isBlank() || announcement.content.isBlank()) {
                _addUpdateAnnouncementState.value = UiState.Error("Title and content cannot be empty.")
                return@launch
            }

            announcementRepoManager.createAnnouncement(announcement).fold(
                onSuccess = {
                    _addUpdateAnnouncementState.value = UiState.Success(Unit)
                    loadFiveLatestAnnouncements() // Refresh latest
//                    loadAllAnnouncements() // Refresh all
                },
                onFailure = { error ->
                    _addUpdateAnnouncementState.value = UiState.Error(error.localizedMessage ?: "Failed to create announcement")
                }
            )
        }
    }

    fun loadFiveLatestAnnouncements() {
        _latestAnnouncementsState.value = UiState.Loading
        viewModelScope.launch {
            announcementRepoManager.getFiveLatestAnnouncements().fold(
                onSuccess = { announcements ->
                    _latestAnnouncementsState.value = UiState.Success(announcements)
                },
                onFailure = { error ->
                    _latestAnnouncementsState.value = UiState.Error(error.localizedMessage ?: "Failed to load announcements")
                }
            )
        }
    }

    fun loadAllAnnouncements() {
        _allAnnouncementsState.value = UiState.Loading
        viewModelScope.launch {
            announcementRepoManager.getAllAnnouncements().fold(
                onSuccess = { announcements ->
                    _allAnnouncementsState.value = UiState.Success(announcements)
                },
                onFailure = { error ->
                    _allAnnouncementsState.value = UiState.Error(error.localizedMessage ?: "Failed to load all announcements")
                }
            )
        }
    }

    fun updateAnnouncement(announcement: Announcement) {
        _addUpdateAnnouncementState.value = UiState.Loading
        viewModelScope.launch {
            if (announcement.id.isBlank()) {
                _addUpdateAnnouncementState.value = UiState.Error("Announcement ID cannot be empty for update operation.")
                return@launch
            }
            if (announcement.title.isBlank() || announcement.content.isBlank()) {
                _addUpdateAnnouncementState.value = UiState.Error("Title and content cannot be empty.")
                return@launch
            }

            announcementRepoManager.updateAnnouncement(announcement).fold(
                onSuccess = {
                    _addUpdateAnnouncementState.value = UiState.Success(Unit)
                    loadFiveLatestAnnouncements() // Refresh latest
//                    loadAllAnnouncements() // Refresh all
                },
                onFailure = { error ->
                    _addUpdateAnnouncementState.value = UiState.Error(error.localizedMessage ?: "Failed to update announcement")
                }
            )
        }
    }

    fun deleteAnnouncement(announcementId: String) {
        _deleteAnnouncementState.value = UiState.Loading
        viewModelScope.launch {
            announcementRepoManager.deleteAnnouncement(announcementId).fold(
                onSuccess = {
                    _deleteAnnouncementState.value = UiState.Success(Unit)
                    loadFiveLatestAnnouncements() // Refresh latest
//                    loadAllAnnouncements() // Refresh all
                },
                onFailure = { error ->
                    _deleteAnnouncementState.value = UiState.Error(error.localizedMessage ?: "Failed to delete announcement")
                }
            )
        }
    }

    fun resetAddUpdateAnnouncementState() {
        _addUpdateAnnouncementState.value = UiState.Idle
    }
}
