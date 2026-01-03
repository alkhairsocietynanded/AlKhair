package com.zabibtech.alkhair.ui.salary

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import com.zabibtech.alkhair.data.models.SalaryModel
import com.zabibtech.alkhair.data.models.User
import com.zabibtech.alkhair.databinding.ActivitySalaryBinding
import com.zabibtech.alkhair.ui.user.UserViewModel
import com.zabibtech.alkhair.utils.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

@AndroidEntryPoint
class SalaryActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySalaryBinding

    private val viewModel: SalaryViewModel by viewModels()
    // UserViewModel is used to populate the teacher dropdown
    private val userViewModel: UserViewModel by viewModels()

    private lateinit var salaryAdapter: SalaryAdapter

    // Local State
    private var selectedStaffId: String? = null
    private var selectedMonth: String? = null
    private var teacherList: List<User> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivitySalaryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()
        setupToolbar()

        // Default Hide
        binding.main.visibility = View.INVISIBLE

        // 1. Determine Role & Setup UI
        initializeScreen()
    }

    private fun initializeScreen() {
        // Show Loading
        DialogUtils.showLoading(supportFragmentManager, "Loading...")

        lifecycleScope.launch {
            // Fetch Current User
            val currentUser = userViewModel.getCurrentUser()
            DialogUtils.hideLoading(supportFragmentManager)

            if (currentUser != null) {
                // Setup UI based on Role
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

        if (role.equals(Roles.TEACHER, ignoreCase = true)) {
            // 🔒 TEACHER VIEW
            binding.fabAddSalary.visibility = View.GONE
            binding.staffSpinnerLayout.visibility = View.GONE
            binding.toolbar.title = "My Salary"

            // Auto Filter: Only My Data
            viewModel.setFilters(user.uid, null)

            setupRecyclerView(isReadOnly = true)
            setupMonthDropdown()
            setupObservers(isTeacher = true)

        } else {
            // 🔓 ADMIN VIEW
            binding.fabAddSalary.visibility = View.VISIBLE
            binding.staffSpinnerLayout.visibility = View.VISIBLE
            binding.toolbar.title = "Salary Management"

            // Auto Filter: All Data
            viewModel.setFilters(null, null)

            // Load Teachers for Dropdown
            userViewModel.setInitialFilters(Roles.TEACHER, null, Shift.ALL)

            setupRecyclerView(isReadOnly = false)
            setupMonthDropdown()
            setupFab()
            setupObservers(isTeacher = false)
        }
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

    private fun setupRecyclerView(isReadOnly: Boolean) {
        salaryAdapter = SalaryAdapter(
            isReadOnly = isReadOnly, // ✅ Pass flag
            onEdit = { if (!isReadOnly) showAddEditDialog(it) },
            onDelete = { salary ->
                if (!isReadOnly) {
                    DialogUtils.showConfirmation(
                        this,
                        title = "Delete Salary",
                        message = "Are you sure?",
                        onConfirmed = { viewModel.deleteSalary(salary.id) }
                    )
                }
            },
            onMarkPaid = { salary ->
                if (!isReadOnly) {
                    val paidCopy = salary.copy(
                        paymentStatus = "PAID",
                        paymentDate = DateUtils.today(),
                        updatedAt = System.currentTimeMillis()
                    )
                    viewModel.saveSalary(paidCopy)
                }
            }
        )

        binding.rvSalaryList.apply {
            layoutManager = LinearLayoutManager(this@SalaryActivity)
            adapter = salaryAdapter
        }
    }

    /* ============================================================
       🔹 FILTERS
       ============================================================ */

    private fun setupMonthDropdown() {
        val displayList = DateUtils.generateMonthListForPicker() // Ensure this util exists or create list manually

        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, displayList)

        binding.spinnerMonth.setAdapter(adapter)
        // Default select first item ("All Months")
        if (displayList.isNotEmpty()) {
            binding.spinnerMonth.setText(displayList.first(), false)
        }

        binding.spinnerMonth.setOnItemClickListener { _, _, position, _ ->
            selectedMonth = if (position == 0) null else displayList[position]
            viewModel.setFilters(selectedStaffId, selectedMonth)
        }
    }

    private fun populateStaffDropdown(staff: List<User>) {
        teacherList = staff

        val names = mutableListOf("All Staff")
        val ids = mutableListOf<String?>(null)

        staff.forEach {
            names.add(it.name)
            ids.add(it.uid)
        }

        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, names)

        binding.spinnerStaff.setAdapter(adapter)
        // Only reset selection if list was empty before to avoid UX glitch
        if (binding.spinnerStaff.text.isEmpty()) {
            binding.spinnerStaff.setText(names.first(), false)
        }

        binding.spinnerStaff.setOnItemClickListener { _, _, position, _ ->
            selectedStaffId = ids[position]
            viewModel.setFilters(selectedStaffId, selectedMonth)
        }
    }

    /* ============================================================
       👀 OBSERVERS
       ============================================================ */

    private fun setupObservers(isTeacher: Boolean) {

        // 1. Staff List (Only needed for Admin)
        if (!isTeacher) {
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    userViewModel.userListState.collectLatest { state ->
                        if (state is UiState.Success) {
                            populateStaffDropdown(state.data)
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
                        is UiState.Success -> {
                            salaryAdapter.submitList(state.data)
                            updateSummary(state.data)
                        }
                        is UiState.Error -> {
                            DialogUtils.showAlert(this@SalaryActivity, "Error", state.message)
                        }
                        else -> Unit
                    }
                }
            }
        }

        // 3. Mutation State (Only needed for Admin)
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
                        setupChart(state.data)
                    }
                }
            }
        }
    }

    /* ============================================================
       📊 CHART & SUMMARY
       ============================================================ */

    private fun setupChart(data: Map<String, Double>) {
        if (data.isEmpty()) {
            binding.salaryChart.clear()
            return
        }

        val entries = data.entries.mapIndexed { index, e ->
            BarEntry(index.toFloat(), e.value.toFloat())
        }

        val dataSet = BarDataSet(entries, "Salary").apply {
            colors = ColorTemplate.MATERIAL_COLORS.toList()
            valueTextSize = 10f
        }

        binding.salaryChart.apply {
            this.data = BarData(dataSet)
            description.isEnabled = false
            axisRight.isEnabled = false
            animateY(800)

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                valueFormatter = IndexAxisValueFormatter(data.keys.toList())
                granularity = 1f
                labelRotationAngle = -45f
            }
            invalidate()
        }
    }

    private fun updateSummary(list: List<SalaryModel>) {
        val paid = list.filter { it.paymentStatus == "PAID" }.sumOf { it.netSalary }
        val unpaid = list.filter { it.paymentStatus != "PAID" }.sumOf { it.netSalary }
        val staffCount = list.map { it.staffId }.distinct().size

        val formatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

        binding.tvTotalPaid.text = formatter.format(paid)
        binding.tvTotalPending.text = formatter.format(unpaid)
        binding.tvTotalStaff.text = staffCount.toString()
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