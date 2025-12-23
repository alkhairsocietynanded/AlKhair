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
import com.zabibtech.alkhair.databinding.ItemAttendanceBinding
import com.zabibtech.alkhair.utils.Shift

class AttendanceAdapter(
    private val onStatusChange: (String, String) -> Unit
) : ListAdapter<AttendanceUiModel, AttendanceAdapter.AttendanceViewHolder>(DiffCallback) {

    inner class AttendanceViewHolder(val binding: ItemAttendanceBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: AttendanceUiModel) = with(binding) {
            val user = item.user

            // 1. Bind User Data
            tvName.text = user.name
            tvEmail.text = user.email
            chipShift.text = user.shift

            // 2. Set Shift Color
            val shiftColorRes = when (user.shift.lowercase()) {
                Shift.SUBAH.lowercase() -> R.color.subah
                Shift.DOPAHAR.lowercase() -> R.color.dopahar
                Shift.SHAAM.lowercase() -> R.color.shaam
                else -> R.color.md_theme_error
            }
            chipShift.chipBackgroundColor = ColorStateList.valueOf(
                ContextCompat.getColor(root.context, shiftColorRes)
            )

            // 3. Handle Button State
            // IMPORTANT: Clear listeners before setting state to avoid infinite loops or wrong callbacks
            toggleButtonGroup.clearOnButtonCheckedListeners()

            // Set the checked button based on the status from ViewModel
            when (item.status) {
                "Present" -> toggleButtonGroup.check(btnPresent.id)
                "Absent" -> toggleButtonGroup.check(btnAbsent.id)
                "Leave" -> toggleButtonGroup.check(btnLeave.id)
                else -> toggleButtonGroup.clearChecked() // No status (null/empty)
            }

            // 4. Attach Listener for User Interaction
            toggleButtonGroup.addOnButtonCheckedListener { group, checkedId, isChecked ->
                if (isChecked) {
                    val status = when (checkedId) {
                        btnPresent.id -> "Present"
                        btnAbsent.id -> "Absent"
                        btnLeave.id -> "Leave"
                        else -> ""
                    }
                    // Notify ViewModel
                    onStatusChange(user.uid, status)
                } else {
                    // If the user unchecks the currently selected button (resulting in NO selection)
                    if (group.checkedButtonId == View.NO_ID) {
                        onStatusChange(user.uid, "") // Send empty string to remove status
                    }
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AttendanceViewHolder {
        val binding =
            ItemAttendanceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AttendanceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AttendanceViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    object DiffCallback : DiffUtil.ItemCallback<AttendanceUiModel>() {
        override fun areItemsTheSame(
            oldItem: AttendanceUiModel,
            newItem: AttendanceUiModel
        ): Boolean {
            // Same User ID
            return oldItem.user.uid == newItem.user.uid
        }

        override fun areContentsTheSame(
            oldItem: AttendanceUiModel,
            newItem: AttendanceUiModel
        ): Boolean {
            // Data Class equality check (compares User data AND Status)
            return oldItem == newItem
        }
    }
}