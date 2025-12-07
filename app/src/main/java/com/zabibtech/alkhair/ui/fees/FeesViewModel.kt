package com.zabibtech.alkhair.ui.fees

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zabibtech.alkhair.data.manager.FeesRepoManager
import com.zabibtech.alkhair.data.manager.UserRepoManager
import com.zabibtech.alkhair.data.models.FeesModel
import com.zabibtech.alkhair.data.models.FeesOverviewData
import com.zabibtech.alkhair.utils.Roles
import com.zabibtech.alkhair.utils.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FeesViewModel @Inject constructor(
    private val feesRepoManager: FeesRepoManager,
    private val userRepoManager: UserRepoManager
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
            feesRepoManager.getFeesForStudent(studentId).fold(
                onSuccess = { feesList ->
                    _feesModelListState.value = UiState.Success(feesList)
                },
                onFailure = { error ->
                    _feesModelListState.value =
                        UiState.Error(error.localizedMessage ?: "Failed to load fees")
                }
            )
        }
    }

    fun saveFee(feesModel: FeesModel) {
        _feesModelState.value = UiState.Loading
        viewModelScope.launch {
            feesRepoManager.saveFee(feesModel).fold( // feeRepo.addOrUpdateFee se change kiya
                onSuccess = { newFeesModel -> // New: feesRepoManager.saveFee now returns FeesModel on success
                    _feesModelState.value = UiState.Success(Unit)
                    // Optionally, you might want to reload fees if this impacts displayed data
                },
                onFailure = { error ->
                    _feesModelState.value = UiState.Error(error.localizedMessage ?: "Failed to save feesModel")
                }
            )
        }
    }

    fun deleteFee(feeId: String) {
        _feesModelState.value = UiState.Loading
        viewModelScope.launch {
            feesRepoManager.deleteFee(feeId).fold(
                onSuccess = {
                    _feesModelState.value = UiState.Success(Unit)
                },
                onFailure = { error ->
                    _feesModelState.value = UiState.Error(error.localizedMessage ?: "Failed to delete fee")
                }
            )
        }
    }

    fun resetFeeState() {
        _feesModelState.value = UiState.Idle
    }

    fun loadFeesOverviewForMonth(monthYear: String) {
        _feesOverviewState.value = UiState.Loading
        viewModelScope.launch {
            userRepoManager.getUsersByRole(Roles.STUDENT).fold(
                onSuccess = { allStudents ->
                    feesRepoManager.getFeesForMonthYear(monthYear).fold(
                        onSuccess = { feesForMonth ->
                            val totalStudents = allStudents.size

                            val feesByStudentId = feesForMonth.groupBy { it.studentId }

                            val unpaidStudentsCount = allStudents.count { student ->
                                val studentFees = feesByStudentId[student.uid]

                                if (studentFees.isNullOrEmpty()) {
                                    true
                                } else {
                                    val totalDueForStudent = studentFees.sumOf { it.baseAmount } -
                                            studentFees.sumOf { it.paidAmount } -
                                            studentFees.sumOf { it.discounts }
                                    totalDueForStudent > 0
                                }
                            }

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
                                unpaidCount = unpaidStudentsCount,
                                classWiseCollected = collectedByClass
                            )

                            _feesOverviewState.value = UiState.Success(overview)
                        },
                        onFailure = { error ->
                            _feesOverviewState.value = UiState.Error(error.localizedMessage ?: "Failed to load fees overview")
                        }
                    )
                },
                onFailure = { error ->
                    _feesOverviewState.value = UiState.Error(error.localizedMessage ?: "Failed to load students")
                }
            )
        }
    }
}
