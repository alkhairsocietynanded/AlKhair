package com.aewsn.alkhair.ui.homework

import android.text.TextUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aewsn.alkhair.R
import com.aewsn.alkhair.data.models.Homework
import com.aewsn.alkhair.databinding.ItemHomeworkBinding

class HomeworkAdapter(
    private val onEdit: ((Homework) -> Unit)? = null,
    private val onDelete: ((Homework) -> Unit)? = null
) : ListAdapter<Homework, HomeworkAdapter.HomeworkViewHolder>(DiffCallback()) {

    inner class HomeworkViewHolder(private val binding: ItemHomeworkBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(homework: Homework) {
            with(binding) {
                // Bind the new fields from the updated layout
                tvTitle.text = homework.title
                tvSubject.text = homework.subject
                tvClass.text = homework.className
                tvDivision.text = homework.divisionName
                tvDate.text = homework.date
                tvDescription.text = homework.description

                // Show the attachment icon only if a file URL exists
                ivAttachment.isVisible = !homework.attachmentUrl.isNullOrBlank()

                // Show "More" button only if callbacks are provided (Edit Mode)
                if (onEdit != null && onDelete != null) {
                    btnMore.isVisible = true
                    btnMore.setOnClickListener { view ->
                        val popup = PopupMenu(view.context, view)
                        popup.menuInflater.inflate(R.menu.menu_item_actions, popup.menu)
                        popup.setOnMenuItemClickListener { item ->
                            when (item.itemId) {
                                R.id.action_edit -> {
                                    onEdit.invoke(homework)
                                    true
                                }

                                R.id.action_delete -> {
                                    onDelete.invoke(homework)
                                    true
                                }

                                else -> false
                            }
                        }
                        popup.show()
                    }
                } else {
                    // Read-Only Mode
                    btnMore.isVisible = false
                }

                // Toggle description maxLines on click
                tvDescription.setOnClickListener {
                    if (tvDescription.maxLines == 2) {
                        tvDescription.maxLines = Integer.MAX_VALUE
                        tvDescription.ellipsize = null
                    } else {
                        tvDescription.maxLines = 2
                        tvDescription.ellipsize = TextUtils.TruncateAt.END
                    }
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HomeworkViewHolder {
        val binding =
            ItemHomeworkBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HomeworkViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HomeworkViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class DiffCallback : DiffUtil.ItemCallback<Homework>() {
        override fun areItemsTheSame(oldItem: Homework, newItem: Homework) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Homework, newItem: Homework) =
            oldItem == newItem
    }
}
