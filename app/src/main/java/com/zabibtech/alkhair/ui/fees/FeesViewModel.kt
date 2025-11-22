package com.zabibtech.alkhair.ui.fees

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zabibtech.alkhair.data.models.FeesModel
import com.zabibtech.alkhair.data.models.FeesOverviewData
import com.zabibtech.alkhair.data.repository.FeesRepository
import com.zabibtech.alkhair.data.repository.UserRepository
import com.zabibtech.alkhair.utils.Roles
import com.zabibtech.alkhair.utils.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FeesViewModel @Inject constructor(
    private val feeRepo: FeesRepository,
    private val userRepo: UserRepository
) : ViewModel() {

    private val _feesModelListState = MutableStateFlow<UiState<List<FeesModel>>>(UiState.Idle)
    val feesModelListState: StateFlow<UiState<List<FeesModel>>> = _feesModelListState

    private val _feesModelState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val feeState: StateFlow<UiState<Unit>> = _feesModelState

    private val _feesOverviewState = MutableStateFlow<UiState<FeesOverviewData>>(UiState.Idle)
    val feesOverviewState: StateFlow<UiState<FeesOverviewData>> = _feesOverviewState

    fun loadFeesByStudent(studentId: String) {
        _feesModelListState.value = UiState.Loading
        viewModelScope.launch {
            try {
                val feesList = feeRepo.getFeesByStudent(studentId)
                _feesModelListState.value = UiState.Success(feesList)
            } catch (e: Exception) {
                _feesModelListState.value =
                    UiState.Error(e.localizedMessage ?: "Failed to load fees")
            }
        }
    }

    fun saveFee(feesModel: FeesModel) {
        _feesModelState.value = UiState.Loading
        viewModelScope.launch {
            try {
                feeRepo.addOrUpdateFee(feesModel)
                _feesModelState.value = UiState.Success(Unit)
            } catch (e: Exception) {
                _feesModelState.value =
                    UiState.Error(e.localizedMessage ?: "Failed to save feesModel")
            }
        }
    }

    fun deleteFee(feeId: String) {
        _feesModelState.value = UiState.Loading
        viewModelScope.launch {
            try {
                feeRepo.deleteFee(feeId)
                _feesModelState.value = UiState.Success(Unit)
            } catch (e: Exception) {
                _feesModelState.value = UiState.Error(e.localizedMessage ?: "Failed to delete fee")
            }
        }
    }

    fun resetFeeState() {
        _feesModelState.value = UiState.Idle
    }

    fun loadFeesOverviewForMonth(monthYear: String) {
        _feesOverviewState.value = UiState.Loading
        viewModelScope.launch {
            try {
                // Step 1: Sabhi students aur uss mahine ki fees fetch karein
                val allStudents = userRepo.getUsers(Roles.STUDENT)
                val feesForMonth = feeRepo.getFeesByMonthYear(monthYear)

                val totalStudents = allStudents.size

                // Step 2: Fees ko studentId se group karein taaki har student ki total fees aasani se calculate ho sake
                val feesByStudentId = feesForMonth.groupBy { it.studentId }

                // Step 3: Unpaid students ko aache se count karein
                val unpaidStudentsCount = allStudents.count { student ->
                    val studentFees = feesByStudentId[student.uid]

                    if (studentFees.isNullOrEmpty()) {
                        // Case 1: Agar student ka koi fee record nahi hai, toh woh unpaid hai.
                        true
                    } else {
                        // Case 2: Agar fee record hai, toh total due amount calculate karein.
                        val totalDueForStudent = studentFees.sumOf { it.baseAmount } -
                                studentFees.sumOf { it.paidAmount } -
                                studentFees.sumOf { it.discounts }
                        // Agar due amount 0 se zyada hai, toh student unpaid hai.
                        totalDueForStudent > 0
                    }
                }

                // Baaki ka calculation pehle jaisa hi rahega
                val totalFees = feesForMonth.sumOf { it.baseAmount }
                val totalCollected = feesForMonth.sumOf { it.paidAmount }
                val totalDiscount = feesForMonth.sumOf { it.discounts }
                val totalDue = totalFees - totalCollected - totalDiscount

                val collectedByClass = allStudents
                    .filter { it.className.isNotBlank() }
                    .groupBy { it.className }
                    .mapValues { (_, students) ->
                        val studentIds = students.map { it.uid }
                        feesForMonth
                            .filter { it.studentId in studentIds }
                            .sumOf { it.paidAmount }
                    }

                val overview = FeesOverviewData(
                    totalStudents = totalStudents,
                    totalFees = totalFees,
                    totalCollected = totalCollected,
                    totalDiscount = totalDiscount,
                    totalDue = totalDue,
                    unpaidCount = unpaidStudentsCount, // Yahan updated count use hoga
                    classWiseCollected = collectedByClass
                )

                _feesOverviewState.value = UiState.Success(overview)

            } catch (e: Exception) {
                _feesOverviewState.value = UiState.Error(e.message ?: "Failed to load data")
            }
        }
    }
}