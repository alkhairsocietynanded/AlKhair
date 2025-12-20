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
       🔹 FILTER STATE
       ============================================================ */

    private val _monthFilter = MutableStateFlow<String?>(null)

    fun setMonthFilter(monthYear: String) {
        _monthFilter.value = monthYear
    }

    /* ============================================================
       📦 STUDENTS DATA
       ============================================================ */

    private val _studentsFlow = MutableStateFlow<List<User>>(emptyList())

    init {
        loadStudents()
    }

    private fun loadStudents() {
        viewModelScope.launch {
            userRepoManager.getUsersByRole(Roles.STUDENT).onSuccess {
                _studentsFlow.value = it
            }
        }
    }

    /* ============================================================
       📦 FEES OVERVIEW — REACTIVE PIPELINE
       ============================================================ */

    val feesOverviewState: StateFlow<UiState<FeesOverviewData>> =
        combine(
            _monthFilter,
            feesRepoManager.observeLocal(),
            _studentsFlow
        ) { month, allFees, students ->
            Triple(month, allFees, students)
        }
            // यहाँ भी Type Inference को मदद करने के लिए explicit type दिया जा सकता है,
            // लेकिन calculateOverview का return type already UiState है, तो यह usually चल जाता है।
            .map { (month, allFees, students) ->
                if (month == null) return@map UiState.Idle

                val feesForMonth = allFees.filter { it.monthYear == month }
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

        val unpaidStudentsCount = students.count { student ->
            val studentFees = feesByStudentId[student.uid]
            if (studentFees.isNullOrEmpty()) {
                true
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

        val collectedByClass = students
            .filter { it.className.isNotBlank() }
            .groupBy { it.className }
            .mapValues { (_, classStudents) ->
                val ids = classStudents.map { it.uid }.toSet()
                feesForMonth
                    .filter { it.studentId in ids }
                    .sumOf { it.paidAmount }
            }

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
        )
    }

    /* ============================================================
       🎓 STUDENT SPECIFIC FEES LIST (Reactive) - FIXED HERE 🔧
       ============================================================ */

    private val _studentIdFilter = MutableStateFlow<String?>(null)

    fun setStudentId(studentId: String) {
        _studentIdFilter.value = studentId
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val studentFeesListState: StateFlow<UiState<List<FeesModel>>> = _studentIdFilter
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else feesRepoManager.observeFeesForStudent(id)
        }
        // 👇 FIX: Added <List<FeesModel>, UiState<List<FeesModel>>> explicit typing
        .map<List<FeesModel>, UiState<List<FeesModel>>> { fees ->
            UiState.Success(fees)
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