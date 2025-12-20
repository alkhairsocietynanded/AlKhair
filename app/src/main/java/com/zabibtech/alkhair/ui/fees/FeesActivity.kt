package com.zabibtech.alkhair.ui.fees

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import com.zabibtech.alkhair.data.models.FeesOverviewData
import com.zabibtech.alkhair.databinding.ActivityFeeBinding
import com.zabibtech.alkhair.ui.classmanager.ClassManagerActivity
import com.zabibtech.alkhair.utils.DateUtils
import com.zabibtech.alkhair.utils.DialogUtils
import com.zabibtech.alkhair.utils.Modes
import com.zabibtech.alkhair.utils.Roles
import com.zabibtech.alkhair.utils.UiState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.Calendar

@AndroidEntryPoint
class FeesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFeeBinding
    private val viewModel: FeesViewModel by viewModels()
    private var currentMonth: Calendar = Calendar.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityFeeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()
        setupToolbar()
        setupMonthSelector()
        setupListeners()
        observeViewModel()
    }

    /* ============================================================
       🔧 UI SETUP
       ============================================================ */

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
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

    private fun setupMonthSelector() {
        updateMonthView()

        binding.btnPrevMonth.setOnClickListener {
            currentMonth.add(Calendar.MONTH, -1)
            updateMonthView()
        }

        binding.btnNextMonth.setOnClickListener {
            currentMonth.add(Calendar.MONTH, 1)
            updateMonthView()
        }

        binding.tvSelectedMonth.setOnClickListener {
            DateUtils.showMaterialDatePicker(supportFragmentManager, currentMonth) { selectedCal ->
                currentMonth = selectedCal
                updateMonthView()
            }
        }
    }

    private fun updateMonthView() {
        binding.tvSelectedMonth.text = DateUtils.formatDate(currentMonth, "MMMM yyyy")
        val monthYearForApi = DateUtils.formatDate(currentMonth, "yyyy-MMMM")
        // Just update the filter in ViewModel, don't trigger a load manually
        viewModel.setMonthFilter(monthYearForApi)
    }

    private fun setupListeners() {
        binding.btnViewDetails.setOnClickListener {
            val intent = Intent(this, ClassManagerActivity::class.java).apply {
                putExtra("mode", Modes.FEES)
                putExtra("role", Roles.STUDENT)
            }
            startActivity(intent)
        }

       /* binding.fabAddFee.setOnClickListener {
            AddEditFeesDialog.newInstance(null).show(supportFragmentManager, "AddFee")
        }*/
    }

    /* ============================================================
       👀 STATE OBSERVERS
       ============================================================ */

    private fun observeViewModel() {

        // 📦 Fees Overview Data (Charts & Stats)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.feesOverviewState.collect { state ->

                    if (state is UiState.Loading) DialogUtils.showLoading(supportFragmentManager)
                    else DialogUtils.hideLoading(supportFragmentManager)

                    when (state) {
                        is UiState.Success -> {
                            val data = state.data
                            updateStatsUI(data)
                            setupPieChart(binding.pieChartFees, data)
                            setupVerticalBarChart(binding.barChartClassFees, data)
                        }
                        is UiState.Error -> {
                            Toast.makeText(this@FeesActivity, state.message, Toast.LENGTH_LONG).show()
                        }
                        else -> Unit
                    }
                }
            }
        }

        // ✍️ Mutation Results (Save/Delete)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.mutationState.collect { state ->
                    when (state) {
                        is UiState.Success -> {
                            Toast.makeText(this@FeesActivity, "Operation successful", Toast.LENGTH_SHORT).show()
                            viewModel.resetMutationState()
                        }
                        is UiState.Error -> {
                            Toast.makeText(this@FeesActivity, state.message, Toast.LENGTH_LONG).show()
                            viewModel.resetMutationState()
                        }
                        is UiState.Loading -> {
                            // Optional: Show specific mutation loader if needed
                        }
                        UiState.Idle -> Unit
                    }
                }
            }
        }
    }

    private fun updateStatsUI(data: FeesOverviewData) {
        binding.tvTotalStudents.text = data.totalStudents.toString()
        binding.tvTotalFees.text = String.format("₹%,.0f", data.totalFees)
        binding.tvTotalCollected.text = String.format("₹%,.0f", data.totalCollected)
        binding.tvTotalDiscount.text = String.format("₹%,.0f", data.totalDiscount)
        binding.tvTotalDue.text = String.format("₹%,.0f", data.totalDue)
        binding.tvUnpaidCount.text = data.unpaidCount.toString()
    }

    /* ============================================================
       📊 CHARTS
       ============================================================ */

    private fun setupPieChart(pieChart: PieChart, data: FeesOverviewData) {
        val entries = listOf(
            PieEntry(data.totalCollected.toFloat(), "Collected"),
            PieEntry(data.totalDue.toFloat(), "Due"),
            PieEntry(data.totalDiscount.toFloat(), "Discount")
        )

        val dataSet = PieDataSet(entries, "").apply {
            colors = ColorTemplate.MATERIAL_COLORS.toList()
            valueTextSize = 12f
            valueTextColor = Color.WHITE
        }

        pieChart.apply {
            this.data = PieData(dataSet)
            centerText = "Fees"
            animateY(800)
            description.isEnabled = false
            invalidate()
        }
    }

    private fun setupVerticalBarChart(barChart: BarChart, data: FeesOverviewData) {
        val classCollections = data.classWiseCollected.filterValues { it > 0.0 }

        if (classCollections.isEmpty()) {
            barChart.clear()
            barChart.setNoDataText("No fee collection data available")
            return
        }

        val entries = mutableListOf<BarEntry>()
        val labels = mutableListOf<String>()
        var index = 0f

        classCollections.forEach { (className, collected) ->
            entries.add(BarEntry(index, collected.toFloat()))
            labels.add(className)
            index++
        }

        val dataSet = BarDataSet(entries, "Collected Fees by Class").apply {
            colors = ColorTemplate.MATERIAL_COLORS.toList()
            valueTextSize = 12f
            valueFormatter = object : ValueFormatter() {
                override fun getBarLabel(barEntry: BarEntry?): String =
                    "₹${barEntry?.y?.toInt() ?: 0}"
            }
        }

        barChart.apply {
            this.data = BarData(dataSet).apply { barWidth = 0.6f }
            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(labels)
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                granularity = 1f
                labelRotationAngle = -30f
            }
            axisRight.isEnabled = false
            axisLeft.axisMinimum = 0f
            description.isEnabled = false
            legend.isEnabled = false
            animateY(1000)
            extraBottomOffset = 20f
            invalidate()
        }
    }
}