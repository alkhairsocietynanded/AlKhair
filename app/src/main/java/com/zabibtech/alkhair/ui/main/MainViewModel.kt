package com.zabibtech.alkhair.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zabibtech.alkhair.data.manager.AuthRepoManager
import com.zabibtech.alkhair.data.manager.UserRepoManager
import com.zabibtech.alkhair.data.models.User
import com.zabibtech.alkhair.utils.UiState
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
                        if (uid != null) {
                            val user = userRepoManager.getUserById(uid)
                            _userSessionState.value = UiState.Success(user)
                        } else {
                             // Should not happen if Authenticated, but handling just in case
                            _userSessionState.value = UiState.Error("Authenticated but no UID found")
                        }
                    }
                    is SessionStatus.NotAuthenticated -> {
                         // Only treat as "not logged in" if explicitly NotAuthenticated (after load attempt)
                         if(authRepoManager.getCurrentUserUid() == null) {
                             _userSessionState.value = UiState.Success(null) // Trigger Login
                         }
                    }
                    is SessionStatus.Initializing -> {
                        // Still loading, keep waiting...
                         _userSessionState.value = UiState.Loading
                    }
                    is SessionStatus.RefreshFailure -> {
                        // Failed to refresh session, treat as logged out
                        _userSessionState.value = UiState.Success(null)
                    }
                }
            }
        }
    }
}