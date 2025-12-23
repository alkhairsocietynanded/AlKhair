package com.zabibtech.alkhair.ui.salary

import android.os.Bundle
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
        setupRecyclerView()
        setupMonthDropdown()
        setupFab()

        // Observers
        setupObservers()

        // Initial setup
        viewModel.setFilters(null, null)

        // Trigger loading of teachers for dropdown
        // Note: UserViewModel now uses 'setRole' instead of 'loadUsers'
        userViewModel.setInitialFilters(Roles.TEACHER, null, Shift.ALL)
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
        salaryAdapter = SalaryAdapter(
            onEdit = { showAddEditDialog(it) },
            onDelete = { salary ->
                DialogUtils.showConfirmation(
                    this,
                    title = "Delete Salary",
                    message = "Are you sure you want to delete this salary record?",
                    onConfirmed = {
                        viewModel.deleteSalary(salary.id)
                    }
                )
            },
            onMarkPaid = { salary ->
                // Create a paid copy
                val paidCopy = salary.copy(
                    paymentStatus = "PAID",
                    paymentDate = DateUtils.today(),
                    updatedAt = System.currentTimeMillis()
                )
                viewModel.saveSalary(paidCopy)
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
        val months = DateUtils.generateMonthListForPicker() // Ensure this util exists or create list manually
        val displayList = mutableListOf("All Months").apply { addAll(months) }

        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, displayList)

        binding.spinnerMonth.setAdapter(adapter)
        binding.spinnerMonth.setText(displayList.first(), false)

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

    private fun setupObservers() {

        // 1. Staff List (For Dropdown)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                userViewModel.userListState.collectLatest { state ->
                    if (state is UiState.Success) {
                        populateStaffDropdown(state.data)
                    }
                }
            }
        }

        // 2. Salary List (Main Data)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.salaryListState.collectLatest { state ->
                    when (state) {
                        is UiState.Loading -> {
                            // Optional: binding.progressBar.visibility = View.VISIBLE
                        }

                        is UiState.Success -> {
                            // binding.progressBar.visibility = View.GONE
                            val list = state.data
                            salaryAdapter.submitList(list)

                            updateSummary(list)

                           /* binding.emptyView.visibility =
                                if(list.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE*/
                        }

                        is UiState.Error -> {
                            // binding.progressBar.visibility = View.GONE
                            DialogUtils.showAlert(this@SalaryActivity, "Error", state.message)
                        }

                        else -> Unit
                    }
                }
            }
        }

        // 3. Mutation State (Save/Delete)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.mutationState.collectLatest { state ->
                    when (state) {
                        is UiState.Loading -> DialogUtils.showLoading(supportFragmentManager, "Processing...")
                        is UiState.Success -> {
                            DialogUtils.hideLoading(supportFragmentManager)
                            Toast.makeText(this@SalaryActivity, "Operation Successful", Toast.LENGTH_SHORT).show()
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