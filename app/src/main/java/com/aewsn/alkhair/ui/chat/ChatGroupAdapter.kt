package com.aewsn.alkhair.ui.chat

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.aewsn.alkhair.R
import com.aewsn.alkhair.databinding.ItemChatGroupBinding

class ChatGroupAdapter(
    private val onGroupClicked: (ChatGroup) -> Unit
) : RecyclerView.Adapter<ChatGroupAdapter.GroupViewHolder>() {

    private val groups = mutableListOf<ChatGroup>()

    fun submitList(list: List<ChatGroup>) {
        groups.clear()
        groups.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupViewHolder {
        val binding = ItemChatGroupBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return GroupViewHolder(binding)
    }

    override fun onBindViewHolder(holder: GroupViewHolder, position: Int) {
        holder.bind(groups[position])
    }

    override fun getItemCount(): Int = groups.size

    inner class GroupViewHolder(
        private val binding: ItemChatGroupBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(group: ChatGroup) {
            binding.tvGroupName.text = group.groupName
            binding.tvGroupSubtitle.text = group.subtitle

            // Different icon for teachers vs class group
            val iconRes = if (group.groupType == "teachers") {
                R.drawable.ic_teacher
            } else {
                R.drawable.ic_classes
            }
            binding.ivGroupIcon.setImageResource(iconRes)

            binding.root.setOnClickListener {
                onGroupClicked(group)
            }
        }
    }
}
