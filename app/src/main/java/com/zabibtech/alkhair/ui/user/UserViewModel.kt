package com.zabibtech.alkhair.ui.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zabibtech.alkhair.data.manager.AuthRepoManager
import com.zabibtech.alkhair.data.manager.UserRepoManager
import com.zabibtech.alkhair.data.models.User
import com.zabibtech.alkhair.utils.Roles
import com.zabibtech.alkhair.utils.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UserViewModel @Inject constructor(
    private val authRepoManager: AuthRepoManager,
    private val userRepoManager: UserRepoManager
) : ViewModel() {

    /* ============================================================
       🔹 FILTER STATES
       ============================================================ */

    private val _roleFilter = MutableStateFlow(Roles.STUDENT)
    private val _shiftFilter = MutableStateFlow("All")
    private val _classIdFilter = MutableStateFlow<String?>(null)
    private val _searchQuery = MutableStateFlow("")

    /* ============================================================
       🔹 SETTERS
       ============================================================ */

    fun setInitialFilters(role: String, classId: String?, shift: String) {
        _roleFilter.value = role
        _classIdFilter.value = classId
        _shiftFilter.value = shift
    }

    fun setShiftFilter(shift: String) {
        _shiftFilter.value = shift
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /* ============================================================
       📦 LIST STATE (Fixed Reactive Pipeline)
       ============================================================ */

    @OptIn(ExperimentalCoroutinesApi::class)
    val userListState: StateFlow<UiState<List<User>>> =
        combine(
            // 1. Database Stream (Depends on Role)
            _roleFilter.flatMapLatest { role -> userRepoManager.observeUsersByRole(role) },
            // 2. Filter States
            _shiftFilter,
            _classIdFilter,
            _searchQuery
        ) { users, shift, classId, query ->
            // 3. Apply Filtering Logic
            filterUsers(users, shift, classId, query)
        }
            .map { filteredList ->
                // 4. Convert to UiState
                UiState.Success(filteredList) as UiState<List<User>>
            }
            .onStart { emit(UiState.Loading) }
            .catch { emit(UiState.Error(it.message ?: "Failed to load users")) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                UiState.Idle
            )

    /* ============================================================
       🕵️ INTERNAL FILTER LOGIC (Improved)
       ============================================================ */

    private fun filterUsers(
        users: List<User>,
        shift: String,
        classId: String?,
        query: String
    ): List<User> {
        return users.filter { user ->
            // 1. Match Shift
            val matchesShift = shift == "All" || user.shift.equals(shift, ignoreCase = true)

            // 2. Match Class
            val matchesClass = classId == null || user.classId == classId

            // 3. Match Search Query (Name, Email, or Phone)
            val matchesQuery = query.isEmpty() ||
                    user.name.contains(query, ignoreCase = true) ||
                    user.email.contains(query, ignoreCase = true) ||
                    user.phone.contains(query, ignoreCase = true)

            matchesShift && matchesClass && matchesQuery
        }
    }

    /* ============================================================
       ✍️ MUTATIONS
       ============================================================ */

    private val _mutationState = MutableStateFlow<UiState<User>>(UiState.Idle)
    val mutationState: StateFlow<UiState<User>> = _mutationState

    fun createUser(user: User) {
        _mutationState.value = UiState.Loading
        viewModelScope.launch {
            authRepoManager.signup(user.email, user.password, user).fold(
                onSuccess = { _mutationState.value = UiState.Success(it) },
                onFailure = { _mutationState.value = UiState.Error(it.message ?: "Signup failed") }
            )
        }
    }

    fun updateUser(user: User) {
        _mutationState.value = UiState.Loading
        viewModelScope.launch {
            userRepoManager.updateUser(user).fold(
                onSuccess = { _mutationState.value = UiState.Success(it) },
                onFailure = { _mutationState.value = UiState.Error(it.message ?: "Update failed") }
            )
        }
    }

    fun deleteUser(uid: String) {
        _mutationState.value = UiState.Loading
        viewModelScope.launch {
            userRepoManager.deleteUser(uid).fold(
                onSuccess = {
                    if (authRepoManager.getCurrentUserUid() == uid) {
                        authRepoManager.logout()
                    }
                    _mutationState.value = UiState.Success(User())
                },
                onFailure = { _mutationState.value = UiState.Error(it.message ?: "Delete failed") }
            )
        }
    }

    fun resetMutationState() {
        _mutationState.value = UiState.Idle
    }
}