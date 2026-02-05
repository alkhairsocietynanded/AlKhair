package com.aewsn.alkhair.ui.approval

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aewsn.alkhair.data.manager.LeaveRepoManager
import com.aewsn.alkhair.data.models.Leave
import com.aewsn.alkhair.data.models.LeaveWithStudent
import com.aewsn.alkhair.data.models.User
import com.aewsn.alkhair.utils.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LeaveApprovalViewModel @Inject constructor(
    private val leaveRepoManager: LeaveRepoManager
) : ViewModel() {

    private val _leavesState = MutableStateFlow<UiState<List<LeaveWithStudent>>>(UiState.Loading)
    val leavesState: StateFlow<UiState<List<LeaveWithStudent>>> = _leavesState.asStateFlow()

    private val _actionState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val actionState: StateFlow<UiState<Unit>> = _actionState.asStateFlow()

    fun loadLeaves(currentUser: User) {
        viewModelScope.launch {
            _leavesState.value = UiState.Loading
            try {
                if (currentUser.role.equals("admin", ignoreCase = true)) {
                    // Admin: Sync and Load All
                    launch { leaveRepoManager.syncAllLeaves() }
                    leaveRepoManager.getAllLeaves()
                        .catch { e -> _leavesState.value = UiState.Error(e.message ?: "Error loading leaves") }
                        .collectLatest { leaves ->
                            _leavesState.value = UiState.Success(leaves)
                        }
                } else if (currentUser.role.equals("teacher", ignoreCase = true)) {
                    // Teacher: Sync and Load Class Leaves
                    val classId = currentUser.classId
                    if (classId.isNullOrEmpty()) {
                        _leavesState.value = UiState.Error("No class assigned to teacher")
                        return@launch
                    }
                    launch { leaveRepoManager.syncLeavesForClass(classId) }
                    leaveRepoManager.getLeavesForClass(classId)
                        .catch { e -> _leavesState.value = UiState.Error(e.message ?: "Error loading class leaves") }
                        .collectLatest { leaves ->
                            _leavesState.value = UiState.Success(leaves)
                        }
                } else {
                    _leavesState.value = UiState.Error("Unauthorized access")
                }
            } catch (e: Exception) {
                _leavesState.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun approveLeave(leave: Leave) {
        updateStatus(leave, "Approved")
    }

    fun rejectLeave(leave: Leave) {
        updateStatus(leave, "Rejected")
    }

    private fun updateStatus(leave: Leave, status: String) {
        viewModelScope.launch {
            _actionState.value = UiState.Loading
            val result = leaveRepoManager.updateLeaveStatus(leave, status)
            if (result.isSuccess) {
                _actionState.value = UiState.Success(Unit)
            } else {
                _actionState.value = UiState.Error(result.exceptionOrNull()?.message ?: "Failed to update status")
            }
        }
    }
    
    fun resetActionState() {
        _actionState.value = UiState.Idle
    }
}
