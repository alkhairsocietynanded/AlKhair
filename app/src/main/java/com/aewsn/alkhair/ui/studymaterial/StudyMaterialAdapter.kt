package com.aewsn.alkhair.ui.studymaterial

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aewsn.alkhair.data.models.StudyMaterial
import com.aewsn.alkhair.databinding.ItemStudyMaterialBinding

class StudyMaterialAdapter(
    private val onAction: (StudyMaterial, String) -> Unit
) : ListAdapter<StudyMaterial, StudyMaterialAdapter.StudyMaterialViewHolder>(StudyMaterialDiffCallback()) {

    var isEditEnabled: Boolean = false
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StudyMaterialViewHolder {
        val binding = ItemStudyMaterialBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return StudyMaterialViewHolder(binding)
    }

    override fun onBindViewHolder(holder: StudyMaterialViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class StudyMaterialViewHolder(private val binding: ItemStudyMaterialBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: StudyMaterial) {
            binding.tvTitle.text = item.title
            binding.tvSubject.text = item.subject
            binding.tvDescription.text = item.description
            binding.chipType.text = item.materialType

            // Make Link chip clickable to open URL
            if (item.materialType == "Link" && !item.attachmentUrl.isNullOrBlank()) {
                binding.chipType.isClickable = true
                binding.chipType.setOnClickListener {
                    onAction(item, "open_link")
                }
            } else {
                binding.chipType.isClickable = false
                binding.chipType.setOnClickListener(null)
            }

            // Set icon based on material type
            val iconRes = when (item.materialType) {
                "PDF" -> com.aewsn.alkhair.R.drawable.ic_book
                "Notes" -> com.aewsn.alkhair.R.drawable.ic_study_material
                "Video" -> com.aewsn.alkhair.R.drawable.ic_study_material
                "Image" -> com.aewsn.alkhair.R.drawable.ic_image
                "Link" -> com.aewsn.alkhair.R.drawable.ic_link
                else -> com.aewsn.alkhair.R.drawable.ic_book
            }
            binding.ivThumbnail.setImageResource(iconRes)

            // Show button only when attachmentUrl exists
            binding.btnDownload.isVisible = !item.attachmentUrl.isNullOrBlank()

            if (item.materialType == "Link") {
                // Link type: show link icon and open in browser
                binding.btnDownload.setImageResource(com.aewsn.alkhair.R.drawable.ic_link)
                binding.btnDownload.setOnClickListener {
                    onAction(item, "open_link")
                }
            } else {
                // File types: show download icon
                binding.btnDownload.setImageResource(com.aewsn.alkhair.R.drawable.ic_download)
                binding.btnDownload.setOnClickListener {
                    onAction(item, "download")
                }
            }

            binding.btnMore.isVisible = isEditEnabled
            binding.btnMore.setOnClickListener { view ->
                showPopupMenu(view, item)
            }
        }

        private fun showPopupMenu(view: android.view.View, item: StudyMaterial) {
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

    class StudyMaterialDiffCallback : DiffUtil.ItemCallback<StudyMaterial>() {
        override fun areItemsTheSame(oldItem: StudyMaterial, newItem: StudyMaterial): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: StudyMaterial, newItem: StudyMaterial): Boolean {
            return oldItem == newItem
        }
    }
}
