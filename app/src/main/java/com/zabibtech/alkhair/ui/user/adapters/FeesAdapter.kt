package com.zabibtech.alkhair.ui.user.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.zabibtech.alkhair.R
import com.zabibtech.alkhair.data.models.Fee
import com.zabibtech.alkhair.databinding.ItemFeeBinding
import java.text.SimpleDateFormat
import java.util.*
import androidx.core.graphics.toColorInt

class FeesAdapter(
    private val onDeleteClick: (Fee) -> Unit,
    private val onEditClick: (Fee) -> Unit
) : ListAdapter<Fee, FeesAdapter.FeeViewHolder>(DiffCallback()) {

    inner class FeeViewHolder(private val binding: ItemFeeBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(fee: Fee) = with(binding) {
            // Basic Fee Info
            tvMonth.text = fee.month
            tvMonthlyFees.text = "Monthly Fees: ₹${fee.totalAmount}"
            tvPaidAmount.text = "Paid: ₹${fee.paidAmount}"
            tvDueAmount.text = "Due: ₹${fee.dueAmount}"
            tvStatus.text = fee.status
            tvDate.text = "Payment Date: ${fee.paymentDate}"

            // Apply color logic
            applyColors(fee)

            // 3-dot popup menu
            btnMore.setOnClickListener { view ->
                val popup = PopupMenu(view.context, view)
                popup.menuInflater.inflate(R.menu.menu_fee_item, popup.menu)
                popup.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.action_edit -> {
                            onEditClick(fee)
                            true
                        }

                        R.id.action_delete -> {
                            onDeleteClick(fee)
                            true
                        }

                        else -> false
                    }
                }
                popup.show()
            }
        }

        private fun applyColors(fee: Fee) = with(binding) {
            val dueColor = if (fee.dueAmount > 0)
                Color.RED
            else
                "#2E7D32".toColorInt() // dark green
            tvDueAmount.setTextColor(dueColor)

            val statusColor = when (fee.status) {
                "Unpaid" -> Color.RED
                "Partially Paid" -> "#FFA000".toColorInt() // amber
                "Paid" -> "#2E7D32".toColorInt() // green
                else -> Color.DKGRAY
            }
            tvStatus.setTextColor(statusColor)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FeeViewHolder {
        val binding = ItemFeeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FeeViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FeeViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    // 🔹 Automatically sort fees by month and then paymentDate
    override fun submitList(list: List<Fee>?) {
        val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        val sortedList = list?.sortedWith(compareByDescending<Fee> { fee ->
            try {
                monthFormat.parse(fee.month)
            } catch (_: Exception) {
                null
            }
        }.thenByDescending { fee ->
            try {
                dateFormat.parse(fee.paymentDate)
            } catch (_: Exception) {
                null
            }
        })
        super.submitList(sortedList)
    }

    class DiffCallback : DiffUtil.ItemCallback<Fee>() {
        override fun areItemsTheSame(oldItem: Fee, newItem: Fee) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Fee, newItem: Fee) = oldItem == newItem
    }
}
