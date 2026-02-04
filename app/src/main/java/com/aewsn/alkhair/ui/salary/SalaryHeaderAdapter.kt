package com.aewsn.alkhair.ui.salary

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aewsn.alkhair.R
import com.aewsn.alkhair.data.models.User
import com.aewsn.alkhair.databinding.ItemSalaryHeaderBinding
import com.aewsn.alkhair.utils.DateUtils
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import java.text.NumberFormat
import java.util.Locale

/**
 * Single-item adapter for the header section (Filters, Chart, Summary).
 * Used with ConcatAdapter to properly recycle views.
 */
class SalaryHeaderAdapter(
    private val isAdmin: Boolean,
    private val onStaffSelected: (String?) -> Unit,
    private val onMonthSelected: (String?) -> Unit
) : RecyclerView.Adapter<SalaryHeaderAdapter.HeaderViewHolder>() {

    private var binding: ItemSalaryHeaderBinding? = null
    
    // Data State
    private var staffList: List<User> = emptyList()
    private var chartData: Map<String, Double> = emptyMap()
    private var summaryData: SummaryData = SummaryData()

    data class SummaryData(
        val totalPaid: Double = 0.0,
        val totalPending: Double = 0.0,
        val staffCount: Int = 0
    )

    inner class HeaderViewHolder(val binding: ItemSalaryHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind() {
            // Setup UI visibility based on role
            binding.staffSpinnerLayout.visibility = if (isAdmin) android.view.View.VISIBLE else android.view.View.GONE

            // Setup Month Dropdown
            setupMonthDropdown()

            // Setup Staff Dropdown (Admin only)
            if (isAdmin) {
                setupStaffDropdown()
            }

            // Update Chart and Summary with current data
            updateChart()
            updateSummary()
        }

        private fun setupMonthDropdown() {
            val displayList = DateUtils.generateMonthListForPicker()
            val adapter = ArrayAdapter(binding.root.context, android.R.layout.simple_dropdown_item_1line, displayList)
            binding.spinnerMonth.setAdapter(adapter)

            // Default select Current Month
            val currentMonth = DateUtils.getCurrentMonthForFee()
            val index = displayList.indexOf(currentMonth)
            if (displayList.isNotEmpty()) {
                if (index != -1) {
                    binding.spinnerMonth.setText(displayList[index], false)
                } else {
                    binding.spinnerMonth.setText(displayList.first(), false)
                }
            }

            binding.spinnerMonth.setOnItemClickListener { _, _, position, _ ->
                val selectedMonth = if (position == 0) null else displayList[position]
                onMonthSelected(selectedMonth)
            }
        }

        private fun setupStaffDropdown() {
            val names = mutableListOf("All Staff")
            val ids = mutableListOf<String?>(null)

            staffList.forEach {
                names.add(it.name)
                ids.add(it.uid)
            }

            val adapter = ArrayAdapter(binding.root.context, android.R.layout.simple_dropdown_item_1line, names)
            binding.spinnerStaff.setAdapter(adapter)

            if (binding.spinnerStaff.text.isEmpty()) {
                binding.spinnerStaff.setText(names.first(), false)
            }

            binding.spinnerStaff.setOnItemClickListener { _, _, position, _ ->
                onStaffSelected(ids[position])
            }
        }

        private fun updateChart() {
            if (chartData.isEmpty()) {
                binding.salaryChart.clear()
                return
            }

            val entries = chartData.entries.mapIndexed { index, e ->
                BarEntry(index.toFloat(), e.value.toFloat())
            }

            val dataSet = BarDataSet(entries, "Salary").apply {
                colors = ColorTemplate.MATERIAL_COLORS.toList()
                valueTextSize = 10f
            }

            binding.salaryChart.apply {
                data = BarData(dataSet)
                description.isEnabled = false
                axisRight.isEnabled = false
                // Disable animation for performance on rebind
                // animateY(800) 

                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    valueFormatter = IndexAxisValueFormatter(chartData.keys.toList())
                    granularity = 1f
                    labelRotationAngle = -45f
                }
                invalidate()
            }
        }

        private fun updateSummary() {
            val formatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
            binding.tvTotalPaid.text = formatter.format(summaryData.totalPaid)
            binding.tvTotalPending.text = formatter.format(summaryData.totalPending)
            binding.tvTotalStaff.text = summaryData.staffCount.toString()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HeaderViewHolder {
        val inflatedBinding = ItemSalaryHeaderBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        binding = inflatedBinding
        return HeaderViewHolder(inflatedBinding)
    }

    override fun onBindViewHolder(holder: HeaderViewHolder, position: Int) {
        holder.bind()
    }

    override fun getItemCount(): Int = 1 // Always one header item

    /* ============================================================
       🔄 PUBLIC UPDATE METHODS (Called from Activity)
       ============================================================ */

    fun updateStaffList(staff: List<User>) {
        staffList = staff
        // Re-setup dropdown if binding exists
        binding?.let { b ->
            val names = mutableListOf("All Staff")
            val ids = mutableListOf<String?>(null)
            staffList.forEach {
                names.add(it.name)
                ids.add(it.uid)
            }
            val adapter = ArrayAdapter(b.root.context, android.R.layout.simple_dropdown_item_1line, names)
            b.spinnerStaff.setAdapter(adapter)
        }
    }

    fun updateChartData(data: Map<String, Double>) {
        chartData = data
        binding?.let { b ->
            if (data.isEmpty()) {
                b.salaryChart.clear()
                return
            }

            val entries = data.entries.mapIndexed { index, e ->
                BarEntry(index.toFloat(), e.value.toFloat())
            }

            val dataSet = BarDataSet(entries, "Salary").apply {
                colors = ColorTemplate.MATERIAL_COLORS.toList()
                valueTextSize = 10f
            }

            b.salaryChart.apply {
                this.data = BarData(dataSet)
                description.isEnabled = false
                axisRight.isEnabled = false

                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    valueFormatter = IndexAxisValueFormatter(data.keys.toList())
                    granularity = 1f
                    labelRotationAngle = -45f
                }
                invalidate()
            }
        }
    }

    fun updateSummary(paid: Double, pending: Double, count: Int) {
        summaryData = SummaryData(paid, pending, count)
        binding?.let { b ->
            val formatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
            b.tvTotalPaid.text = formatter.format(paid)
            b.tvTotalPending.text = formatter.format(pending)
            b.tvTotalStaff.text = count.toString()
        }
    }
}
