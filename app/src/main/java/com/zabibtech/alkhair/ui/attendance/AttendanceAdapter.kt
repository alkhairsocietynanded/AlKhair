package com.zabibtech.alkhair.ui.attendance

import android.view.LayoutInflater
import android.view.View
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

    // Listener to notify when all attendance is marked
    var attendanceCompleteListener: ((Boolean) -> Unit)? = null

    inner class AttendanceViewHolder(val binding: ItemAttendanceBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(user: User) {
            binding.apply {
                tvName.text = user.name
                tvEmail.text = user.email
                chipRole.text = user.role // Updated to use Chip

                // Clear previous listener to avoid multiple triggers on recycled views
                toggleButtonGroup.clearOnButtonCheckedListeners()

                // Restore selection state without triggering the listener
                when (attendanceMap[user.uid]) {
                    "Present" -> toggleButtonGroup.check(btnPresent.id)
                    "Absent" -> toggleButtonGroup.check(btnAbsent.id)
                    "Leave" -> toggleButtonGroup.check(btnLeave.id)
                    else -> toggleButtonGroup.clearChecked()
                }

                // Set the listener for user interactions
                toggleButtonGroup.addOnButtonCheckedListener { group, checkedId, isChecked ->
                    if (isChecked) {
                        // A button was checked
                        val status = when (checkedId) {
                            btnPresent.id -> "Present"
                            btnAbsent.id -> "Absent"
                            btnLeave.id -> "Leave"
                            else -> null
                        }
                        status?.let { attendanceMap[user.uid] = it }
                    } else {
                        // A button was unchecked. If no button is selected now, remove from map.
                        if (group.checkedButtonId == View.NO_ID) {
                            attendanceMap.remove(user.uid)
                        }
                    }
                    // Notify the activity/fragment that the attendance state might have changed
                    attendanceCompleteListener?.invoke(isAttendanceComplete())
                }

                root.setOnClickListener { onClick(user) }
            }
        }
    }

    // --- Public helper functions ---

    fun clearAttendance() {
        attendanceMap.clear()
        notifyDataSetChanged() // To update the UI and clear selections
        attendanceCompleteListener?.invoke(false) // Hide FAB
    }

    fun setAttendanceMap(prefilled: Map<String, String>) {
        attendanceMap.clear()
        attendanceMap.putAll(prefilled)
        notifyDataSetChanged()
        attendanceCompleteListener?.invoke(isAttendanceComplete())
    }

    fun isAttendanceComplete(): Boolean {
        if (fullList.isEmpty()) return false
        return fullList.all { user -> attendanceMap.containsKey(user.uid) }
    }

    fun getAttendanceMap(): Map<String, String> = attendanceMap

    // --- ViewHolder and DiffUtil boilerplate ---

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
        // Initial check when the list is submitted
        attendanceCompleteListener?.invoke(isAttendanceComplete())
    }

    object DiffCallback : DiffUtil.ItemCallback<User>() {
        override fun areItemsTheSame(oldItem: User, newItem: User) = oldItem.uid == newItem.uid
        override fun areContentsTheSame(oldItem: User, newItem: User) = oldItem == newItem
    }
}
