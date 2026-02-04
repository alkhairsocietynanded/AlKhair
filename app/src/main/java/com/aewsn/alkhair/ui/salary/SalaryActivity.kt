package com.aewsn.alkhair.ui.salary

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import com.aewsn.alkhair.data.models.SalaryModel
import com.aewsn.alkhair.data.models.User
import com.aewsn.alkhair.databinding.ActivitySalaryBinding
import com.aewsn.alkhair.ui.user.UserViewModel
import com.aewsn.alkhair.utils.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class SalaryActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySalaryBinding

    private val viewModel: SalaryViewModel by viewModels()
    private val userViewModel: UserViewModel by viewModels()

    private lateinit var salaryAdapter: SalaryAdapter
    private lateinit var headerAdapter: SalaryHeaderAdapter
    private lateinit var concatAdapter: ConcatAdapter

    // Local State
    private var selectedStaffId: String? = null
    private var selectedMonth: String? = null
    private var teacherList: List<User> = emptyList()
    private var isTeacher: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivitySalaryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()
        setupToolbar()

        // Default Hide
        binding.main.visibility = View.INVISIBLE

        // Determine Role & Setup UI
        initializeScreen()
    }

    private fun initializeScreen() {
        DialogUtils.showLoading(supportFragmentManager, "Loading...")

        lifecycleScope.launch {
            val currentUser = userViewModel.getCurrentUser()
            DialogUtils.hideLoading(supportFragmentManager)

            if (currentUser != null) {
                setupUIBasedOnRole(currentUser)
                binding.main.visibility = View.VISIBLE
            } else {
                Toast.makeText(this@SalaryActivity, "Session Expired", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun setupUIBasedOnRole(user: User) {
        val role = user.role.trim()
        isTeacher = role.equals(Roles.TEACHER, ignoreCase = true)

        if (isTeacher) {
            // 🔒 TEACHER VIEW
            binding.fabAddSalary.visibility = View.GONE
            binding.toolbar.title = "My Salary"
            viewModel.setFilters(user.uid, null)
        } else {
            // 🔓 ADMIN VIEW
            binding.fabAddSalary.visibility = View.VISIBLE
            binding.toolbar.title = "Salary Management"
            viewModel.setFilters(null, DateUtils.getCurrentMonthForFee())
            userViewModel.setInitialFilters(Roles.TEACHER, null, Shift.ALL)
        }

        setupRecyclerView()
        setupFab()
        setupObservers()
    }

    /* ============================================================
       🔧 UI SETUP
       ============================================================ */

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupRecyclerView() {
        // 1. Header Adapter (Filters, Chart, Summary)
        headerAdapter = SalaryHeaderAdapter(
            isAdmin = !isTeacher,
            onStaffSelected = { staffId ->
                selectedStaffId = staffId
                viewModel.setFilters(selectedStaffId, selectedMonth)
            },
            onMonthSelected = { monthYear ->
                selectedMonth = monthYear
                viewModel.setFilters(selectedStaffId, selectedMonth)
            }
        )

        // 2. Salary List Adapter
        salaryAdapter = SalaryAdapter(
            isReadOnly = isTeacher,
            onEdit = { if (!isTeacher) showAddEditDialog(it) },
            onDelete = { salary ->
                if (!isTeacher) {
                    DialogUtils.showConfirmation(
                        this,
                        title = "Delete Salary",
                        message = "Are you sure?",
                        onConfirmed = { viewModel.deleteSalary(salary.id) }
                    )
                }
            },
            onMarkPaid = { salary ->
                if (!isTeacher) {
                    val paidCopy = salary.copy(
                        paymentStatus = "PAID",
                        paymentDate = DateUtils.today(),
                        updatedAt = System.currentTimeMillis()
                    )
                    viewModel.saveSalary(paidCopy)
                }
            }
        )

        // 3. Concat both adapters
        concatAdapter = ConcatAdapter(headerAdapter, salaryAdapter)

        binding.rvSalaryList.apply {
            layoutManager = LinearLayoutManager(this@SalaryActivity)
            adapter = concatAdapter
            setHasFixedSize(false)
        }

        // SwipeRefreshLayout (Visual only, data is reactive)
        binding.swipeRefreshLayout.setOnRefreshListener {
            binding.swipeRefreshLayout.isRefreshing = false
        }
    }

    /* ============================================================
       👀 OBSERVERS
       ============================================================ */

    private fun setupObservers() {

        // 1. Staff List (Admin only)
        if (!isTeacher) {
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    userViewModel.userListState.collectLatest { state ->
                        if (state is UiState.Success) {
                            teacherList = state.data
                            headerAdapter.updateStaffList(state.data)
                        }
                    }
                }
            }
        }

        // 2. Salary List
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.salaryListState.collectLatest { state ->
                    when (state) {
                        is UiState.Loading -> {
                            binding.swipeRefreshLayout.isRefreshing = true
                        }
                        is UiState.Success -> {
                            binding.swipeRefreshLayout.isRefreshing = false
                            salaryAdapter.submitList(state.data)
                            // Offload summary calculation
                            withContext(Dispatchers.Default) {
                                val summary = calculateSummary(state.data)
                                withContext(Dispatchers.Main) {
                                    headerAdapter.updateSummary(summary.paid, summary.unpaid, summary.staffCount)
                                }
                            }
                        }
                        is UiState.Error -> {
                            binding.swipeRefreshLayout.isRefreshing = false
                            DialogUtils.showAlert(this@SalaryActivity, "Error", state.message)
                        }
                        else -> {
                            binding.swipeRefreshLayout.isRefreshing = false
                        }
                    }
                }
            }
        }

        // 3. Mutation State (Admin only)
        if (!isTeacher) {
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModel.mutationState.collectLatest { state ->
                        when (state) {
                            is UiState.Loading -> DialogUtils.showLoading(supportFragmentManager, "Processing...")
                            is UiState.Success -> {
                                DialogUtils.hideLoading(supportFragmentManager)
                                Toast.makeText(this@SalaryActivity, "Success", Toast.LENGTH_SHORT).show()
                                viewModel.resetMutationState()
                            }
                            is UiState.Error -> {
                                DialogUtils.hideLoading(supportFragmentManager)
                                DialogUtils.showAlert(this@SalaryActivity, "Error", state.message)
                                viewModel.resetMutationState()
                            }
                            else -> Unit
                        }
                    }
                }
            }
        }

        // 4. Chart Data
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.salaryChartState.collectLatest { state ->
                    if (state is UiState.Success) {
                        headerAdapter.updateChartData(state.data)
                    }
                }
            }
        }
    }

    /* ============================================================
       📊 SUMMARY CALCULATION (Background)
       ============================================================ */

    private data class SummaryResult(val paid: Double, val unpaid: Double, val staffCount: Int)

    private fun calculateSummary(list: List<SalaryModel>): SummaryResult {
        val paid = list.filter { it.paymentStatus.equals("PAID", ignoreCase = true) }.sumOf { it.netSalary }
        val unpaid = list.filter { !it.paymentStatus.equals("PAID", ignoreCase = true) }.sumOf { it.netSalary }
        val staffCount = list.map { it.staffId }.distinct().size
        return SummaryResult(paid, unpaid, staffCount)
    }

    /* ============================================================
       ➕ FAB
       ============================================================ */

    private fun setupFab() {
        binding.fabAddSalary.setOnClickListener {
            showAddEditDialog(null)
        }
    }

    private fun showAddEditDialog(salary: SalaryModel?) {
        AddEditSalaryDialog
            .newInstance(salary, teacherList)
            .show(supportFragmentManager, "AddEditSalaryDialog")
    }
}