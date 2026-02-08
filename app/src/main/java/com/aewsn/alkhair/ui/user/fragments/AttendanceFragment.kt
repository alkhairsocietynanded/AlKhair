package com.aewsn.alkhair.ui.user.fragments

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.children
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.CalendarMonth
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.view.MonthDayBinder
import com.kizitonwose.calendar.view.MonthHeaderFooterBinder
import com.kizitonwose.calendar.view.ViewContainer
import com.aewsn.alkhair.R
import com.aewsn.alkhair.data.models.User
import com.aewsn.alkhair.databinding.CalendarDayLayoutBinding
import com.aewsn.alkhair.databinding.CalendarMonthHeaderBinding
import com.aewsn.alkhair.databinding.FragmentAttendanceBinding
import com.aewsn.alkhair.utils.DialogUtils
import com.aewsn.alkhair.utils.UiState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@AndroidEntryPoint
class AttendanceFragment : Fragment() {

    private var _binding: FragmentAttendanceBinding? = null
    private val binding get() = _binding!!

    private var user: User? = null

    private val viewModel: UserAttendanceViewModel by viewModels()

    companion object {
        private const val ARG_USER = "arg_user"
        fun newInstance(user: User) = AttendanceFragment().apply {
            arguments = Bundle().apply {
                putParcelable(ARG_USER, user)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        user = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arguments?.getParcelable(ARG_USER, User::class.java)
        } else {
            arguments?.getParcelable(ARG_USER)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAttendanceBinding.inflate(inflater, container, false)
        user?.let { viewModel.loadAttendanceForUser(it.uid) }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val currentMonth = YearMonth.now()
        val firstMonth = currentMonth.minusMonths(12)
        val lastMonth = currentMonth.plusMonths(12)
        val firstDayOfWeek = DayOfWeek.MONDAY

        binding.calendarView.setup(firstMonth, lastMonth, firstDayOfWeek)
        binding.calendarView.scrollToMonth(currentMonth)

        binding.calendarView.monthHeaderResource = R.layout.calendar_month_header
        binding.calendarView.monthHeaderBinder =
            object : MonthHeaderFooterBinder<MonthHeaderContainer> {
                override fun create(view: View) = MonthHeaderContainer(view)

                override fun bind(container: MonthHeaderContainer, data: CalendarMonth) {
                    val title =
                        data.yearMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) +
                                " " + data.yearMonth.year
                    container.titleText.text = title

                    if (container.weekDaysContainer.tag == null) {
                        container.weekDaysContainer.tag = true
                        val daysOfWeek = data.weekDays.first().map { it.date.dayOfWeek }
                        container.weekDaysContainer.children
                            .filterIsInstance<TextView>()
                            .forEachIndexed { index, textView ->
                                val dayOfWeek = daysOfWeek[index]
                                val shortName =
                                    dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                                textView.text = shortName
                            }
                    }
                }
            }

        // Initial day binder setup
        binding.calendarView.dayBinder = object : MonthDayBinder<DayViewContainer> {
            override fun create(view: View) = DayViewContainer(view)
            override fun bind(container: DayViewContainer, data: CalendarDay) {
                container.textView.text = data.date.dayOfMonth.toString()
                container.textView.background = null
                container.textView.alpha = if (data.position == DayPosition.MonthDate) 1f else 0.3f
            }
        }

        // Initialize Adapter
        val leaveAdapter = com.aewsn.alkhair.ui.student.adapter.LeaveHistoryAdapter(
            onEditClick = { leave ->
                val bottomSheet = com.aewsn.alkhair.ui.student.leave.ApplyLeaveBottomSheet.newInstance(leave)
                bottomSheet.show(parentFragmentManager, com.aewsn.alkhair.ui.student.leave.ApplyLeaveBottomSheet.TAG)
            },
            onDeleteClick = { leave ->
                com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Delete Application")
                    .setMessage("Are you sure you want to delete this leave application?")
                    .setPositiveButton("Delete") { _, _ ->
                        viewModel.deleteLeave(leave)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )
        binding.rvLeaveHistory.apply {
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
            adapter = leaveAdapter
        }

        // Observe the ViewModel for attendance and leaves
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.userAttendance.collectLatest { state ->
                        when (state) {
                            is UiState.Loading -> showLoading(true)
                            is UiState.Success -> {
                                showLoading(false)

                                // Correctly process the List<Attendance>
                                val attendanceMap = mutableMapOf<LocalDate, String>()
                                var presentCount = 0
                                var absentCount = 0
                                var leaveCount = 0

                                state.data.forEach { attendanceRecord ->
                                    try {
                                        val date = LocalDate.parse(attendanceRecord.date) // "yyyy-MM-dd" format
                                        attendanceMap[date] = attendanceRecord.status
                                        
                                        when (attendanceRecord.status) {
                                            "Present" -> presentCount++
                                            "Absent" -> absentCount++
                                            "Leave" -> leaveCount++
                                        }
                                    } catch (e: Exception) {
                                        Log.e("AttendanceFragment", "Invalid date format: ${attendanceRecord.date}", e)
                                    }
                                }

                                // Update Dashboard UI (Overall Attendance)
                                val totalDays = presentCount + absentCount + leaveCount
                                val percentage = if (totalDays > 0) {
                                    (presentCount.toFloat() / totalDays.toFloat()) * 100
                                } else {
                                    0f
                                }
                                
                                binding.tvAttendancePercentage.text = String.format("%.1f%%", percentage)
                                binding.progressIndicator.setProgress(percentage.toInt(), true)
                                
                                val (statusText, statusColor) = when {
                                    percentage >= 75 -> "Excellent" to "#10B981" // Green
                                    percentage >= 60 -> "Good" to "#F59E0B"      // Orange
                                    else -> "Low Attendance" to "#EF4444"         // Red
                                }
                                
                                binding.tvAttendanceStatus.text = statusText
                                binding.tvAttendanceStatus.setTextColor(android.graphics.Color.parseColor(statusColor))

                                // Update Stats Row Cards
                                binding.tvTotalDaysCard.text = totalDays.toString()
                                binding.tvPresentCard.text = presentCount.toString()
                                binding.tvAbsentCard.text = absentCount.toString()
                                binding.tvLeaveCard.text = leaveCount.toString()

                                // Update the calendar day binder with the new data
                                binding.calendarView.dayBinder = object : MonthDayBinder<DayViewContainer> {
                                    override fun create(view: View) = DayViewContainer(view)
                                    override fun bind(container: DayViewContainer, data: CalendarDay) {
                                        container.textView.text = data.date.dayOfMonth.toString()
                                        container.textView.background = null
                                        container.textView.alpha = if (data.position == DayPosition.MonthDate) 1f else 0.3f

                                        when (attendanceMap[data.date]) {
                                            "Present" -> container.textView.setBackgroundResource(R.drawable.bg_present_day)
                                            "Absent" -> container.textView.setBackgroundResource(R.drawable.bg_absent_day)
                                            "Leave" -> container.textView.setBackgroundResource(R.drawable.bg_leave_day)
                                        }
                                    }
                                }
                                binding.calendarView.notifyCalendarChanged()
                            }
                            is UiState.Error -> {
                                showLoading(false)
                                DialogUtils.showAlert(requireContext(), "Error", state.message)
                            }
                            else -> showLoading(false)
                        }
                    }
                }
                
                launch {
                    viewModel.userLeaves.collectLatest { state ->
                        when (state) {
                            is UiState.Success -> {
                                leaveAdapter.submitList(state.data)
                            }
                            is UiState.Error -> {
                                Log.e("AttendanceFragment", "Error loading leaves: ${state.message}")
                            }
                            else -> {}
                        }
                    }
                }
            }
        }
    }

    private fun showLoading(isLoading: Boolean) {
        if (isLoading) {
            DialogUtils.showLoading(childFragmentManager)
        } else {
            DialogUtils.hideLoading(childFragmentManager)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    class DayViewContainer(view: View) : ViewContainer(view) {
        val textView: TextView = CalendarDayLayoutBinding.bind(view).calendarDayText
    }

    class MonthHeaderContainer(view: View) : ViewContainer(view) {
        val titleText: TextView = CalendarMonthHeaderBinding.bind(view).tvMonthTitle
        val weekDaysContainer: ViewGroup = CalendarMonthHeaderBinding.bind(view).weekDaysContainer
    }
}