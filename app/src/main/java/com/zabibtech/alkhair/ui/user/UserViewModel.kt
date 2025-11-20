package com.zabibtech.alkhair.ui.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zabibtech.alkhair.data.datastore.UserStore
import com.zabibtech.alkhair.data.models.User
import com.zabibtech.alkhair.data.repository.AttendanceRepository
import com.zabibtech.alkhair.data.repository.AuthRepository
import com.zabibtech.alkhair.data.repository.UserRepository
import com.zabibtech.alkhair.utils.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UserViewModel @Inject constructor(
    private val authRepo: AuthRepository,
    private val userRepo: UserRepository,
    private val attendanceRepo: AttendanceRepository,
    private val userStore: UserStore
) : ViewModel() {

    private val _userState = MutableStateFlow<UiState<User>>(UiState.Idle)
    val userState: StateFlow<UiState<User>> = _userState

    private val _userListState = MutableStateFlow<UiState<List<User>>>(UiState.Idle)
    val userListState: StateFlow<UiState<List<User>>> = _userListState

    // ================================
    // Create user (only DB, not local)
    // ================================
    fun createUser(user: User) {
        _userState.value = UiState.Loading
        viewModelScope.launch {
            try {
                val uid = authRepo.signup(user.email, user.password)
                val finalUser = user.copy(uid = uid)
                val savedUser = userRepo.createUser(finalUser)
                _userState.value = UiState.Success(savedUser)
            } catch (e: Exception) {
                _userState.value = UiState.Error(e.localizedMessage ?: "Create failed")
            }
        }
    }

    // ================================
    // Update user (only DB, unless current user)
    // ================================
    fun updateUser(user: User) {
        _userState.value = UiState.Loading
        viewModelScope.launch {
            try {
                val updatedUser = userRepo.updateUser(user)

                // Cache only if current logged-in user
                val current = userStore.getUser()
                if (current?.uid == updatedUser.uid) {
                    userStore.saveUser(updatedUser)
                }

                _userState.value = UiState.Success(updatedUser)
            } catch (e: Exception) {
                _userState.value = UiState.Error(e.localizedMessage ?: "Update failed")
            }
        }
    }

    // ================================
    // Get single user
    // ================================
    fun getUser(uid: String) {
        _userState.value = UiState.Loading
        viewModelScope.launch {
            try {
                val user = userRepo.getUserById(uid)
                if (user != null) _userState.value = UiState.Success(user)
                else _userState.value = UiState.Error("User not found")
            } catch (e: Exception) {
                _userState.value = UiState.Error(e.localizedMessage ?: "Fetch failed")
            }
        }
    }

    // ================================
    // Delete user
    // ================================
    fun deleteUser(uid: String) {
        _userState.value = UiState.Loading
        viewModelScope.launch {
            try {
                userRepo.deleteUser(uid)
                val current = userStore.getUser()
                if (current?.uid == uid) userStore.clearUser() // Clear only if current
                _userState.value = UiState.Success(User()) // dummy
            } catch (e: Exception) {
                _userState.value = UiState.Error(e.localizedMessage ?: "Delete failed")
            }
        }
    }

    // ================================
    // Load all users by role
    // ================================
    fun loadUsers(role: String) {
        _userListState.value = UiState.Loading
        viewModelScope.launch {
            try {
                val users = userRepo.getUsers(role)
                _userListState.value = UiState.Success(users)
            } catch (e: Exception) {
                _userListState.value = UiState.Error(e.localizedMessage ?: "Failed to load users")
            }
        }
    }

    // ================================
    // Only for currently logged-in user
    // ================================
    fun saveCurrentUser(user: User) {
        viewModelScope.launch { userStore.saveUser(user) }
    }

    suspend fun getCurrentUser(): User? = runCatching { userStore.getUser() }.getOrNull()
}

