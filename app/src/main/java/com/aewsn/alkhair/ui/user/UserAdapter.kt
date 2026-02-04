package com.aewsn.alkhair.ui.user

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aewsn.alkhair.R
import com.aewsn.alkhair.data.models.User
import com.aewsn.alkhair.databinding.ItemUserBinding
import com.aewsn.alkhair.utils.Shift

class UserAdapter(
    private val onEdit: (User) -> Unit,
    private val onDelete: (User) -> Unit,
    private val onClick: (User) -> Unit,
) : ListAdapter<User, UserAdapter.UserViewHolder>(DiffCallback) {

    // ❌ Removed: Filterable implementation, fullList, and getFilter().
    // The ViewModel now filters the data and passes the final list here.

    inner class UserViewHolder(private val binding: ItemUserBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(user: User) = with(binding) {
            tvName.text = user.name
            tvEmail.text = user.email
            tvPhone.text = user.phone

            tvClass.text = user.className
            tvDivision.text = user.divisionName
            chipShift.text = user.shift

            // Set Shift Color
            val shiftColor = when (user.shift.lowercase()) {
                Shift.SUBAH.lowercase() -> ContextCompat.getColor(root.context, R.color.subah)
                Shift.DOPAHAR.lowercase() -> ContextCompat.getColor(root.context, R.color.dopahar)
                Shift.SHAAM.lowercase() -> ContextCompat.getColor(root.context, R.color.shaam)
                else -> ContextCompat.getColor(root.context, R.color.md_theme_error)
            }
            chipShift.chipBackgroundColor = ColorStateList.valueOf(shiftColor)

            // Popup Menu
            btnMore.setOnClickListener { view ->
                val popup = PopupMenu(view.context, view)
                popup.menuInflater.inflate(R.menu.menu_user_item, popup.menu)
                popup.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.action_edit -> {
                            onEdit(user)
                            true
                        }

                        R.id.action_delete -> {
                            onDelete(user)
                            true
                        }

                        else -> false
                    }
                }
                popup.show()
            }

            root.setOnClickListener { onClick(user) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val binding = ItemUserBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return UserViewHolder(binding)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    object DiffCallback : DiffUtil.ItemCallback<User>() {
        override fun areItemsTheSame(oldItem: User, newItem: User) = oldItem.uid == newItem.uid
        override fun areContentsTheSame(oldItem: User, newItem: User) = oldItem == newItem
    }
}