package com.zabibtech.alkhair.ui.announcement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zabibtech.alkhair.data.manager.AnnouncementRepoManager
import com.zabibtech.alkhair.data.models.Announcement
import com.zabibtech.alkhair.utils.UiState
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
       📦 READ STREAMS (SSOT)
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
       ✍️ MUTATIONS (Create / Update / Delete)
       ============================================================ */

    // Renamed to match other modules
    private val _mutationState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val mutationState: StateFlow<UiState<Unit>> = _mutationState

    fun createAnnouncement(title: String, content: String) {
        if (title.isBlank() || content.isBlank()) {
            _mutationState.value = UiState.Error("Title and content cannot be empty.")
            return
        }

        _mutationState.value = UiState.Loading
        viewModelScope.launch {
            // We generate ID locally to allow immediate insertion into Room DB (Offline support)
            val newAnnouncement = Announcement(
                title = title,
                content = content,
                timeStamp = System.currentTimeMillis()
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