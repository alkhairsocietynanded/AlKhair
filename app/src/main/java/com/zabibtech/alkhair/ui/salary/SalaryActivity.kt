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

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupToolbar()
        setupRecyclerView()
        setupMonthDropdown()
        setupFab()
        setupObservers()

        // Initial filters → triggers SSOT observation
        viewModel.setFilters(null, null)

        // Load staff list
        userViewModel.loadUsers(Roles.TEACHER)
    }

    /* ============================================================
       🔹 TOOLBAR
       ============================================================ */

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    /* ============================================================
       🔹 RECYCLER VIEW
       ============================================================ */

    private fun setupRecyclerView() {
        salaryAdapter = SalaryAdapter(
            onEdit = { showAddEditDialog(it) },
            onDelete = { salary ->
                DialogUtils.showConfirmation(
                    this,
                    title = "Delete Salary",
                    message = "Are you sure you want to delete this salary?",
                    onConfirmed = {
                        viewModel.deleteSalary(salary.id)
                    }
                )
            },
            onMarkPaid = { salary ->
                viewModel.saveSalary(
                    salary.copy(
                        paymentStatus = "PAID",
                        paymentDate = DateUtils.today()
                    )
                )
            }
        )

        binding.rvSalaryList.apply {
            layoutManager = LinearLayoutManager(this@SalaryActivity)
            adapter = salaryAdapter
        }
    }

    /* ============================================================
       🔹 MONTH FILTER
       ============================================================ */

    private fun setupMonthDropdown() {
        val months = DateUtils.generateMonthListForPicker()

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            months
        )

        binding.spinnerMonth.setAdapter(adapter)
        binding.spinnerMonth.setText(months.first(), false)

        binding.spinnerMonth.setOnItemClickListener { _, _, position, _ ->
            selectedMonth = if (position == 0) null else months[position]
            viewModel.setFilters(selectedStaffId, selectedMonth)
        }
    }

    /* ============================================================
       🔹 STAFF FILTER
       ============================================================ */

    private fun populateStaffDropdown(staff: List<User>) {
        teacherList = staff

        val names = mutableListOf("All Staff")
        val ids = mutableListOf<String?>(null)

        staff.forEach {
            names.add(it.name)
            ids.add(it.uid)
        }

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            names
        )

        binding.spinnerStaff.setAdapter(adapter)
        binding.spinnerStaff.setText(names.first(), false)

        binding.spinnerStaff.setOnItemClickListener { _, _, position, _ ->
            selectedStaffId = ids[position]
            viewModel.setFilters(selectedStaffId, selectedMonth)
        }
    }

    /* ============================================================
       🔹 OBSERVERS (SSOT)
       ============================================================ */

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

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.salaryListState.collectLatest { state ->
                    when (state) {
                        is UiState.Loading ->
                            DialogUtils.showLoading(
                                supportFragmentManager,
                                "Loading salaries..."
                            )

                        is UiState.Success -> {
                            DialogUtils.hideLoading(supportFragmentManager)
                            salaryAdapter.submitList(state.data)
                            updateSummary(state.data)
                        }

                        is UiState.Error -> {
                            DialogUtils.hideLoading(supportFragmentManager)
                            DialogUtils.showAlert(this@SalaryActivity, "Error", state.message)
                        }

                        UiState.Idle -> Unit
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.mutationState.collectLatest { state ->
                    when (state) {
                        is UiState.Loading ->
                            DialogUtils.showLoading(
                                supportFragmentManager,
                                "Saving..."
                            )

                        is UiState.Success -> {
                            DialogUtils.hideLoading(supportFragmentManager)
                            DialogUtils.showAlert(
                                this@SalaryActivity,
                                "Operation successful",
                                "Salary saved successfully"
                            )
                            viewModel.resetMutationState()
                        }

                        is UiState.Error -> {
                            DialogUtils.hideLoading(supportFragmentManager)
                            DialogUtils.showAlert(this@SalaryActivity, "Error", state.message)
                            viewModel.resetMutationState()
                        }

                        UiState.Idle -> Unit
                    }
                }
            }
        }

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
       📊 CHART
       ============================================================ */

    private fun setupChart(data: Map<String, Double>) {
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

    /* ============================================================
       📈 SUMMARY
       ============================================================ */

    private fun updateSummary(list: List<SalaryModel>) {
        val paid = list.filter { it.paymentStatus == "PAID" }.sumOf { it.netSalary }
        val unpaid = list.filter { it.paymentStatus != "PAID" }.sumOf { it.netSalary }
        val staffCount = list.map { it.staffId }.distinct().size

        val formatter = NumberFormat.getCurrencyInstance(
            Locale.Builder().setLanguage("en").setRegion("IN").build()
        )

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
