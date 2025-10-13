package com.zabibtech.alkhair.ui.fees

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zabibtech.alkhair.data.models.Fee
import com.zabibtech.alkhair.data.repository.FeeRepository
import com.zabibtech.alkhair.utils.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FeeViewModel @Inject constructor(
    private val feeRepo: FeeRepository
) : ViewModel() {

    // ✅ Fee list load karne ke liye
    private val _feeListState = MutableStateFlow<UiState<List<Fee>>>(UiState.Idle)
    val feeListState: StateFlow<UiState<List<Fee>>> = _feeListState

    // ✅ Fee save / delete ke liye
    private val _feeState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val feeState: StateFlow<UiState<Unit>> = _feeState


    // ================================
    // Load all fees by studentId
    // ================================
    fun loadFeesByStudent(studentId: String) {
        _feeListState.value = UiState.Loading
        viewModelScope.launch {
            try {
                val feesList = feeRepo.getFeesByStudent(studentId)
                _feeListState.value = UiState.Success(feesList)
            } catch (e: Exception) {
                _feeListState.value = UiState.Error(e.localizedMessage ?: "Failed to load fees")
            }
        }
    }

    // ================================
    // Add or update fee
    // ================================
    fun saveFee(fee: Fee) {
        _feeState.value = UiState.Loading
        viewModelScope.launch {
            try {
                feeRepo.addOrUpdateFee(fee)
                _feeState.value = UiState.Success(Unit)
            } catch (e: Exception) {
                _feeState.value = UiState.Error(e.localizedMessage ?: "Failed to save fee")
            }
        }
    }

    // ================================
    // Delete fee record
    // ================================
    fun deleteFee(feeId: String) {
        _feeState.value = UiState.Loading
        viewModelScope.launch {
            try {
                feeRepo.deleteFee(feeId)
                _feeState.value = UiState.Success(Unit)
            } catch (e: Exception) {
                _feeState.value = UiState.Error(e.localizedMessage ?: "Failed to delete fee")
            }
        }
    }
}
