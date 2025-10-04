package com.zabibtech.alkhair.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zabibtech.alkhair.data.datastore.ClassDivisionStore
import com.zabibtech.alkhair.data.datastore.UserStore
import com.zabibtech.alkhair.data.models.ClassModel
import com.zabibtech.alkhair.data.models.DivisionModel
import com.zabibtech.alkhair.data.models.User
import com.zabibtech.alkhair.data.repository.AuthRepository
import com.zabibtech.alkhair.data.repository.UserRepository
import com.zabibtech.alkhair.utils.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val authRepo: AuthRepository,
    private val userRepo: UserRepository,
    private val sessionManager: UserStore,
    private val classDivisionStore: ClassDivisionStore
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<User?>>(UiState.Idle)
    val state: StateFlow<UiState<User?>> = _state

    private val _divisions = MutableStateFlow<List<DivisionModel>>(emptyList())
    val divisions: StateFlow<List<DivisionModel>> = _divisions

    private val _classes = MutableStateFlow<List<ClassModel>>(emptyList())
    val classes: StateFlow<List<ClassModel>> = _classes

    // ================================
    // User session management
    // ================================
    fun checkUser() {
        _state.value = UiState.Loading
        viewModelScope.launch {
            try {
                val uid = authRepo.currentUserUid()
                if (uid == null) {
                    _state.value = UiState.Success(null)
                } else {
                    val user = userRepo.getUserById(uid)
                    if (user != null) {
                        sessionManager.saveUser(user)
                    }
                    _state.value = UiState.Success(user)
                }
            } catch (e: Exception) {
                val localUser = sessionManager.getUser()
                if (localUser != null) {
                    _state.value = UiState.Success(localUser)
                } else {
                    _state.value = UiState.Error(e.localizedMessage ?: "Failed to fetch user")
                }
            }
        }
    }

    // ================================
    // Divisions only
    // ================================
    fun loadDivisions() {
        viewModelScope.launch {
            val list = classDivisionStore.getOrFetchDivisionList()
            _divisions.value = list
        }
    }

    // ================================
    // Classes only
    // ================================
    fun loadClasses() {
        viewModelScope.launch {
            val list = classDivisionStore.getOrFetchClassList()
            _classes.value = list
        }
    }

    // ================================
    // Combined: load both divisions + classes
    // ================================
    fun loadDivisionsAndClasses() {
        viewModelScope.launch {
            val divisionsList = classDivisionStore.getOrFetchDivisionList()
            val classesList = classDivisionStore.getOrFetchClassList()

            _divisions.value = divisionsList
            _classes.value = classesList
        }
    }
}
