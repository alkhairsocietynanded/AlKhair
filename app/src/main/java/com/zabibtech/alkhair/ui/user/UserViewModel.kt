package com.zabibtech.alkhair.ui.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zabibtech.alkhair.data.manager.AuthRepoManager
import com.zabibtech.alkhair.data.manager.UserRepoManager
import com.zabibtech.alkhair.data.models.User
import com.zabibtech.alkhair.utils.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UserViewModel @Inject constructor(
    private val authRepoManager: AuthRepoManager,
    private val userRepoManager: UserRepoManager
) : ViewModel() {

    private val _userState = MutableStateFlow<UiState<User>>(UiState.Idle)
    val userState: StateFlow<UiState<User>> = _userState

    private val _userListState = MutableStateFlow<UiState<List<User>>>(UiState.Idle)
    val userListState: StateFlow<UiState<List<User>>> = _userListState

    fun createUser(user: User) {
        _userState.value = UiState.Loading
        viewModelScope.launch {
            authRepoManager.signup(user.email, user.password, user).fold(
                onSuccess = { savedUser ->
                    _userState.value = UiState.Success(savedUser)
                },
                onFailure = { e ->
                    _userState.value = UiState.Error(e.localizedMessage ?: "Create user failed")
                }
            )
        }
    }

    fun updateUser(user: User) {
        _userState.value = UiState.Loading
        viewModelScope.launch {
            userRepoManager.updateUser(user).fold(
                onSuccess = { updatedUser ->
                    _userState.value = UiState.Success(updatedUser)
                },
                onFailure = { e ->
                    _userState.value = UiState.Error(e.localizedMessage ?: "Update failed")
                }
            )
        }
    }

    fun getUser(uid: String) {
        _userState.value = UiState.Loading
        viewModelScope.launch {
            userRepoManager.getUserById(uid).fold(
                onSuccess = { user ->
                    if (user != null) {
                        _userState.value = UiState.Success(user)
                    } else {
                        _userState.value = UiState.Error("User not found")
                    }
                },
                onFailure = { e ->
                    _userState.value = UiState.Error(e.localizedMessage ?: "Fetch failed")
                }
            )
        }
    }

    fun deleteUser(uid: String) {
        _userState.value = UiState.Loading
        viewModelScope.launch {
            userRepoManager.deleteUser(uid).fold(
                onSuccess = {
                    if (authRepoManager.getCurrentUserUid() == uid) {
                        authRepoManager.logout()
                    }
                    _userState.value = UiState.Success(User()) // Dummy success
                },
                onFailure = { e ->
                    _userState.value = UiState.Error(e.localizedMessage ?: "Delete failed")
                }
            )
        }
    }

    fun loadUsers(role: String) {
        _userListState.value = UiState.Loading
        viewModelScope.launch {
            userRepoManager.getUsersByRole(role).fold(
                onSuccess = { users ->
                    _userListState.value = UiState.Success(users)
                },
                onFailure = { e ->
                    _userListState.value = UiState.Error(e.localizedMessage ?: "Failed to load users")
                }
            )
        }
    }

    suspend fun getCurrentUser(): User? {
        return userRepoManager.getCurrentUser().getOrNull()
    }
}
