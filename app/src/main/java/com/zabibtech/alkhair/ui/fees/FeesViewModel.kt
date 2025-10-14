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

    // ✅ FeesModel list load karne ke liye
    private val _feesModelListState = MutableStateFlow<UiState<List<FeesModel>>>(UiState.Idle)
    val feesModelListState: StateFlow<UiState<List<FeesModel>>> = _feesModelListState

    // ✅ FeesModel save / delete ke liye
    private val _feesModelState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val feeState: StateFlow<UiState<Unit>> = _feesModelState

    private val _feesOverviewState = MutableStateFlow<UiState<FeesOverviewData>>(UiState.Idle)
    val feesOverviewState: StateFlow<UiState<FeesOverviewData>> = _feesOverviewState


    // ================================
    // Load all fees by studentId
    // ================================
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

    // ================================
    // Add or update feesModel
    // ================================
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

    // ================================
    // Delete fee record
    // ================================
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

    fun loadAllFeesOverview() {
        _feesOverviewState.value = UiState.Loading
        viewModelScope.launch {
            try {
                // ✅ 1. Get all students
                val allStudents = userRepo.getUsers(Roles.STUDENT)
                val totalStudents = allStudents.size

                // ✅ 2. Get all fees records
                val allFees = feeRepo.getAllFees()

                // ✅ 3. Calculate totals from existing fee records
                val totalFees = allFees.sumOf { it.totalAmount }
                val totalCollected = allFees.sumOf { it.paidAmount }
                val totalDue = allFees.sumOf { it.dueAmount }

                // ✅ 4. Identify unpaid students
                val unpaidStudentsCount = allStudents.count { student ->
                    val studentFees = allFees.filter { it.studentId == student.uid }
                    if (studentFees.isEmpty()) {
                        true // no fee record = unpaid
                    } else {
                        studentFees.all { it.status == "Unpaid" || it.status.isBlank() }
                    }
                }

                // ✅ 5. Compute collected fees by class
                val collectedByClass = allStudents
                    .groupBy { it.className } // assumes userRepo model has className
                    .mapValues { (className, students) ->
                        val studentIds = students.map { it.uid }
                        allFees
                            .filter { it.studentId in studentIds }
                            .sumOf { it.paidAmount }
                    }

                // ✅ 6. Create overview data including classWiseCollected
                val overview = FeesOverviewData(
                    totalStudents = totalStudents,
                    totalFees = totalFees,
                    totalCollected = totalCollected,
                    totalDue = totalDue,
                    unpaidCount = unpaidStudentsCount,
                    classWiseCollected = collectedByClass
                )

                _feesOverviewState.value = UiState.Success(overview)

            } catch (e: Exception) {
                _feesOverviewState.value = UiState.Error(e.message ?: "Failed to load data")
            }
        }
    }
}

