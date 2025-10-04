package com.zabibtech.alkhair.ui.user.fragments.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zabibtech.alkhair.data.models.User
import com.zabibtech.alkhair.data.repository.UserRepository
import com.zabibtech.alkhair.utils.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repository: UserRepository
) : ViewModel() {

    private val _userDetail = MutableLiveData<UiState<User>>()
    val userDetail: LiveData<UiState<User>> get() = _userDetail

    fun getUserDetails(userId: String) {
        viewModelScope.launch {
            _userDetail.value = UiState.Loading
            try {
                val user = repository.getUserById(userId)
                user?.let {
                    _userDetail.value = UiState.Success(it)
                } ?: run {
                    _userDetail.value = UiState.Error("User not found")
                }
            } catch (e: Exception) {
                _userDetail.value = UiState.Error(e.message ?: "Unknown Error")
            }
        }
    }
}