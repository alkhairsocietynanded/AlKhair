package com.aewsn.alkhair.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aewsn.alkhair.data.manager.AuthRepoManager
import com.aewsn.alkhair.data.manager.UserRepoManager
import com.aewsn.alkhair.data.models.User
import com.aewsn.alkhair.utils.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject


@HiltViewModel
class MainViewModel @Inject constructor(
    private val authRepoManager: AuthRepoManager,
    private val userRepoManager: UserRepoManager,
) : ViewModel() {

    private val _userSessionState = MutableStateFlow<UiState<User?>>(UiState.Idle)
    val userSessionState: StateFlow<UiState<User?>> = _userSessionState

    /*   init {
           checkUserSession()
       }*/

    fun checkUserSession() {
        viewModelScope.launch(Dispatchers.IO) { // ✅ Run on Background Thread
            _userSessionState.value = UiState.Loading

            // Collect session status to wait for auto-load to complete
            authRepoManager.monitorSession().collect { status ->
                when (status) {
                    is SessionStatus.Authenticated -> {
                        val uid = authRepoManager.getCurrentUserUid()
                        // ✅ Migration: Ensure login state is saved for existing users
                        if (uid != null) authRepoManager.saveLoginState(uid)
                        
                        handleAuthenticatedUser(uid)
                    }

                    is SessionStatus.NotAuthenticated,
                    is SessionStatus.RefreshFailure -> {
                        // ⚠ Supabase Session Failed (Expired or Offline)
                        // ✅ Check Fallback: Do we have a locally saved Login State?
                        val fallbackUid = authRepoManager.getLocalLoginUid()

                        if (fallbackUid != null) {
                            // We believe the user is logged in, but network/session failed.
                            // Let's trust the Local DB.
                            val localUser = userRepoManager.getUserById(fallbackUid)
                            if (localUser != null) {
                                _userSessionState.value = UiState.Success(localUser)
                            } else {
                                // Data mismatch (UID expected but not in DB) -> Logout
                                _userSessionState.value = UiState.Success(null)
                            }
                        } else {
                            // Truly logged out
                            _userSessionState.value = UiState.Success(null)
                        }
                    }

                    is SessionStatus.Initializing -> {
                        // Still loading, keep waiting...
                        _userSessionState.value = UiState.Loading
                    }
                }
            }
        }
    }

    private suspend fun handleAuthenticatedUser(uid: String?) {
        if (uid != null) {
            // 1. Try Local DB
            val user = userRepoManager.getUserById(uid)
            if (user != null) {
                _userSessionState.value = UiState.Success(user)
            } else {
                // 2. User Authenticated but Missing in Local DB (Sync Issue/Fresh Clean)
                // Attempt to fetch from Remote
                userRepoManager.syncUserProfile(uid)
                    .onSuccess {
                        // Retry Local Fetch after Sync
                        val syncedUser = userRepoManager.getUserById(uid)
                        if (syncedUser != null) {
                            _userSessionState.value = UiState.Success(syncedUser)
                        } else {
                            _userSessionState.value = UiState.Error("User profile fetch failed")
                        }
                    }
                    .onFailure {
                        // Remote Sync Failed (Likely Offline & No Local Data) -> Stuck
                        _userSessionState.value = UiState.Error("Unable to load user profile. Check internet.")
                    }
            }
        } else {
            // Should not happen if Authenticated, but handling just in case
            _userSessionState.value = UiState.Error("Authenticated but no UID found")
        }
    }
}