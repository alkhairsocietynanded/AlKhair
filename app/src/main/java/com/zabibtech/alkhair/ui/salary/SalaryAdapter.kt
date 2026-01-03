package com.zabibtech.alkhair.ui.salary

import android.content.res.ColorStateList
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
import java.util.Locale

class SalaryAdapter(
    private val isReadOnly: Boolean, // ✅ Flag to check Role (Teacher vs Admin)
    private val onEdit: (SalaryModel) -> Unit,
    private val onDelete: (SalaryModel) -> Unit,
    private val onMarkPaid: (SalaryModel) -> Unit
) : ListAdapter<SalaryModel, SalaryAdapter.SalaryViewHolder>(DiffCallback()) {

    inner class SalaryViewHolder(private val binding: ItemSalaryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(salary: SalaryModel) {
            val context = binding.root.context

            // Format Currency (e.g., ₹ 50,000)
            val formatter = NumberFormat.getCurrencyInstance(
                Locale.Builder().setLanguage("en").setRegion("IN").build()
            )

            binding.apply {
                // 1. Basic Data Binding
                tvStaffName.text = salary.staffName
                tvMonthYear.text = salary.monthYear
                tvNetSalary.text = formatter.format(salary.netSalary)
                tvPaymentStatus.text = salary.paymentStatus

                // 2. Status Color Logic
                // Check ignoreCase to handle "PAID" or "Paid"
                val isPaid = salary.paymentStatus.equals("Paid", ignoreCase = true)

                val statusColor = if (isPaid)
                    ContextCompat.getColor(context, R.color.success)
                else ContextCompat.getColor(context, R.color.failure)

                tvPaymentStatus.chipBackgroundColor = ColorStateList.valueOf(statusColor)

                // 3. Conditional Visibility for Details
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

                /* ============================================================
                   🔒 ROLE BASED UI VISIBILITY
                   ============================================================ */

                if (isReadOnly) {
                    // 👨‍🏫 TEACHER VIEW: Hide Actions
                    btnMarkPaid.visibility = View.GONE
                    btnMenu.visibility = View.GONE
                } else {
                    // 👑 ADMIN VIEW: Show Actions

                    // Show "Mark Paid" only if Pending
                    btnMarkPaid.visibility = if (isPaid) View.GONE else View.VISIBLE
                    btnMarkPaid.setOnClickListener { onMarkPaid(salary) }

                    // Show Menu (Edit/Delete)
                    btnMenu.visibility = View.VISIBLE
                    btnMenu.setOnClickListener { view ->
                        val popup = PopupMenu(context, view)
                        // Ensure you have this menu resource, or use R.menu.menu_item_actions
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