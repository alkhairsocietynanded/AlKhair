package com.zabibtech.alkhair.ui.salary

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.zabibtech.alkhair.R
import com.zabibtech.alkhair.data.models.SalaryModel
import com.zabibtech.alkhair.databinding.ItemSalaryBinding
import java.text.NumberFormat
import java.util.*

class SalaryAdapter(
    private val onEdit: (SalaryModel) -> Unit,
    private val onDelete: (SalaryModel) -> Unit,
    private val onMarkPaid: (SalaryModel) -> Unit
) : ListAdapter<SalaryModel, SalaryAdapter.SalaryViewHolder>(DiffCallback()) {

    inner class SalaryViewHolder(private val binding: ItemSalaryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(salary: SalaryModel) {
            val context = binding.root.context
            val formatter = NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("en").setRegion("IN").build())

            binding.apply {
                tvStaffName.text = salary.staffName
                tvMonthYear.text = salary.monthYear
                tvNetSalary.text = formatter.format(salary.netSalary)
                tvPaymentStatus.text = salary.paymentStatus

                // Status color
                val isPaid = salary.paymentStatus == "Paid"
                val statusColor = if (isPaid)
                    ContextCompat.getColor(context, android.R.color.holo_green_dark)
                else ContextCompat.getColor(context, android.R.color.holo_red_dark)
                tvPaymentStatus.setBackgroundColor(statusColor)
                // You may need to define bg_status_chip in drawable
                // tvPaymentStatus.setBackgroundResource(if (isPaid) R.drawable.bg_status_paid else R.drawable.bg_status_pending)


                // Allowances and Deductions
                if (salary.allowances > 0) {
                    tvAllowances.visibility = View.VISIBLE
                    tvAllowances.text = "+ ${formatter.format(salary.allowances)}"
                } else {
                    tvAllowances.visibility = View.GONE
                }

                if (salary.deductions > 0) {
                    tvDeductions.visibility = View.VISIBLE
                    tvDeductions.text = "- ${formatter.format(salary.deductions)}"
                } else {
                    tvDeductions.visibility = View.GONE
                }

                // Payment Date
                if (isPaid && !salary.paymentDate.isNullOrEmpty()) {
                    tvPaymentDate.visibility = View.VISIBLE
                    tvPaymentDate.text = "Paid on: ${salary.paymentDate}"
                } else {
                    tvPaymentDate.visibility = View.GONE
                }

                // Remarks
                if (!salary.remarks.isNullOrEmpty()) {
                    tvRemarks.visibility = View.VISIBLE
                    tvRemarks.text = "Note: ${salary.remarks}"
                } else {
                    tvRemarks.visibility = View.GONE
                }

                // Button visibility
                btnMarkPaid.visibility = if (isPaid) View.GONE else View.VISIBLE
                btnMarkPaid.setOnClickListener { onMarkPaid(salary) }

                // Menu button
                btnMenu.setOnClickListener { view ->
                    val popup = PopupMenu(context, view)
                    popup.inflate(R.menu.salary_item_menu)
                    popup.setOnMenuItemClickListener { item ->
                        when (item.itemId) {
                            R.id.action_edit -> {
                                onEdit(salary)
                                true
                            }
                            R.id.action_delete -> {
                                onDelete(salary)
                                true
                            }
                            else -> false
                        }
                    }
                    popup.show()
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SalaryViewHolder {
        val binding = ItemSalaryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return SalaryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SalaryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class DiffCallback : DiffUtil.ItemCallback<SalaryModel>() {
        override fun areItemsTheSame(oldItem: SalaryModel, newItem: SalaryModel) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: SalaryModel, newItem: SalaryModel) =
            oldItem == newItem
    }
}
