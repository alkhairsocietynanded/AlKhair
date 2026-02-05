package com.aewsn.alkhair.ui.user.adapters

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aewsn.alkhair.R
import com.aewsn.alkhair.data.models.FeesModel
import com.aewsn.alkhair.databinding.ItemFeeBinding
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class FeesAdapter(
    private val isReadOnly: Boolean = false,
    private val onDeleteClick: (FeesModel) -> Unit,
    private val onEditClick: (FeesModel) -> Unit
) : ListAdapter<FeesModel, FeesAdapter.FeeViewHolder>(DiffCallback()) {

    inner class FeeViewHolder(private val binding: ItemFeeBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private val currencyFormat = NumberFormat.getCurrencyInstance(
            Locale.Builder().setLanguage("en").setRegion("IN").build()
        )

        fun bind(feesModel: FeesModel) = with(binding) {
            // Basic Fee Info
            
            // Format feeDate (yyyy-MM-dd) to "MMM yyyy"
            val displayMonth = if (feesModel.feeDate.isNotBlank()) {
                 try {
                     val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                     val outputFormat = SimpleDateFormat("MMMM yyyy", Locale.US)
                     val date = inputFormat.parse(feesModel.feeDate)
                     outputFormat.format(date!!)
                 } catch (e: Exception) {
                     feesModel.feeDate // Fallback
                 }
            } else ""
            
            tvMonthYear.text = displayMonth
            tvBaseAmount.text = currencyFormat.format(feesModel.baseAmount)
            tvPaidAmount.text = currencyFormat.format(feesModel.paidAmount)
            tvDiscounts.text = currencyFormat.format(feesModel.discounts)
            tvNetDue.text = currencyFormat.format(feesModel.dueAmount)
            tvRemarks.text = feesModel.remarks

            // Status Chip
            chipStatus.text = feesModel.paymentStatus

            // Payment Date
            if (feesModel.paymentDate.isNotBlank()) {
                try {
                    val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    val outputFormat = SimpleDateFormat("dd MMM yyyy", Locale.US)
                    val date = inputFormat.parse(feesModel.paymentDate)
                    tvPaymentDate.text = "Paid on: ${outputFormat.format(date!!)}"
                    tvPaymentDate.visibility = android.view.View.VISIBLE
                } catch (e: Exception) {
                     tvPaymentDate.visibility = android.view.View.GONE
                }
            } else {
                tvPaymentDate.visibility = android.view.View.GONE
            }

            // Apply color logic
            applyColors(feesModel)

            // 3-dot popup menu
            if (isReadOnly) {
                btnMore.visibility = android.view.View.GONE
            } else {
                btnMore.visibility = android.view.View.VISIBLE
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
        }

        private fun applyColors(feesModel: FeesModel) = with(binding) {
            val netDueColor = if (feesModel.dueAmount > 0) ContextCompat.getColor(
                binding.root.context,
                R.color.failure
            ) else ContextCompat.getColor(binding.root.context, R.color.success)
            tvNetDue.setTextColor(netDueColor)

            val statusColor = when (feesModel.paymentStatus) {
                "Pending" -> ContextCompat.getColor(binding.root.context, R.color.warning)
                "Paid" -> ContextCompat.getColor(binding.root.context, R.color.success) // green
                else -> ContextCompat.getColor(binding.root.context, R.color.failure)
            }
            chipStatus.chipBackgroundColor = ColorStateList.valueOf(statusColor)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FeeViewHolder {
        val binding = ItemFeeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FeeViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FeeViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    // 🔹 Automatically sort fees by feeDate
    override fun submitList(list: List<FeesModel>?) {
        val sortedList = list?.sortedByDescending { it.feeDate }
        super.submitList(sortedList)
    }

    class DiffCallback : DiffUtil.ItemCallback<FeesModel>() {
        override fun areItemsTheSame(oldItem: FeesModel, newItem: FeesModel) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: FeesModel, newItem: FeesModel) = oldItem == newItem
    }
}
