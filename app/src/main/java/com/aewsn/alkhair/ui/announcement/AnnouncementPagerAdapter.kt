package com.aewsn.alkhair.ui.announcement

import android.text.TextUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.aewsn.alkhair.data.models.Announcement
import com.aewsn.alkhair.databinding.ItemAnnouncementBinding
import com.aewsn.alkhair.utils.DateUtils

class AnnouncementPagerAdapter(
    private var announcements: MutableList<Announcement>,
    private val onDelete: (Announcement) -> Unit,
) :
    RecyclerView.Adapter<AnnouncementPagerAdapter.AnnouncementViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AnnouncementViewHolder {
        val binding =
            ItemAnnouncementBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AnnouncementViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AnnouncementViewHolder, position: Int) {
        holder.bind(announcements[position])
    }

    override fun getItemCount(): Int = announcements.size

    inner class AnnouncementViewHolder(private val binding: ItemAnnouncementBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(announcement: Announcement) {
            binding.tvAnnouncementTitle.text = announcement.title
            binding.tvAnnouncementContent.text = announcement.content
            binding.tvTimeStamp.text =
                DateUtils.convertTimestampToStringDate(announcement.timestamp)

            binding.deleteButton.setOnClickListener {
                onDelete(announcement)
            }

            // Toggle description maxLines on click
            binding.tvAnnouncementContent.setOnClickListener {
                if (binding.tvAnnouncementContent.maxLines == 3) {
                    binding.tvAnnouncementContent.maxLines = Integer.MAX_VALUE
                    binding.tvAnnouncementContent.ellipsize = null
                } else {
                    binding.tvAnnouncementContent.maxLines = 3
                    binding.tvAnnouncementContent.ellipsize = TextUtils.TruncateAt.END
                }
            }
        }
    }

    fun updateData(newData: List<Announcement>) {
        announcements.clear()
        announcements.addAll(newData)
        notifyDataSetChanged()
    }
}