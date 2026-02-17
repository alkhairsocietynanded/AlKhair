package com.aewsn.alkhair.ui.student.dashboard

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aewsn.alkhair.data.models.Announcement
import com.aewsn.alkhair.databinding.ItemDashboardAnnouncementBinding

class DashboardAnnouncementAdapter : ListAdapter<Announcement, DashboardAnnouncementAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDashboardAnnouncementBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(private val binding: ItemDashboardAnnouncementBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Announcement) {
            binding.tvTitle.text = item.title
            binding.tvContent.text = item.content
            
            // Format time relative (e.g., "2 hours ago")
            val relativeTime = DateUtils.getRelativeTimeSpanString(item.timestamp, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
            binding.tvDate.text = relativeTime
            
            // Badge Logic (Mock based on title content for demo)
            if (item.title.contains("Holiday", true) || item.title.contains("Reopening", true)) {
                binding.tvBadge.text = "HOLIDAY"
                binding.tvBadge.setTextColor(android.graphics.Color.parseColor("#16A34A")) // Green
                binding.cardBadge.setCardBackgroundColor(android.graphics.Color.parseColor("#DCFCE7")) // Light Green
            } else {
                binding.tvBadge.text = "EVENT"
                binding.tvBadge.setTextColor(android.graphics.Color.parseColor("#2563EB")) // Blue
                binding.cardBadge.setCardBackgroundColor(android.graphics.Color.parseColor("#DBEAFE")) // Light Blue
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Announcement>() {
        override fun areItemsTheSame(oldItem: Announcement, newItem: Announcement) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Announcement, newItem: Announcement) = oldItem == newItem
    }
}
