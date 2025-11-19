package com.zabibtech.alkhair.ui.announcement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zabibtech.alkhair.data.models.Announcement
import com.zabibtech.alkhair.data.repository.AnnouncementRepository
import com.zabibtech.alkhair.utils.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AnnouncementViewModel @Inject constructor(
    private val repository: AnnouncementRepository
) : ViewModel() {

    // Dashboard par dikhane ke liye sirf 5 latest announcements
    private val _latestAnnouncementsState = MutableStateFlow<UiState<List<Announcement>>>(UiState.Idle)
    val latestAnnouncementsState: StateFlow<UiState<List<Announcement>>> = _latestAnnouncementsState

    // "View All" screen ke liye saare announcements
    private val _allAnnouncementsState = MutableStateFlow<UiState<List<Announcement>>>(UiState.Idle)
    val allAnnouncementsState: StateFlow<UiState<List<Announcement>>> = _allAnnouncementsState

    // Add/Update/Delete operations ke liye states
    private val _addAnnouncementState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val addAnnouncementState: StateFlow<UiState<Unit>> = _addAnnouncementState

    private val _deleteAnnouncementState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val deleteAnnouncementState: StateFlow<UiState<Unit>> = _deleteAnnouncementState

    init {
        // ViewModel shuru hote hi, dashboard ke liye latest announcements load karein
        loadFiveLatestAnnouncements()
    }

    /**
     * Sirf 5 sabse naye announcements fetch karta hai.
     */
    fun loadFiveLatestAnnouncements() {
        _latestAnnouncementsState.value = UiState.Loading
        viewModelScope.launch {
            val result = repository.getFiveLatestAnnouncements()
            result.onSuccess { announcements ->
                _latestAnnouncementsState.value = UiState.Success(announcements)
            }
            result.onFailure { error ->
                _latestAnnouncementsState.value = UiState.Error(error.localizedMessage ?: "Failed to load announcements")
            }
        }
    }

    /**
     * Saare announcements fetch karta hai (e.g., "All Announcements" screen ke liye).
     */
    fun loadAllAnnouncements() {
        _allAnnouncementsState.value = UiState.Loading
        viewModelScope.launch {
            val result = repository.getAllAnnouncements()
            result.onSuccess { announcements ->
                _allAnnouncementsState.value = UiState.Success(announcements)
            }
            result.onFailure { error ->
                _allAnnouncementsState.value = UiState.Error(error.localizedMessage ?: "Failed to load all announcements")
            }
        }
    }

    fun addOrUpdateAnnouncement(announcement: Announcement) {
        _addAnnouncementState.value = UiState.Loading
        viewModelScope.launch {
            if (announcement.title.isBlank() || announcement.content.isBlank()) {
                _addAnnouncementState.value = UiState.Error("Title and content cannot be empty.")
                return@launch
            }

            val result = repository.addAnnouncement(announcement)
            result.onSuccess {
                _addAnnouncementState.value = UiState.Success(Unit)
                // Refresh karein taaki dashboard par naya item aa jaye
                loadFiveLatestAnnouncements()
            }
            result.onFailure { error ->
                _addAnnouncementState.value = UiState.Error(error.localizedMessage ?: "Failed to save announcement")
            }
        }
    }

    fun deleteAnnouncement(announcementId: String) {
        _deleteAnnouncementState.value = UiState.Loading
        viewModelScope.launch {
            val result = repository.deleteAnnouncement(announcementId)
            result.onSuccess {
                _deleteAnnouncementState.value = UiState.Success(Unit)
                loadFiveLatestAnnouncements()
            }
            result.onFailure { error ->
                _deleteAnnouncementState.value = UiState.Error(error.localizedMessage ?: "Failed to delete announcement")
            }
        }
    }

    fun resetAddAnnouncementState() {
        _addAnnouncementState.value = UiState.Idle
    }
}
