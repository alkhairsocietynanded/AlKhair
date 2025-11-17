package com.zabibtech.alkhair.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.zabibtech.alkhair.databinding.ItemAnnouncementBinding
import com.zabibtech.alkhair.model.Announcement

class AnnouncementPagerAdapter(private val announcements: List<Announcement>) :
    RecyclerView.Adapter<AnnouncementPagerAdapter.AnnouncementViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AnnouncementViewHolder {
        val binding = ItemAnnouncementBinding.inflate(LayoutInflater.from(parent.context), parent, false)
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
        }
    }
}