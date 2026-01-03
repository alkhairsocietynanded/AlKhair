package com.zabibtech.alkhair.ui.user

import android.util.Log
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

    companion object {
        private const val TAG = "UserViewModel"
    }

    init {
        Log.d(TAG, "init: ViewModel created")
    }

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
        Log.d(TAG, "setInitialFilters: role=$role, classId=$classId, shift=$shift")
        _roleFilter.value = role
        _classIdFilter.value = classId
        _shiftFilter.value = shift
    }

    fun setShiftFilter(shift: String) {
        Log.d(TAG, "setShiftFilter: shift=$shift")
        _shiftFilter.value = shift
    }

    fun setSearchQuery(query: String) {
        Log.d(TAG, "setSearchQuery: query=$query")
        _searchQuery.value = query
    }

    /* ============================================================
       📦 LIST STATE (Fixed Reactive Pipeline)
       ============================================================ */

    @OptIn(ExperimentalCoroutinesApi::class)
    val userListState: StateFlow<UiState<List<User>>> =
        combine(
            // 1. Database Stream (Depends on Role)
            _roleFilter.flatMapLatest { role ->
                Log.d(TAG, "flatMapLatest: role changed to $role, observing users by role")
                userRepoManager.observeUsersByRole(role)
            },
            // 2. Filter States
            _shiftFilter,
            _classIdFilter,
            _searchQuery
        ) { users, shift, classId, query ->
            Log.d(TAG, "combine: Applying filters - shift=$shift, classId=$classId, query='$query' to ${users.size} users")
            // 3. Apply Filtering Logic
            filterUsers(users, shift, classId, query)
        }
            .map { filteredList ->
                Log.d(TAG, "map: Filtered list size: ${filteredList.size}")
                // 4. Convert to UiState
                UiState.Success(filteredList) as UiState<List<User>>
            }
            .onStart { emit(UiState.Loading) }
            .catch { e ->
                Log.e(TAG, "catch: Error in user list flow", e)
                emit(UiState.Error(e.message ?: "Failed to load users"))
            }
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
        val filtered = users.filter { user ->
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
        // Log.d(TAG, "filterUsers: ${users.size} -> ${filtered.size}") // Can be too verbose
        return filtered
    }

    /* ============================================================
       ✍️ MUTATIONS
       ============================================================ */

    private val _mutationState = MutableStateFlow<UiState<User>>(UiState.Idle)
    val mutationState: StateFlow<UiState<User>> = _mutationState

    fun createUser(user: User) {
        Log.d(TAG, "createUser: Attempting to create user: ${user.email}")
        _mutationState.value = UiState.Loading
        viewModelScope.launch {
            authRepoManager.signup(user.email, user.password, user).fold(
                onSuccess = {
                    Log.d(TAG, "createUser: Success for user: ${it.email}")
                    _mutationState.value = UiState.Success(it)
                },
                onFailure = { e ->
                    Log.e(TAG, "createUser: Failure for user: ${user.email}", e)
                    _mutationState.value = UiState.Error(e.message ?: "Signup failed")
                }
            )
        }
    }

    fun updateUser(user: User) {
        Log.d(TAG, "updateUser: Attempting to update user: ${user.uid}")
        _mutationState.value = UiState.Loading
        viewModelScope.launch {
            userRepoManager.updateUser(user).fold(
                onSuccess = {
                    Log.d(TAG, "updateUser: Success for user: ${it.uid}")
                    _mutationState.value = UiState.Success(it)
                },
                onFailure = { e ->
                    Log.e(TAG, "updateUser: Failure for user: ${user.uid}", e)
                    _mutationState.value = UiState.Error(e.message ?: "Update failed")
                }
            )
        }
    }

    fun deleteUser(uid: String) {
        Log.d(TAG, "deleteUser: Attempting to delete user: $uid")
        _mutationState.value = UiState.Loading
        viewModelScope.launch {
            userRepoManager.deleteUser(uid).fold(
                onSuccess = {
                    Log.d(TAG, "deleteUser: Success for user: $uid")
                    if (authRepoManager.getCurrentUserUid() == uid) {
                        Log.d(TAG, "deleteUser: Deleted user was current user, logging out.")
                        authRepoManager.logout()
                    }
                    _mutationState.value = UiState.Success(User()) // Using empty user as success signal
                },
                onFailure = { e ->
                    Log.e(TAG, "deleteUser: Failure for user: $uid", e)
                    _mutationState.value = UiState.Error(e.message ?: "Delete failed")
                }
            )
        }
    }
    suspend fun getCurrentUser(): User? {
        val uid = authRepoManager.getCurrentUserUid() ?: return null
        // Repo Manager me already 'getUserById' hai jo suspend function hai
        return userRepoManager.getUserById(uid)
    }
    fun resetMutationState() {
        Log.d(TAG, "resetMutationState: Resetting mutation state to Idle.")
        _mutationState.value = UiState.Idle
    }
}
