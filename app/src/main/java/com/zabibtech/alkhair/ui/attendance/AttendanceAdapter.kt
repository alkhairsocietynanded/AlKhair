package com.zabibtech.alkhair.ui.attendance

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.zabibtech.alkhair.R
import com.zabibtech.alkhair.data.models.User
import com.zabibtech.alkhair.databinding.ItemAttendanceBinding

class AttendanceAdapter(
    private val onClick: (User) -> Unit
) : ListAdapter<User, AttendanceAdapter.AttendanceViewHolder>(DiffCallback) {

    private val attendanceMap = mutableMapOf<String, String>() // uid -> status
    private var fullList: List<User> = emptyList()
    private var hasUserMadeChanges = false // Track if user has modified attendance

    // Listener to notify when all attendance is marked
    var attendanceCompleteListener: ((Boolean) -> Unit)? = null

    inner class AttendanceViewHolder(val binding: ItemAttendanceBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(user: User) {
            binding.apply {
                tvName.text = user.name
                tvEmail.text = user.email
                chipShift.text = user.shift
                val shiftColor = when (user.shift.lowercase()) {
                    "subah".lowercase() -> ContextCompat.getColor(
                        binding.root.context,
                        R.color.subah
                    )

                    "dopahar".lowercase() -> ContextCompat.getColor(
                        binding.root.context,
                        R.color.dopahar
                    )

                    else -> ContextCompat.getColor(
                        binding.root.context,
                        R.color.shaam
                    )
                }
                chipShift.chipBackgroundColor = ColorStateList.valueOf(shiftColor)

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
                    // Mark that user has made changes
                    hasUserMadeChanges = true
                    // Notify the activity/fragment that the attendance state might have changed
                    attendanceCompleteListener?.invoke(isAttendanceComplete() && hasUserMadeChanges)
                }

                root.setOnClickListener { onClick(user) }
            }
        }
    }

    // --- Public helper functions ---

    fun clearAttendance() {
        attendanceMap.clear()
        hasUserMadeChanges = false
        notifyDataSetChanged() // To update the UI and clear selections
        attendanceCompleteListener?.invoke(false) // Hide FAB
    }

    fun setAttendanceMap(prefilled: Map<String, String>) {
        attendanceMap.clear()
        attendanceMap.putAll(prefilled)
        hasUserMadeChanges = false // Reset flag when loading from DB
        notifyDataSetChanged()
        // Don't show FAB on initial load
        attendanceCompleteListener?.invoke(false)
    }

    fun isAttendanceComplete(): Boolean {
        if (fullList.isEmpty()) return false
        return fullList.all { user -> attendanceMap.containsKey(user.uid) }
    }

    fun getAttendanceMap(): Map<String, String> = attendanceMap

    fun getAttendanceSummary(): Triple<Int, Int, Int> {
        val present = fullList.count { attendanceMap[it.uid] == "Present" }
        val absent = fullList.count { attendanceMap[it.uid] == "Absent" }
        val leave = fullList.count { attendanceMap[it.uid] == "Leave" }
        return Triple(present, absent, leave)
    }

    fun resetChangeFlag() {
        hasUserMadeChanges = false
    }

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
