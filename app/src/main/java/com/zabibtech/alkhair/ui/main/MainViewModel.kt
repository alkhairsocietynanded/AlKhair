package com.zabibtech.alkhair.ui.main

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zabibtech.alkhair.data.manager.AppDataSyncManager
import com.zabibtech.alkhair.data.manager.AttendanceRepoManager
import com.zabibtech.alkhair.data.manager.AuthRepoManager
import com.zabibtech.alkhair.data.manager.ClassDivisionRepoManager
import com.zabibtech.alkhair.data.manager.UserRepoManager
import com.zabibtech.alkhair.data.models.User
import com.zabibtech.alkhair.utils.DateUtils
import com.zabibtech.alkhair.utils.Roles
import com.zabibtech.alkhair.utils.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject
import com.zabibtech.alkhair.di.ApplicationScope
import kotlinx.coroutines.Dispatchers


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
            try {
                val uid = authRepoManager.getCurrentUserUid()
                val user = if (uid != null) userRepoManager.getUserById(uid) else null

                // Switch back to Main to update UI State
                _userSessionState.value = UiState.Success(user)
            } catch (e: Exception) {
                _userSessionState.value = UiState.Error(e.message ?: "Session Error")
            }
        }
    }
}