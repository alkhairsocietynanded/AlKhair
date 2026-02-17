package com.aewsn.alkhair.ui.announcement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aewsn.alkhair.data.manager.AnnouncementRepoManager
import com.aewsn.alkhair.data.models.Announcement
import com.aewsn.alkhair.utils.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AnnouncementViewModel @Inject constructor(
    private val announcementRepoManager: AnnouncementRepoManager
) : ViewModel() {

    /* ============================================================
       📦 READ STREAMS
       ============================================================ */
    val latestAnnouncementsState: StateFlow<UiState<List<Announcement>>> =
        announcementRepoManager.observeLatestAnnouncements()
            .map { UiState.Success(it) as UiState<List<Announcement>> }
            .onStart { emit(UiState.Loading) }
            .catch { emit(UiState.Error(it.message ?: "Failed to load announcements")) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                UiState.Idle
            )

    /* ============================================================
       ✍️ MUTATIONS
       ============================================================ */
    private val _mutationState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val mutationState: StateFlow<UiState<Unit>> = _mutationState

    /**
     * ✅ UPDATED:
     * 1. 'target' parameter added (Default = "ALL").
     * 2. UUID generation REMOVED.
     */
    fun createAnnouncement(title: String, content: String, target: String = "ALL") {
        if (title.isBlank() || content.isBlank()) {
            _mutationState.value = UiState.Error("Title and content cannot be empty.")
            return
        }

        _mutationState.value = UiState.Loading
        viewModelScope.launch {

            // ✅ ID is empty string here.
            // Repo will see empty ID and generate one using Firebase push().key
            val newAnnouncement = Announcement(
                title = title,
                content = content,
                target = target,
                timestamp = System.currentTimeMillis()
            )

            announcementRepoManager.createAnnouncement(newAnnouncement).fold(
                onSuccess = { _mutationState.value = UiState.Success(Unit) },
                onFailure = { _mutationState.value = UiState.Error(it.message ?: "Create failed") }
            )
        }
    }

    fun deleteAnnouncement(announcementId: String) {
        _mutationState.value = UiState.Loading
        viewModelScope.launch {
            announcementRepoManager.deleteAnnouncement(announcementId).fold(
                onSuccess = { _mutationState.value = UiState.Success(Unit) },
                onFailure = { _mutationState.value = UiState.Error(it.message ?: "Delete failed") }
            )
        }
    }

    fun resetMutationState() {
        _mutationState.value = UiState.Idle
    }
}