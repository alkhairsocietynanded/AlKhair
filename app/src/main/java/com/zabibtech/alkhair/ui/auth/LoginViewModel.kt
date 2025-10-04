package com.zabibtech.alkhair.ui.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zabibtech.alkhair.data.datastore.UserStore
import com.zabibtech.alkhair.data.models.User
import com.zabibtech.alkhair.data.repository.AuthRepository
import com.zabibtech.alkhair.data.repository.UserRepository
import com.zabibtech.alkhair.utils.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepo: AuthRepository,
    private val userRepo: UserRepository,
    private val userStore: UserStore,
) : ViewModel() {

    private val _state = MutableLiveData<UiState<User>>(UiState.Idle)
    val state: LiveData<UiState<User>> = _state

    // 🔹 Login
    fun login(email: String, password: String) {
        _state.value = UiState.Loading
        viewModelScope.launch {
            try {
                val uid = authRepo.login(email, password)
                val user = userRepo.getUserById(uid)
                    ?: throw Exception("User record not found in database")
                userStore.saveUser(user)

                _state.value = UiState.Success(user)
            } catch (e: Exception) {
                _state.value = UiState.Error(e.localizedMessage ?: "Login failed")
            }
        }
    }

    // 🔹 Signup
    fun signup(email: String, password: String, user: User) {
        _state.value = UiState.Loading
        viewModelScope.launch {
            try {
                val uid = authRepo.signup(email, password)
                val finalUser = user.copy(uid = uid)
                val savedUser = userRepo.createUser(finalUser)
                userStore.saveUser(savedUser)

                _state.value = UiState.Success(savedUser)
            } catch (e: Exception) {
                _state.value = UiState.Error(e.localizedMessage ?: "Signup failed")
            }
        }
    }
}
