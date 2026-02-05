package com.aewsn.alkhair.ui.student.adapter

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aewsn.alkhair.data.models.Leave
import com.aewsn.alkhair.databinding.ItemLeaveHistoryBinding

class LeaveHistoryAdapter(
    private val onEditClick: (Leave) -> Unit,
    private val onDeleteClick: (Leave) -> Unit
) : ListAdapter<Leave, LeaveHistoryAdapter.LeaveViewHolder>(LeaveDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LeaveViewHolder {
        val binding = ItemLeaveHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return LeaveViewHolder(binding, onEditClick, onDeleteClick)
    }

    override fun onBindViewHolder(holder: LeaveViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class LeaveViewHolder(
        private val binding: ItemLeaveHistoryBinding,
        private val onEditClick: (Leave) -> Unit,
        private val onDeleteClick: (Leave) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(leave: Leave) {
            binding.tvDateRange.text = "${leave.startDate} - ${leave.endDate}"
            binding.tvReason.text = leave.reason
            
            binding.chipStatus.text = leave.status
            
            // Color coding validation
            val color = when (leave.status.lowercase()) {
                "approved" -> Color.parseColor("#10B981") // Green
                "rejected" -> Color.parseColor("#EF4444") // Red
                else -> Color.parseColor("#F59E0B") // Amber/Yellow for Pending
            }
            binding.chipStatus.chipBackgroundColor = ColorStateList.valueOf(color)

            // Options Menu
            binding.btnOptions.setOnClickListener { view ->
                val popup = androidx.appcompat.widget.PopupMenu(view.context, view)
                popup.menuInflater.inflate(com.aewsn.alkhair.R.menu.menu_leave_options, popup.menu)
                
                // Disable edit/delete if not pending (optional business rule)
                if (leave.status.lowercase() != "pending") {
                    popup.menu.findItem(com.aewsn.alkhair.R.id.action_edit).isVisible = false
                    popup.menu.findItem(com.aewsn.alkhair.R.id.action_delete).isVisible = false
                }

                popup.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        com.aewsn.alkhair.R.id.action_edit -> {
                            onEditClick(leave)
                            true
                        }
                        com.aewsn.alkhair.R.id.action_delete -> {
                            onDeleteClick(leave)
                            true
                        }
                        else -> false
                    }
                }
                popup.show()
            }
        }
    }

    class LeaveDiffCallback : DiffUtil.ItemCallback<Leave>() {
        override fun areItemsTheSame(oldItem: Leave, newItem: Leave): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Leave, newItem: Leave): Boolean = oldItem == newItem
    }
}
