package com.zabibtech.alkhair.ui.fees

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zabibtech.alkhair.data.manager.FeesRepoManager
import com.zabibtech.alkhair.data.manager.UserRepoManager
import com.zabibtech.alkhair.data.models.FeesModel
import com.zabibtech.alkhair.data.models.FeesOverviewData
import com.zabibtech.alkhair.data.models.User
import com.zabibtech.alkhair.utils.Roles
import com.zabibtech.alkhair.utils.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FeesViewModel @Inject constructor(
    private val feesRepoManager: FeesRepoManager,
    private val userRepoManager: UserRepoManager
) : ViewModel() {

    /* ============================================================
       🔹 FILTER STATE (For Overview)
       ============================================================ */

    private val _monthFilter = MutableStateFlow<String?>(null)

    fun setMonthFilter(monthYear: String) {
        _monthFilter.value = monthYear
    }

    /* ============================================================
       📦 STUDENTS DATA (Cached for calculations)
       ============================================================ */

    private val _studentsFlow = MutableStateFlow<List<User>>(emptyList())

    init {
        loadStudents()
    }

    private fun loadStudents() {
        viewModelScope.launch {
            // Fetch students to calculate "Unpaid Count" and link names
            userRepoManager.getUsersByRole(Roles.STUDENT).onSuccess {
                _studentsFlow.value = it
            }
        }
    }

    /* ============================================================
       📊 FEES OVERVIEW — REACTIVE PIPELINE (Admin Dashboard)
       ============================================================ */

    val feesOverviewState: StateFlow<UiState<FeesOverviewData>> =
        combine(
            _monthFilter,
            feesRepoManager.observeLocal(), // SSOT: Observe Room DB
            _studentsFlow
        ) { month, allFees, students ->
            Triple(month, allFees, students)
        }
            .map { (month, allFees, students) ->
                if (month == null) return@map UiState.Idle

                // 1. Filter by Month
                val feesForMonth = allFees.filter { it.monthYear == month }

                // 2. Calculate Stats
                calculateOverview(feesForMonth, students)
            }
            .onStart { emit(UiState.Loading) }
            .catch { emit(UiState.Error(it.message ?: "Failed to compute fees")) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                UiState.Idle
            )

    private fun calculateOverview(
        feesForMonth: List<FeesModel>,
        students: List<User>
    ): UiState<FeesOverviewData> {
        if (students.isEmpty()) return UiState.Loading

        val totalStudents = students.size
        val feesByStudentId = feesForMonth.groupBy { it.studentId }

        // Logic: Count students who have pending dues
        val unpaidStudentsCount = students.count { student ->
            val studentFees = feesByStudentId[student.uid]
            if (studentFees.isNullOrEmpty()) {
                true // No record = Unpaid (Assuming monthly fee logic)
            } else {
                val due = studentFees.sumOf { it.baseAmount } -
                        studentFees.sumOf { it.paidAmount } -
                        studentFees.sumOf { it.discounts }
                due > 0
            }
        }

        val totalFees = feesForMonth.sumOf { it.baseAmount }
        val totalCollected = feesForMonth.sumOf { it.paidAmount }
        val totalDiscount = feesForMonth.sumOf { it.discounts }
        val totalDue = totalFees - totalCollected - totalDiscount

        // Group collection by Class Name
        val collectedByClass = students
            .filter { it.className.isNotBlank() }
            .groupBy { it.className }
            .mapValues { (_, classStudents) ->
                val ids = classStudents.map { it.uid }.toSet()
                feesForMonth
                    .filter { it.studentId in ids }
                    .sumOf { it.paidAmount }
            }

        // ✅ Explicit Cast to avoid Type Mismatch with Loading state
        return UiState.Success(
            FeesOverviewData(
                totalStudents = totalStudents,
                totalFees = totalFees,
                totalCollected = totalCollected,
                totalDiscount = totalDiscount,
                totalDue = totalDue,
                unpaidCount = unpaidStudentsCount,
                classWiseCollected = collectedByClass
            )
        ) as UiState<FeesOverviewData>
    }

    /* ============================================================
       🎓 STUDENT SPECIFIC FEES LIST (Reactive)
       Used in Student Profile / Student Dashboard
       ============================================================ */

    private val _studentIdFilter = MutableStateFlow<String?>(null)

    fun setStudentId(studentId: String) {
        _studentIdFilter.value = studentId
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val studentFeesListState: StateFlow<UiState<List<FeesModel>>> = _studentIdFilter
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else feesRepoManager.observeFeesForStudent(id) // Observe Local DB
        }
        .map { fees ->
            // ✅ Explicit Cast to avoid Type Mismatch
            UiState.Success(fees) as UiState<List<FeesModel>>
        }
        .onStart { emit(UiState.Loading) }
        .catch { emit(UiState.Error(it.message ?: "Failed to load fees")) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            UiState.Idle
        )

    /* ============================================================
       ✍️ MUTATIONS (Save / Delete)
       ============================================================ */

    private val _mutationState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val mutationState: StateFlow<UiState<Unit>> = _mutationState

    fun saveFee(feesModel: FeesModel) {
        _mutationState.value = UiState.Loading
        viewModelScope.launch {
            // Decide Create vs Update based on ID
            val result = if (feesModel.id.isEmpty()) {
                feesRepoManager.createFee(feesModel)
            } else {
                feesRepoManager.updateFee(feesModel)
            }

            _mutationState.value = result.fold(
                onSuccess = { UiState.Success(Unit) },
                onFailure = { UiState.Error(it.message ?: "Save failed") }
            )
        }
    }

    fun deleteFee(feeId: String) {
        _mutationState.value = UiState.Loading
        viewModelScope.launch {
            _mutationState.value = feesRepoManager.deleteFee(feeId).fold(
                onSuccess = { UiState.Success(Unit) },
                onFailure = { UiState.Error(it.message ?: "Delete failed") }
            )
        }
    }

    fun resetMutationState() {
        _mutationState.value = UiState.Idle
    }
}