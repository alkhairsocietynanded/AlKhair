package com.zabibtech.alkhair.ui.user.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.zabibtech.alkhair.R
import com.zabibtech.alkhair.data.models.FeesModel
import com.zabibtech.alkhair.databinding.ItemFeeBinding
import java.text.SimpleDateFormat
import java.util.*
import androidx.core.graphics.toColorInt

class FeesAdapter(
    private val onDeleteClick: (FeesModel) -> Unit,
    private val onEditClick: (FeesModel) -> Unit
) : ListAdapter<FeesModel, FeesAdapter.FeeViewHolder>(DiffCallback()) {

    inner class FeeViewHolder(private val binding: ItemFeeBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(feesModel: FeesModel) = with(binding) {
            // Basic FeesModel Info
            tvMonth.text = feesModel.month
            tvMonthlyFees.text = "Monthly Fees: ₹${feesModel.totalAmount}"
            tvPaidAmount.text = "Paid: ₹${feesModel.paidAmount}"
            tvDueAmount.text = "Due: ₹${feesModel.dueAmount}"
            tvStatus.text = feesModel.status
            tvDate.text = "Payment Date: ${feesModel.paymentDate}"

            // Apply color logic
            applyColors(feesModel)

            // 3-dot popup menu
            btnMore.setOnClickListener { view ->
                val popup = PopupMenu(view.context, view)
                popup.menuInflater.inflate(R.menu.menu_fee_item, popup.menu)
                popup.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.action_edit -> {
                            onEditClick(feesModel)
                            true
                        }

                        R.id.action_delete -> {
                            onDeleteClick(feesModel)
                            true
                        }

                        else -> false
                    }
                }
                popup.show()
            }
        }

        private fun applyColors(feesModel: FeesModel) = with(binding) {
            val dueColor = if (feesModel.dueAmount > 0)
                Color.RED
            else
                "#2E7D32".toColorInt() // dark green
            tvDueAmount.setTextColor(dueColor)

            val statusColor = when (feesModel.status) {
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
    override fun submitList(list: List<FeesModel>?) {
        val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        val sortedList = list?.sortedWith(compareByDescending<FeesModel> { fee ->
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

    class DiffCallback : DiffUtil.ItemCallback<FeesModel>() {
        override fun areItemsTheSame(oldItem: FeesModel, newItem: FeesModel) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: FeesModel, newItem: FeesModel) = oldItem == newItem
    }
}
