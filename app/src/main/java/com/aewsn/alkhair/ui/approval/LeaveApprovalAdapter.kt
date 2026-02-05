package com.aewsn.alkhair.ui.approval

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aewsn.alkhair.data.models.LeaveWithStudent
import com.aewsn.alkhair.databinding.ItemLeaveApprovalBinding

class LeaveApprovalAdapter(
    private val onApprove: (com.aewsn.alkhair.data.models.Leave) -> Unit,
    private val onReject: (com.aewsn.alkhair.data.models.Leave) -> Unit
) : ListAdapter<LeaveWithStudent, LeaveApprovalAdapter.LeaveViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LeaveViewHolder {
        val binding = ItemLeaveApprovalBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return LeaveViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LeaveViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class LeaveViewHolder(private val binding: ItemLeaveApprovalBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: LeaveWithStudent) {
            val leave = item.leave
            
            binding.tvStudentName.text = item.studentName
            binding.tvDateRange.text = "${leave.startDate} - ${leave.endDate}"
            binding.tvReason.text = leave.reason
            
            binding.chipStatus.text = leave.status
            
            // Status Color Logic
            val statusColor = when (leave.status.lowercase()) {
                "approved" -> Color.parseColor("#059669") // Green
                "rejected" -> Color.parseColor("#DC2626") // Red
                else -> Color.parseColor("#D97706")       // Amber/Orange
            }
            binding.chipStatus.chipBackgroundColor = ColorStateList.valueOf(statusColor)

            // Show/Hide Actions based on Status
            if (leave.status.equals("Pending", ignoreCase = true)) {
                binding.actionLayout.visibility = View.VISIBLE
                binding.btnApprove.setOnClickListener { onApprove(leave) }
                binding.btnReject.setOnClickListener { onReject(leave) }
            } else {
                binding.actionLayout.visibility = View.GONE
                // Optional: Show a text saying "Processed" or just hide buttons
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<LeaveWithStudent>() {
        override fun areItemsTheSame(oldItem: LeaveWithStudent, newItem: LeaveWithStudent): Boolean {
            return oldItem.leave.id == newItem.leave.id
        }

        override fun areContentsTheSame(oldItem: LeaveWithStudent, newItem: LeaveWithStudent): Boolean {
            return oldItem == newItem
        }
    }
}
