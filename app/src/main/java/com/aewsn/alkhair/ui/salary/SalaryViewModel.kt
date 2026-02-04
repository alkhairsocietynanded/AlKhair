package com.aewsn.alkhair.ui.salary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aewsn.alkhair.data.manager.SalaryRepoManager
import com.aewsn.alkhair.data.models.SalaryModel
import com.aewsn.alkhair.utils.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SalaryViewModel @Inject constructor(
    private val salaryRepoManager: SalaryRepoManager
) : ViewModel() {

    /* ============================================================
       🔹 FILTER STATE (UI driven)
       ============================================================ */

    private val staffIdFilter = MutableStateFlow<String?>(null)
    private val monthYearFilter = MutableStateFlow<String?>(null)

    fun setFilters(staffId: String?, monthYear: String?) {
        staffIdFilter.value = staffId
        monthYearFilter.value = monthYear
    }

    /* ============================================================
       📦 SALARY LIST — SSOT (ROOM → UI)
       ============================================================ */

    @OptIn(ExperimentalCoroutinesApi::class)
    val salaryListState: StateFlow<UiState<List<SalaryModel>>> =
        combine(staffIdFilter, monthYearFilter) { staffId, monthYear ->
            staffId to monthYear
        }.flatMapLatest { (staffId, monthYear) ->
            salaryRepoManager.observeFiltered(staffId, monthYear)
                .map<List<SalaryModel>, UiState<List<SalaryModel>>> {
                    UiState.Success(it)
                }
                .onStart { emit(UiState.Loading) }
                .catch { e ->
                    emit(UiState.Error(e.message ?: "Failed to load salaries"))
                }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UiState.Idle
        )

    /* ============================================================
       📊 CHART DATA — DERIVED FROM SSOT
       ============================================================ */

    val salaryChartState: StateFlow<UiState<Map<String, Double>>> =
        salaryListState.map { state ->
            when (state) {
                is UiState.Success -> {
                    val grouped = state.data
                        .groupBy { it.salaryDate.take(7) } // Group by YYYY-MM
                        .mapValues { entry ->
                            entry.value.sumOf { it.netSalary }
                        }
                    UiState.Success(grouped)
                }
                is UiState.Error -> UiState.Error(state.message)
                is UiState.Loading -> UiState.Loading
                UiState.Idle -> UiState.Idle
                UiState.Idle -> UiState.Idle
            }
        }.flowOn(kotlinx.coroutines.Dispatchers.Default) // CPU Intensive Work
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UiState.Idle
        )

    /* ============================================================
       📈 MONTHLY SUMMARY — DERIVED (NO REPO CALL)
       ============================================================ */

    val monthlySummaryState: StateFlow<UiState<MonthlySummary>> =
        salaryListState.map { state ->
            when (state) {
                is UiState.Success -> {
                    val totalPaid = state.data
                        .filter { it.paymentStatus == "PAID" }
                        .sumOf { it.netSalary }

                    val totalUnpaid = state.data
                        .filter { it.paymentStatus != "PAID" }
                        .sumOf { it.netSalary }

                    UiState.Success(
                        MonthlySummary(
                            totalPaid = totalPaid,
                            totalUnpaid = totalUnpaid,
                            count = state.data.size
                        )
                    )
                }
                is UiState.Error -> UiState.Error(state.message)
                is UiState.Loading -> UiState.Loading
                UiState.Idle -> UiState.Idle
            }
        }.flowOn(kotlinx.coroutines.Dispatchers.Default) // CPU Intensive Work
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UiState.Idle
        )

    /* ============================================================
       ✍️ MUTATIONS — FIREBASE WRITE ONLY
       ============================================================ */

    private val _mutationState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val mutationState: StateFlow<UiState<Unit>> = _mutationState

    fun saveSalary(salary: SalaryModel) {
        _mutationState.value = UiState.Loading
        viewModelScope.launch {
            val result = if (salary.id.isBlank()) {
                salaryRepoManager.createSalary(salary)
            } else {
                salaryRepoManager.updateSalary(salary)
            }

            _mutationState.value = result.fold(
                onSuccess = { UiState.Success(Unit) },
                onFailure = {
                    UiState.Error(it.message ?: "Failed to save salary")
                }
            )
        }
    }

    fun deleteSalary(id: String) {
        _mutationState.value = UiState.Loading
        viewModelScope.launch {
            _mutationState.value = salaryRepoManager.deleteSalary(id).fold(
                onSuccess = { UiState.Success(Unit) },
                onFailure = {
                    UiState.Error(it.message ?: "Failed to delete salary")
                }
            )
        }
    }

    fun resetMutationState() {
        _mutationState.value = UiState.Idle
    }
}

/* ============================================================
   📦 UI MODEL — SAFE & SIMPLE
   ============================================================ */

data class MonthlySummary(
    val totalPaid: Double,
    val totalUnpaid: Double,
    val count: Int
)
