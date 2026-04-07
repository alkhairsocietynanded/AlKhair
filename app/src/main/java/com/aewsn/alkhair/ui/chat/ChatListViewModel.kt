package com.aewsn.alkhair.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aewsn.alkhair.data.manager.AuthRepoManager
import com.aewsn.alkhair.data.manager.ClassDivisionRepoManager
import com.aewsn.alkhair.data.manager.UserRepoManager
import com.aewsn.alkhair.utils.Roles
import com.aewsn.alkhair.utils.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val authRepoManager: AuthRepoManager,
    private val userRepoManager: UserRepoManager,
    private val classDivisionRepoManager: ClassDivisionRepoManager
) : ViewModel() {

    private val _groupsState = MutableStateFlow<UiState<List<ChatGroup>>>(UiState.Loading)
    val groupsState: StateFlow<UiState<List<ChatGroup>>> = _groupsState

    // Current user info for UI
    var currentUserRole: String = Roles.STUDENT
        private set
    var currentUserName: String = ""
        private set
    var currentUserClassId: String? = null
        private set

    init {
        loadGroups()
    }

    fun loadGroups() {
        viewModelScope.launch {
            _groupsState.value = UiState.Loading
            try {
                // Try Supabase Auth first, fallback to our offline DataStore
                var uid = authRepoManager.getCurrentUserUid()
                if (uid == null) {
                    uid = authRepoManager.getLocalLoginUid()
                }
                
                if (uid == null) {
                    _groupsState.value = UiState.Error("User not logged in")
                    return@launch
                }

                val user = userRepoManager.getUserById(uid)
                if (user == null) {
                    _groupsState.value = UiState.Error("User data not found offline")
                    return@launch
                }

                currentUserRole = user.role.trim()
                currentUserName = user.name
                currentUserClassId = user.classId

                val groups = mutableListOf<ChatGroup>()

                when {
                    // ADMIN: Teachers Group + All Class Groups
                    currentUserRole.equals(Roles.ADMIN, true) -> {
                        groups.add(
                            ChatGroup(
                                groupId = "teachers_group",
                                groupType = "teachers",
                                groupName = "Teachers Group",
                                subtitle = "Admin + All Teachers"
                            )
                        )

                        // Get all classes from local DB
                        val classesResult = classDivisionRepoManager.getAllClassesSnapshot()
                        classesResult.onSuccess { classes ->
                            classes.forEach { classModel ->
                                groups.add(
                                    ChatGroup(
                                        groupId = classModel.id,
                                        groupType = "class",
                                        groupName = classModel.className,
                                        subtitle = "Class Chat"
                                    )
                                )
                            }
                        }
                    }

                    // TEACHER: Teachers Group + My Class Group
                    currentUserRole.equals(Roles.TEACHER, true) -> {
                        groups.add(
                            ChatGroup(
                                groupId = "teachers_group",
                                groupType = "teachers",
                                groupName = "Teachers Group",
                                subtitle = "Admin + Teachers"
                            )
                        )

                        if (!user.classId.isNullOrBlank()) {
                            // Use className from User model (already hydrated)
                            groups.add(
                                ChatGroup(
                                    groupId = user.classId!!,
                                    groupType = "class",
                                    groupName = user.className.ifEmpty { "My Class" },
                                    subtitle = "Class Chat"
                                )
                            )
                        }
                    }

                    // STUDENT: Only My Class Group (will be redirected directly)
                    else -> {
                        if (!user.classId.isNullOrBlank()) {
                            groups.add(
                                ChatGroup(
                                    groupId = user.classId!!,
                                    groupType = "class",
                                    groupName = user.className.ifEmpty { "My Class" },
                                    subtitle = "Class Chat"
                                )
                            )
                        }
                    }
                }

                _groupsState.value = UiState.Success(groups)

            } catch (e: Exception) {
                _groupsState.value = UiState.Error(e.message ?: "Failed to load groups")
            }
        }
    }
}
