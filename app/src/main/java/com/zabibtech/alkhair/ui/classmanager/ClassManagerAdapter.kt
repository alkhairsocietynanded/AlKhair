package com.zabibtech.alkhair.ui.classmanager

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import com.zabibtech.alkhair.R
import com.zabibtech.alkhair.data.models.ClassModel
import com.zabibtech.alkhair.databinding.ItemClassBinding

class ClassManagerAdapter(
    private var items: List<ClassModel>,
    private val onEdit: (ClassModel) -> Unit,
    private val onDelete: (ClassModel) -> Unit,
    private val onClick: (ClassModel) -> Unit
) : RecyclerView.Adapter<ClassManagerAdapter.ClassViewHolder>() {

    inner class ClassViewHolder(val binding: ItemClassBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClassViewHolder {
        val binding = ItemClassBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ClassViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ClassViewHolder, position: Int) {
        val item = items[position]
        holder.binding.apply {
            tvClassName.text = item.className
            tvDivisionName.text = item.division

            // 🔹 3-dot menu popup
            btnMore.setOnClickListener { view ->
                val popup = PopupMenu(view.context, view)
                popup.menuInflater.inflate(R.menu.menu_class_item, popup.menu)
                popup.setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        R.id.action_edit -> { onEdit(item); true }
                        R.id.action_delete -> { onDelete(item); true }
                        else -> false
                    }
                }
                popup.show()
            }

            root.setOnClickListener { onClick(item) }
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateList(newList: List<ClassModel>) {
        items = newList
        notifyDataSetChanged()
    }
}
