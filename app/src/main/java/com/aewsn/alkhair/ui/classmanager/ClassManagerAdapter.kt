package com.aewsn.alkhair.ui.classmanager

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aewsn.alkhair.R
import com.aewsn.alkhair.data.models.ClassModel
import com.aewsn.alkhair.databinding.ItemClassBinding
import com.aewsn.alkhair.databinding.ItemDivisionHeaderBinding

// Sealed class for heterogeneous list (Headers + Items)
sealed class ClassListItem {
    data class Header(val divisionName: String) : ClassListItem()
    data class ClassItem(val classModel: ClassModel) : ClassListItem()
}

class ClassManagerAdapter(
    private val onEdit: (ClassModel) -> Unit,
    private val onDelete: (ClassModel) -> Unit,
    private val onClick: (ClassModel) -> Unit
) : ListAdapter<ClassListItem, RecyclerView.ViewHolder>(ClassDiffCallback) {

    companion object {
        const val VIEW_TYPE_HEADER = 0
        const val VIEW_TYPE_ITEM = 1
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is ClassListItem.Header -> VIEW_TYPE_HEADER
            is ClassListItem.ClassItem -> VIEW_TYPE_ITEM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                val binding = ItemDivisionHeaderBinding.inflate(inflater, parent, false)
                HeaderViewHolder(binding)
            }
            VIEW_TYPE_ITEM -> {
                val binding = ItemClassBinding.inflate(inflater, parent, false)
                ClassViewHolder(binding)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is ClassListItem.Header -> (holder as HeaderViewHolder).bind(item)
            is ClassListItem.ClassItem -> (holder as ClassViewHolder).bind(item)
        }
    }

    // --- ViewHolders ---

    inner class HeaderViewHolder(private val binding: ItemDivisionHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(headerItem: ClassListItem.Header) {
            binding.tvDivisionName.text = headerItem.divisionName
        }
    }

    inner class ClassViewHolder(private val binding: ItemClassBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(classItem: ClassListItem.ClassItem) {
            val classModel = classItem.classModel
            binding.tvClassName.text = classModel.className

            // Navigate on Click
            binding.root.setOnClickListener { onClick(classModel) }

            // Context Menu on Long Click
            binding.root.setOnLongClickListener { view ->
                showPopupMenu(view, classModel)
                true
            }
        }

        private fun showPopupMenu(view: android.view.View, classModel: ClassModel) {
            val popup = PopupMenu(view.context, view)
            popup.menuInflater.inflate(R.menu.menu_item_actions, popup.menu)
            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_edit -> {
                        onEdit(classModel)
                        true
                    }
                    R.id.action_delete -> {
                        onDelete(classModel)
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }

    // --- DiffUtil ---

    object ClassDiffCallback : DiffUtil.ItemCallback<ClassListItem>() {
        override fun areItemsTheSame(oldItem: ClassListItem, newItem: ClassListItem): Boolean {
            return when {
                oldItem is ClassListItem.Header && newItem is ClassListItem.Header ->
                    oldItem.divisionName == newItem.divisionName
                oldItem is ClassListItem.ClassItem && newItem is ClassListItem.ClassItem ->
                    oldItem.classModel.id == newItem.classModel.id
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: ClassListItem, newItem: ClassListItem): Boolean {
            // Since ClassListItem uses data classes, we can rely on standard equality ==
            return oldItem == newItem
        }
    }
}