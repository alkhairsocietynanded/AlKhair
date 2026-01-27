package com.zabibtech.alkhair.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zabibtech.alkhair.data.manager.AuthRepoManager
import com.zabibtech.alkhair.data.models.User
import com.zabibtech.alkhair.utils.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepoManager: AuthRepoManager
) : ViewModel() {

    private val _loginState = MutableStateFlow<UiState<User>>(UiState.Idle)
    val loginState: StateFlow<UiState<User>> = _loginState

    fun login(email: String, password: String) {
        _loginState.value = UiState.Loading
        viewModelScope.launch {
            authRepoManager.login(email, password).fold(
                onSuccess = { user ->
                    _loginState.value = UiState.Success(user)
                },
                onFailure = { e ->
                    _loginState.value = UiState.Error(e.message ?: "Login failed")
                }
            )
        }
    }

    fun signup(user: User) {
        _loginState.value = UiState.Loading
        viewModelScope.launch {
            authRepoManager.signup(user).fold(
                onSuccess = { savedUser ->
                    _loginState.value = UiState.Success(savedUser)
                },
                onFailure = { e ->
                    _loginState.value = UiState.Error(e.message ?: "Signup failed")
                }
            )
        }
    }

    fun resetState() {
        _loginState.value = UiState.Idle
    }
}