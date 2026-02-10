package com.aewsn.alkhair.ui.syllabus

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aewsn.alkhair.data.models.Syllabus
import com.aewsn.alkhair.databinding.ItemSyllabusViewBinding

class SyllabusAdapter(
    private val onAction: (Syllabus, String) -> Unit
) : ListAdapter<Syllabus, SyllabusAdapter.SyllabusViewHolder>(SyllabusDiffCallback()) {

    var isEditEnabled: Boolean = false
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SyllabusViewHolder {
        val binding = ItemSyllabusViewBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SyllabusViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SyllabusViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class SyllabusViewHolder(private val binding: ItemSyllabusViewBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: Syllabus) {
            binding.tvSubject.text = item.subject
            binding.tvTopic.text = item.topic
            binding.tvDescription.text = item.description
            
            // Set default thumbnail. If using Coil/Glide, load image here.
            binding.ivThumbnail.setImageResource(com.aewsn.alkhair.R.drawable.ic_book)
            
            binding.btnDownload.isVisible = !item.attachmentUrl.isNullOrBlank()
            binding.btnDownload.setOnClickListener {
                onAction(item, "download")
            }

            binding.btnMore.isVisible = isEditEnabled
            binding.btnMore.setOnClickListener { view ->
                showPopupMenu(view, item)
            }
        }

        private fun showPopupMenu(view: android.view.View, item: Syllabus) {
            val popup = android.widget.PopupMenu(view.context, view)
            popup.menuInflater.inflate(com.aewsn.alkhair.R.menu.menu_edit_delete, popup.menu)
            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    com.aewsn.alkhair.R.id.action_edit -> {
                        onAction(item, "edit")
                        true
                    }
                    com.aewsn.alkhair.R.id.action_delete -> {
                        onAction(item, "delete")
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }

    class SyllabusDiffCallback : DiffUtil.ItemCallback<Syllabus>() {
        override fun areItemsTheSame(oldItem: Syllabus, newItem: Syllabus): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Syllabus, newItem: Syllabus): Boolean {
            return oldItem == newItem
        }
    }
}
