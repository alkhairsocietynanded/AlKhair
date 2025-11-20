package com.zabibtech.alkhair.ui.user.adapters

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.zabibtech.alkhair.R
import com.zabibtech.alkhair.data.models.FeesModel
import com.zabibtech.alkhair.databinding.ItemFeeBinding
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class FeesAdapter(
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
            tvMonthYear.text = feesModel.monthYear
            tvBaseAmount.text = currencyFormat.format(feesModel.baseAmount)
            tvPaidAmount.text = currencyFormat.format(feesModel.paidAmount)
            tvDiscounts.text = currencyFormat.format(feesModel.discounts)
            tvNetFees.text = currencyFormat.format(feesModel.netFees)
            tvRemarks.text = feesModel.remarks

            // Status Chip
            chipStatus.text = feesModel.paymentStatus

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
            val netFeesColor = if (feesModel.netFees > 0) Color.RED else "#2E7D32".toColorInt()
            tvNetFees.setTextColor(netFeesColor)

            val statusColor = when (feesModel.paymentStatus) {
                "Pending" -> R.color.md_theme_error // amber
                "Paid" -> R.color.md_theme_primary // green
                else -> Color.DKGRAY
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

    // 🔹 Automatically sort fees by month and then paymentDate
    override fun submitList(list: List<FeesModel>?) {
        val monthFormat = SimpleDateFormat("yyyy-MMMM", Locale.getDefault())

        val sortedList = list?.sortedWith(compareByDescending { fee ->
            try {
                monthFormat.parse(fee.monthYear)
            } catch (_: Exception) {
                null
            }
        })
        super.submitList(sortedList)
    }

    class DiffCallback : DiffUtil.ItemCallback<FeesModel>() {
        override fun areItemsTheSame(oldItem: FeesModel, newItem: FeesModel) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: FeesModel, newItem: FeesModel) = oldItem == newItem
    }
}
