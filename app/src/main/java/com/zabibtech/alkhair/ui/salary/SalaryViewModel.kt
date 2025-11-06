package com.zabibtech.alkhair.ui.salary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zabibtech.alkhair.data.models.SalaryModel
import com.zabibtech.alkhair.data.repository.SalaryRepository
import com.zabibtech.alkhair.utils.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SalaryViewModel @Inject constructor(
    private val salaryRepo: SalaryRepository
) : ViewModel() {

    private val _salaryListState = MutableStateFlow<UiState<List<SalaryModel>>>(UiState.Idle)
    val salaryListState: StateFlow<UiState<List<SalaryModel>>> = _salaryListState

    private val _salaryMutationState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val salaryMutationState: StateFlow<UiState<Unit>> = _salaryMutationState

    private val _summaryState =
        MutableStateFlow<UiState<SalaryRepository.MonthlySummary>>(UiState.Idle)
    val summaryState: StateFlow<UiState<SalaryRepository.MonthlySummary>> = _summaryState

    private val _chartDataState =
        MutableStateFlow<UiState<Map<String, Double>>>(UiState.Idle)
    val chartDataState: StateFlow<UiState<Map<String, Double>>> = _chartDataState

    // ✅ FIX: Add Loading state before filtering
    fun loadFilteredSalaries(staffId: String?, monthYear: String?) {
        _salaryListState.value = UiState.Loading
        viewModelScope.launch {
            try {
                val allSalaries = salaryRepo.getAllSalaries()

                val filtered = allSalaries.filter { salary ->
                    when {
                        // ✅ Case 1: No staff, no month → show all
                        staffId.isNullOrEmpty() && monthYear.isNullOrEmpty() -> true

                        // ✅ Case 2: Staff only
                        !staffId.isNullOrEmpty() && monthYear.isNullOrEmpty() ->
                            salary.staffId == staffId

                        // ✅ Case 3: Month only
                        staffId.isNullOrEmpty() && !monthYear.isNullOrEmpty() ->
                            salary.monthYear == monthYear

                        // ✅ Case 4: Both staff + month
                        else ->
                            salary.staffId == staffId && salary.monthYear == monthYear
                    }
                }

                _salaryListState.value = UiState.Success(filtered)
            } catch (e: Exception) {
                _salaryListState.value = UiState.Error(e.localizedMessage ?: "Error loading salaries")
            }
        }
    }

    fun loadSalaries(staffId: String? = null, monthYear: String? = null) {
        _salaryListState.value = UiState.Loading
        viewModelScope.launch {
            try {
                val list = salaryRepo.getSalaries(staffId, monthYear)
                _salaryListState.value = UiState.Success(list)
            } catch (e: Exception) {
                _salaryListState.value =
                    UiState.Error(e.localizedMessage ?: "Failed to load salary list")
            }
        }
    }

    fun saveSalary(salary: SalaryModel) {
        _salaryMutationState.value = UiState.Loading
        viewModelScope.launch {
            try {
                salaryRepo.addOrUpdateSalary(salary)
                _salaryMutationState.value = UiState.Success(Unit)
            } catch (e: Exception) {
                _salaryMutationState.value =
                    UiState.Error(e.localizedMessage ?: "Failed to save salary")
            }
        }
    }

    fun deleteSalary(salaryId: String) {
        _salaryMutationState.value = UiState.Loading
        viewModelScope.launch {
            try {
                salaryRepo.deleteSalary(salaryId)
                _salaryMutationState.value = UiState.Success(Unit)
            } catch (e: Exception) {
                _salaryMutationState.value =
                    UiState.Error(e.localizedMessage ?: "Failed to delete salary")
            }
        }
    }

    fun loadMonthlySummary(monthYear: String? = null) {
        _summaryState.value = UiState.Loading
        viewModelScope.launch {
            try {
                val summary = salaryRepo.getMonthlySummary(monthYear)
                _summaryState.value = UiState.Success(summary)
            } catch (e: Exception) {
                _summaryState.value =
                    UiState.Error(e.localizedMessage ?: "Failed to load monthly summary")
            }
        }
    }

    fun loadStaffSummary(staffId: String, monthYear: String? = null) {
        _summaryState.value = UiState.Loading
        viewModelScope.launch {
            try {
                val summary = salaryRepo.getStaffSummary(staffId, monthYear)
                _summaryState.value = UiState.Success(summary)
            } catch (e: Exception) {
                _summaryState.value =
                    UiState.Error(e.localizedMessage ?: "Failed to load staff summary")
            }
        }
    }

    // ✅ FIX: No loading state for chart - silent background operation
    fun loadSalaryChartData(staffId: String? = null, monthYear: String? = null) {
        viewModelScope.launch {
            try {
                val allSalaries = salaryRepo.getAllSalaries()

                // same filter apply here for chart
                val filtered = allSalaries.filter { salary ->
                    when {
                        staffId.isNullOrEmpty() && monthYear.isNullOrEmpty() -> true
                        !staffId.isNullOrEmpty() && monthYear.isNullOrEmpty() ->
                            salary.staffId == staffId
                        staffId.isNullOrEmpty() && !monthYear.isNullOrEmpty() ->
                            salary.monthYear == monthYear
                        else ->
                            salary.staffId == staffId && salary.monthYear == monthYear
                    }
                }

                // Group by month for chart summary
                val grouped = filtered.groupBy { it.monthYear }.mapValues { entry ->
                    entry.value.sumOf { it.netSalary }
                }

                _chartDataState.value = UiState.Success(grouped)
            } catch (e: Exception) {
                _chartDataState.value = UiState.Error(e.localizedMessage ?: "Error loading chart")
            }
        }
    }

    fun resetMutationState() {
        _salaryMutationState.value = UiState.Idle
    }

    fun resetSummaryState() {
        _summaryState.value = UiState.Idle
    }
}