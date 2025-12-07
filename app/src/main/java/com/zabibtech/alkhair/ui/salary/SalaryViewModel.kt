package com.zabibtech.alkhair.ui.salary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zabibtech.alkhair.data.manager.SalaryRepoManager
import com.zabibtech.alkhair.data.models.SalaryModel
import com.zabibtech.alkhair.utils.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SalaryViewModel @Inject constructor(
    private val salaryRepoManager: SalaryRepoManager // Injected SalaryRepoManager
) : ViewModel() {

    private val _salaryListState = MutableStateFlow<UiState<List<SalaryModel>>>(UiState.Idle)
    val salaryListState: StateFlow<UiState<List<SalaryModel>>> = _salaryListState

    private val _salaryMutationState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val salaryMutationState: StateFlow<UiState<Unit>> = _salaryMutationState

    private val _summaryState =
        MutableStateFlow<UiState<SalaryRepoManager.MonthlySummary>>(UiState.Idle) // Updated type
    val summaryState: StateFlow<UiState<SalaryRepoManager.MonthlySummary>> = _summaryState

    private val _chartDataState =
        MutableStateFlow<UiState<Map<String, Double>>>(UiState.Idle)
    val chartDataState: StateFlow<UiState<Map<String, Double>>> = _chartDataState

    fun loadSalaries(staffId: String? = null, monthYear: String? = null) {
        _salaryListState.value = UiState.Loading
        viewModelScope.launch {
            salaryRepoManager.getSalaries(staffId, monthYear).fold(
                onSuccess = { salaries ->
                    _salaryListState.value = UiState.Success(salaries)
                },
                onFailure = { error ->
                    _salaryListState.value =
                        UiState.Error(error.localizedMessage ?: "Failed to load salary list")
                }
            )
        }
    }

    fun saveSalary(salary: SalaryModel) {
        _salaryMutationState.value = UiState.Loading
        viewModelScope.launch {
            val result = if (salary.id.isBlank()) {
                salaryRepoManager.createSalary(salary)
            } else {
                // For update, we pass the whole object for simplicity as per manager's new logic
                salaryRepoManager.updateSalary(salary)
            }

            result.fold(
                onSuccess = { _ -> _salaryMutationState.value = UiState.Success(Unit) },
                onFailure = { error ->
                    _salaryMutationState.value =
                        UiState.Error(error.localizedMessage ?: "Failed to save salary")
                }
            )
        }
    }

    fun deleteSalary(salaryId: String) {
        _salaryMutationState.value = UiState.Loading
        viewModelScope.launch {
            salaryRepoManager.deleteSalary(salaryId).fold(
                onSuccess = { _ -> _salaryMutationState.value = UiState.Success(Unit) },
                onFailure = { error ->
                    _salaryMutationState.value =
                        UiState.Error(error.localizedMessage ?: "Failed to delete salary")
                }
            )
        }
    }

    fun loadMonthlySummary(monthYear: String? = null) {
        _summaryState.value = UiState.Loading
        viewModelScope.launch {
            salaryRepoManager.getMonthlySummary(monthYear).fold(
                onSuccess = { summary -> _summaryState.value = UiState.Success(summary) },
                onFailure = { error ->
                    _summaryState.value =
                        UiState.Error(error.localizedMessage ?: "Failed to load monthly summary")
                }
            )
        }
    }

    fun loadStaffSummary(staffId: String, monthYear: String? = null) {
        _summaryState.value = UiState.Loading
        viewModelScope.launch {
            salaryRepoManager.getStaffSummary(staffId, monthYear).fold(
                onSuccess = { summary -> _summaryState.value = UiState.Success(summary) },
                onFailure = { error ->
                    _summaryState.value =
                        UiState.Error(error.localizedMessage ?: "Failed to load staff summary")
                }
            )
        }
    }

    fun loadSalaryChartData(staffId: String? = null, monthYear: String? = null) {
        viewModelScope.launch {
            salaryRepoManager.getSalaries(staffId, monthYear).fold(
                onSuccess = { salaries ->
                    val grouped = salaries.groupBy { it.monthYear }.mapValues { entry ->
                        entry.value.sumOf { it.netSalary }
                    }
                    _chartDataState.value = UiState.Success(grouped)
                },
                onFailure = { error ->
                    _chartDataState.value = UiState.Error(error.localizedMessage ?: "Error loading chart")
                }
            )
        }
    }

    fun resetMutationState() {
        _salaryMutationState.value = UiState.Idle
    }

    fun resetSummaryState() {
        _summaryState.value = UiState.Idle
    }
}