package com.zabibtech.alkhair.ui.salary

import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import com.zabibtech.alkhair.R
import com.zabibtech.alkhair.data.models.SalaryModel
import com.zabibtech.alkhair.data.models.User
import com.zabibtech.alkhair.databinding.ActivitySalaryBinding
import com.zabibtech.alkhair.ui.user.UserViewModel
import com.zabibtech.alkhair.utils.DateUtils
import com.zabibtech.alkhair.utils.DialogUtils
import com.zabibtech.alkhair.utils.Roles
import com.zabibtech.alkhair.utils.UiState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

@AndroidEntryPoint
class SalaryActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySalaryBinding
    val viewModel: SalaryViewModel by viewModels()
    private val userViewModel: UserViewModel by viewModels()
    private lateinit var salaryAdapter: SalaryAdapter

    private var selectedStaffId: String? = null
    private var selectedMonth: String? = null
    private var teacherList: List<User> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySalaryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Apply window insets to handle system bars
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupToolbar()

        setupRecyclerView()
        setupMonthDropdown()
        setupObservers()
        setupFab()

        // Initial Load with no filters
        reloadData()
        userViewModel.loadUsers(Roles.TEACHER)
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
            onEdit = { salary -> showAddEditDialog(salary) },
            onDelete = { salary ->
                DialogUtils.showConfirmation(
                    this@SalaryActivity,
                    title = "Delete Salary",
                    message = "Are you sure you want to delete this salary record?",
                    onConfirmed = { viewModel.deleteSalary(salary.id) }
                )
            },
            onMarkPaid = { salary ->
                val updated = salary.copy(paymentStatus = "Paid", paymentDate = getToday())
                viewModel.saveSalary(updated)
            }
        )
        binding.rvSalaryList.apply {
            layoutManager = LinearLayoutManager(this@SalaryActivity)
            adapter = salaryAdapter
        }
    }

    private fun setupMonthDropdown() {
        val monthList = DateUtils.generateMonthListForPicker()

        val monthAdapter =
            ArrayAdapter(
                this,
                android.R.layout.simple_dropdown_item_1line,
                monthList
            )
        binding.spinnerMonth.setAdapter(monthAdapter)
        binding.spinnerMonth.setOnItemClickListener { _, _, position, _ ->
            selectedMonth = if (position == 0) null else monthList[position]
            reloadData()
        }
        binding.spinnerMonth.setText(monthList[0], false)
    }

    private fun populateStaffDropdown(teachers: List<User>) {
        this.teacherList = teachers
        val staffNames = mutableListOf("All Staff")
        val staffIds = mutableListOf<String?>(null)

        teachers.forEach { teacher ->
            staffNames.add(teacher.name)
            staffIds.add(teacher.uid)
        }

        val staffAdapter =
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, staffNames)
        binding.spinnerStaff.setAdapter(staffAdapter)
        binding.spinnerStaff.setOnItemClickListener { _, _, position, _ ->
            selectedStaffId = staffIds[position]
            reloadData()
        }
        binding.spinnerStaff.setText(staffNames[0], false)
    }


    private fun setupObservers() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                userViewModel.userListState.collectLatest { state ->
                    if (state is UiState.Success) {
                        populateStaffDropdown(state.data)
                    }
                }
            }
        }

        // ✅ FIX: Properly handle loading dialog for salary list
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.salaryListState.collectLatest { state ->
                    when (state) {
                        is UiState.Loading -> {
                            DialogUtils.showLoading(supportFragmentManager, "Loading salaries...")
                        }

                        is UiState.Success -> {
                            DialogUtils.hideLoading(supportFragmentManager)
                            salaryAdapter.submitList(state.data)
                            updateSummary(state.data)
                        }

                        is UiState.Error -> {
                            DialogUtils.hideLoading(supportFragmentManager)
                            DialogUtils.showAlert(
                                this@SalaryActivity,
                                message = state.message
                            )
                        }

                        is UiState.Idle -> {
                            // Do nothing on Idle
                        }
                    }
                }
            }
        }

        // ✅ FIX: Separate loading dialog for mutations
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.salaryMutationState.collectLatest { state ->
                    when (state) {
                        is UiState.Loading -> {
                            DialogUtils.showLoading(supportFragmentManager, "Saving...")
                        }

                        is UiState.Success -> {
                            DialogUtils.hideLoading(supportFragmentManager)
                            DialogUtils.showAlert(
                                this@SalaryActivity,
                                message = "Salary updated successfully"
                            )
                            reloadData()
                            viewModel.resetMutationState()
                        }

                        is UiState.Error -> {
                            DialogUtils.hideLoading(supportFragmentManager)
                            DialogUtils.showAlert(this@SalaryActivity, message = state.message)
                            viewModel.resetMutationState()
                        }

                        is UiState.Idle -> {
                            // Do nothing on Idle
                        }
                    }
                }
            }
        }

        // ✅ FIX: Chart data observer - no loading dialog needed
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.chartDataState.collectLatest { state ->
                    when (state) {
                        is UiState.Success -> setupChart(state.data)
                        is UiState.Error -> {
                            DialogUtils.showAlert(
                                this@SalaryActivity,
                                message = state.message
                            )
                        }

                        else -> {}
                    }
                }
            }
        }
    }


    private fun setupChart(data: Map<String, Double>) {
        val barChart = binding.salaryChart

        // Prepare entries and labels from the data map
        val chartEntries = data.entries.mapIndexed { index, entry ->
            BarEntry(index.toFloat(), entry.value.toFloat())
        }
        val monthLabels = data.keys.toList()

        // Create DataSet
        val dataSet = BarDataSet(chartEntries, "Monthly Salary Summary").apply {
            valueTextSize = 10f
            // BUG FIX: Use ColorTemplate for reliable and modern colors
            colors = ColorTemplate.MATERIAL_COLORS.toList()
        }

        // Create BarData
        val barData = BarData(dataSet).apply {
            barWidth = 0.4f
        }

        // Configure BarChart
        barChart.apply {
            this.data = barData
            description.isEnabled = false
            setFitBars(true)
            axisRight.isEnabled = false
            animateY(1000)

            // Configure X-axis
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                valueFormatter = IndexAxisValueFormatter(monthLabels)
                labelRotationAngle = -45f
            }

            invalidate()
        }
    }

    private fun updateSummary(list: List<SalaryModel>) {
        val totalPaid = list.filter { it.paymentStatus == "Paid" }.sumOf { it.netSalary }
        val totalPending = list.filter { it.paymentStatus == "Pending" }.sumOf { it.netSalary }
        val totalStaff = list.map { it.staffId }.distinct().size

        val formatter = NumberFormat.getCurrencyInstance(
            Locale.Builder().setLanguage("en").setRegion("IN").build()
        )
        binding.tvTotalPaid.text = formatter.format(totalPaid)
        binding.tvTotalPending.text = formatter.format(totalPending)
        binding.tvTotalStaff.text = totalStaff.toString()
    }

    private fun setupFab() {
        binding.fabAddSalary.setOnClickListener {
            showAddEditDialog(null)
        }
    }

    private fun showAddEditDialog(salary: SalaryModel?) {
        val dialog = AddEditSalaryDialog.newInstance(salary, teacherList)
        dialog.show(supportFragmentManager, "AddEditSalaryDialog")
    }

    private fun reloadData() {
        viewModel.loadFilteredSalaries(selectedStaffId, selectedMonth)
        viewModel.loadSalaryChartData(selectedStaffId, selectedMonth)
    }


    private fun getToday(): String {
        val cal = Calendar.getInstance()
        return "%04d-%02d-%02d".format(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }
}
