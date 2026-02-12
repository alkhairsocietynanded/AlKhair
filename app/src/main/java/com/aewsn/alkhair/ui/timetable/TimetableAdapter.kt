package com.aewsn.alkhair.ui.timetable

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aewsn.alkhair.data.models.Timetable
import com.aewsn.alkhair.databinding.ItemTimetableSlotBinding

class TimetableAdapter(
    private val isAdmin: Boolean,
    private val onDeleteClick: (Timetable) -> Unit
) : ListAdapter<Timetable, TimetableAdapter.TimetableViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TimetableViewHolder {
        val binding = ItemTimetableSlotBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TimetableViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TimetableViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    inner class TimetableViewHolder(val binding: ItemTimetableSlotBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Timetable) {
            binding.tvTime.text = "${item.startTime} - ${item.endTime}"
            binding.tvSubject.text = item.subjectName
            
            // Show teacher if available, else hint
            binding.tvTeacher.text = if (item.teacherName.isNotEmpty()) item.teacherName else "No Teacher"
            
            binding.tvRoom.text = item.roomNo
            
            binding.btnDeleteSlot.isVisible = isAdmin
            binding.btnDeleteSlot.setOnClickListener { 
                onDeleteClick(item)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Timetable>() {
        override fun areItemsTheSame(oldItem: Timetable, newItem: Timetable): Boolean {
           return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Timetable, newItem: Timetable): Boolean {
            return oldItem == newItem
        }
    }
}
