package com.zabibtech.alkhair.ui.user

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.zabibtech.alkhair.R
import com.zabibtech.alkhair.data.models.User
import com.zabibtech.alkhair.databinding.ItemUserBinding
import java.util.Locale

class UserAdapter(
    private val onEdit: (User) -> Unit,
    private val onDelete: (User) -> Unit,
    private val onClick: (User) -> Unit,
) : ListAdapter<User, UserAdapter.UserViewHolder>(DiffCallback), Filterable {

    private var fullList: List<User> = emptyList()

    // This method is called from the UI Controller to set the base list for filtering.
    fun setFullList(list: List<User>?) {
        fullList = list ?: emptyList()
        submitList(list) // Update the displayed list.
    }

    inner class UserViewHolder(private val binding: ItemUserBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(user: User) = with(binding) {
            tvName.text = user.name
            tvEmail.text = user.email
            tvPhone.text = user.phone
            tvRole.text = user.role

            tvClassName.text = user.className
            tvDivisionName.text = user.divisionName
            tvShift.text = user.shift
            tvStatus.text = if (user.isActive) "Active" else "Inactive"

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
        val binding = ItemUserBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return UserViewHolder(binding)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun submitList(list: List<User>?) {
        super.submitList(list)
        if (list != null) fullList = list
    }

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val query = constraint?.toString()?.lowercase(Locale.getDefault()) ?: ""
                val filtered = if (query.isEmpty()) fullList else fullList.filter {
                    it.name.lowercase(Locale.getDefault()).contains(query) ||
                            it.email.lowercase(Locale.getDefault()).contains(query) ||
                            it.phone.contains(query)
                }
                return FilterResults().apply { values = filtered }
            }

            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                super@UserAdapter.submitList(results?.values as? List<User> ?: emptyList())
            }
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<User>() {
        override fun areItemsTheSame(oldItem: User, newItem: User) = oldItem.uid == newItem.uid
        override fun areContentsTheSame(oldItem: User, newItem: User) = oldItem == newItem
    }
}
