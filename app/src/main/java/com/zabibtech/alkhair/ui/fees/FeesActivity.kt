package com.zabibtech.alkhair.ui.fees

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.zabibtech.alkhair.data.models.FeesOverviewData
import com.zabibtech.alkhair.databinding.ActivityFeeBinding
import com.zabibtech.alkhair.ui.classmanager.ClassManagerActivity
import com.zabibtech.alkhair.utils.DialogUtils
import com.zabibtech.alkhair.utils.Modes
import com.zabibtech.alkhair.utils.Roles
import com.zabibtech.alkhair.utils.UiState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class FeesActivity : AppCompatActivity() {
    private lateinit var binding: ActivityFeeBinding
    private val viewModel: FeesViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityFeeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ✅ Insets handle karo
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupListeners()
        observeFeesData()
        viewModel.loadAllFeesOverview() // custom method for summary
    }

    private fun setupListeners() {
        binding.btnViewDetails.setOnClickListener {
            val intent = Intent(this, ClassManagerActivity::class.java).apply {
                putExtra("mode", Modes.FEES)
                putExtra("role", Roles.STUDENT)
            }
            startActivity(intent)
        }

        binding.btnDownloadReport.setOnClickListener {
            // TODO: implement download logic
        }
    }

    private fun observeFeesData() {
        lifecycleScope.launch {
            viewModel.feesOverviewState.collectLatest { state ->
                when (state) {
                    is UiState.Loading -> {
                        DialogUtils.showLoading(supportFragmentManager)
                    }

                    is UiState.Success -> {
                        DialogUtils.hideLoading(supportFragmentManager)
                        val data = state.data
                        binding.tvTotalStudents.text = data.totalStudents.toString()
                        binding.tvTotalFees.text = "₹${data.totalFees}"
                        binding.tvTotalCollected.text = "₹${data.totalCollected}"
                        binding.tvTotalDue.text = "₹${data.totalDue}"
                        binding.tvUnpaidCount.text = data.unpaidCount.toString()

                        // Pie chart 1: Overall
                        setupPieChart(binding.pieChartFees, data)

                        // Pie chart 2: Class-wise collected
                        setupVerticalBarChart(binding.barChartClassFees, data)
                    }

                    is UiState.Error -> {
                        DialogUtils.hideLoading(supportFragmentManager)
                    }

                    else -> {
                        DialogUtils.hideLoading(supportFragmentManager)
                    }
                }
            }
        }
    }

    private fun setupPieChart(pieChart: PieChart, data: FeesOverviewData) {
        val entries = listOf(
            PieEntry(data.totalCollected.toFloat(), "Collected"),
            PieEntry(data.totalDue.toFloat(), "Due"),
        )

        val dataSet = PieDataSet(entries, "")
        dataSet.colors = listOf(
            getColor(android.R.color.holo_green_light),
            getColor(android.R.color.holo_red_light)
        )
        val pieData = PieData(dataSet)
        pieChart.data = pieData
        pieChart.centerText = "Fees"
        pieChart.animateY(800)
        pieChart.invalidate()
    }

    private fun setupVerticalBarChart(
        barChart: com.github.mikephil.charting.charts.BarChart,
        data: FeesOverviewData
    ) {
        val classCollections = data.classWiseCollected.filterValues { it > 0.0 }

        if (classCollections.isEmpty()) {
            barChart.clear()
            barChart.setNoDataText("No fee collection data available")
            return
        }

        val entries = mutableListOf<com.github.mikephil.charting.data.BarEntry>()
        val labels = mutableListOf<String>()
        var index = 0f

        classCollections.forEach { (className, collected) ->
            entries.add(com.github.mikephil.charting.data.BarEntry(index, collected.toFloat()))
            labels.add(className)
            index++
        }

        val dataSet = com.github.mikephil.charting.data.BarDataSet(entries, "Collected Fees by Class")
        dataSet.colors = listOf(
            getColor(android.R.color.holo_blue_light),
            getColor(android.R.color.holo_green_light),
            getColor(android.R.color.holo_orange_light),
            getColor(android.R.color.holo_red_light),
            getColor(android.R.color.holo_purple)
        ).take(entries.size)

        // Show amount on top of bars
        dataSet.valueTextSize = 14f
        dataSet.valueTextColor = getColor(android.R.color.black)
        dataSet.valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
            override fun getBarLabel(barEntry: com.github.mikephil.charting.data.BarEntry?): String {
                val amount = barEntry?.y?.toInt() ?: 0
                return "₹$amount"
            }
        }

        val barData = com.github.mikephil.charting.data.BarData(dataSet)
        barData.barWidth = 0.6f
        barChart.data = barData

        // X-axis = class names below bars
        val xAxis = barChart.xAxis
        xAxis.valueFormatter = com.github.mikephil.charting.formatter.IndexAxisValueFormatter(labels)
        xAxis.position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.granularity = 1f
        xAxis.textSize = 12f
        xAxis.labelRotationAngle = -30f // optional, avoid overlapping

        // Y-axis setup
        barChart.axisRight.isEnabled = false
        barChart.axisLeft.axisMinimum = 0f

        // Legend + description
        barChart.legend.isEnabled = false
        barChart.description.isEnabled = false

        // Enable pinch/zoom
        barChart.setPinchZoom(true)
        barChart.setScaleEnabled(true)
        barChart.setFitBars(true)
        barChart.setDrawValueAboveBar(true)

        barChart.animateY(1000)

        barChart.setExtraBottomOffset(40f) // make sure labels have space below
        barChart.setFitBars(true)
        barChart.invalidate()
    }
}
