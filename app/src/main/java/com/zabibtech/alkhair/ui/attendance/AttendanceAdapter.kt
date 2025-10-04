package com.zabibtech.alkhair.ui.attendance

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.zabibtech.alkhair.data.models.User
import com.zabibtech.alkhair.databinding.ItemAttendanceBinding

class AttendanceAdapter(
    private val onClick: (User) -> Unit
) : ListAdapter<User, AttendanceAdapter.AttendanceViewHolder>(DiffCallback) {

    private val attendanceMap = mutableMapOf<String, String>() // uid -> status
    private var fullList: List<User> = emptyList()

    // Listener to notify when all attendance marked
    var attendanceCompleteListener: ((Boolean) -> Unit)? = null

    inner class AttendanceViewHolder(val binding: ItemAttendanceBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(user: User) {
            binding.apply {
                tvName.text = user.name
                tvEmail.text = user.email
                tvRole.text = user.role

                // Clear old listener
                rgAttendance.setOnCheckedChangeListener(null)

                // Restore selection if exists
                when (attendanceMap[user.uid]) {
                    "Present" -> {
                        rbPresent.isChecked = true
                        setRadioColors("Present")
                    }

                    "Absent" -> {
                        rbAbsent.isChecked = true
                        setRadioColors("Absent")
                    }

                    "Leave" -> {
                        rbLeave.isChecked = true
                        setRadioColors("Leave")
                    }

                    else -> {
                        rgAttendance.clearCheck()
                        resetRadioColors()
                    }
                }

                // Update map on change
                rgAttendance.setOnCheckedChangeListener { _, checkedId ->
                    val status = when (checkedId) {
                        rbPresent.id -> "Present"
                        rbAbsent.id -> "Absent"
                        rbLeave.id -> "Leave"
                        else -> null
                    }
                    if (status != null) {
                        attendanceMap[user.uid] = status
                        setRadioColors(status) // ✅ Color change here
                        attendanceCompleteListener?.invoke(isAttendanceComplete())
                    }
                }

                root.setOnClickListener { onClick(user) }
            }
        }

        // --- Helper functions ---
        private fun ItemAttendanceBinding.setRadioColors(status: String) {
            when (status) {
                "Present" -> {
                    rbPresent.setTextColor(root.context.getColor(android.R.color.holo_green_dark))
                    rbAbsent.setTextColor(root.context.getColor(android.R.color.darker_gray))
                    rbLeave.setTextColor(root.context.getColor(android.R.color.darker_gray))
                }

                "Absent" -> {
                    rbPresent.setTextColor(root.context.getColor(android.R.color.darker_gray))
                    rbAbsent.setTextColor(root.context.getColor(android.R.color.holo_red_dark))
                    rbLeave.setTextColor(root.context.getColor(android.R.color.darker_gray))
                }

                "Leave" -> {
                    rbPresent.setTextColor(root.context.getColor(android.R.color.darker_gray))
                    rbAbsent.setTextColor(root.context.getColor(android.R.color.darker_gray))
                    rbLeave.setTextColor(root.context.getColor(android.R.color.holo_orange_dark))
                }
            }
        }

        private fun ItemAttendanceBinding.resetRadioColors() {
            rbPresent.setTextColor(root.context.getColor(android.R.color.black))
            rbAbsent.setTextColor(root.context.getColor(android.R.color.black))
            rbLeave.setTextColor(root.context.getColor(android.R.color.black))
        }
    }

    fun clearAttendance() {
        attendanceMap.clear()
        notifyDataSetChanged() // ✅ Taaki UI se bhi radio clear ho jaye
        attendanceCompleteListener?.invoke(false) // ✅ FAB hide karne ke liye
    }

    fun setAttendanceMap(prefilled: Map<String, String>) {
        attendanceMap.clear()
        attendanceMap.putAll(prefilled)
        notifyDataSetChanged()
        attendanceCompleteListener?.invoke(isAttendanceComplete())
    }

    fun isAttendanceComplete(): Boolean {
        return fullList.all { user -> attendanceMap.containsKey(user.uid) }
    }

    fun getAttendanceMap(): Map<String, String> = attendanceMap

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AttendanceViewHolder {
        val binding =
            ItemAttendanceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AttendanceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AttendanceViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun submitList(list: List<User>?) {
        super.submitList(list)
        if (list != null) fullList = list
        attendanceCompleteListener?.invoke(isAttendanceComplete())
    }

    object DiffCallback : DiffUtil.ItemCallback<User>() {
        override fun areItemsTheSame(oldItem: User, newItem: User) = oldItem.uid == newItem.uid
        override fun areContentsTheSame(oldItem: User, newItem: User) = oldItem == newItem
    }
}
