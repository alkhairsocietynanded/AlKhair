package com.zabibtech.alkhair.ui.user.fragments

import android.os.Build
import android.os.Bundle
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
import com.zabibtech.alkhair.R
import com.zabibtech.alkhair.data.models.User
import com.zabibtech.alkhair.databinding.CalendarDayLayoutBinding
import com.zabibtech.alkhair.databinding.CalendarMonthHeaderBinding
import com.zabibtech.alkhair.databinding.FragmentAttendanceBinding
import com.zabibtech.alkhair.ui.attendance.AttendanceViewModel
import com.zabibtech.alkhair.utils.DialogUtils
import com.zabibtech.alkhair.utils.UiState
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

    private val attendanceViewModel: AttendanceViewModel by viewModels()

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

        user?.let { attendanceViewModel.loadAttendanceForUser(it.uid) }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Setup calendar range, first day of week etc.
        val currentMonth = YearMonth.now()
        val firstMonth = currentMonth.minusMonths(12)
        val lastMonth = currentMonth.plusMonths(12)
        val firstDayOfWeek = DayOfWeek.MONDAY  // ya aap locale-based

        binding.calendarView.setup(firstMonth, lastMonth, firstDayOfWeek)

        // Scroll to current month
        binding.calendarView.scrollToMonth(currentMonth)

        // 👇 Add this line so the calendar knows which layout to use for headers
        binding.calendarView.monthHeaderResource = R.layout.calendar_month_header
        // Optional: show month header (e.g. “April 2025”) above each month
        binding.calendarView.monthHeaderBinder =
            object : MonthHeaderFooterBinder<MonthHeaderContainer> {
                override fun create(view: View) = MonthHeaderContainer(view)

                override fun bind(container: MonthHeaderContainer, data: CalendarMonth) {
                    // Month title (e.g. "September 2025")
                    val title =
                        data.yearMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) +
                                " " + data.yearMonth.year
                    container.titleText.text = title

                    // Week days row (bind only once per header view)
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

// --- Day Binder (default, avoid crash) ---
        binding.calendarView.dayBinder = object : MonthDayBinder<DayViewContainer> {
            override fun create(view: View) = DayViewContainer(view)
            override fun bind(container: DayViewContainer, data: CalendarDay) {
                container.textView.text = data.date.dayOfMonth.toString()
                container.textView.background = null
                container.textView.alpha = if (data.position == DayPosition.MonthDate) 1f else 0.3f
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                attendanceViewModel.userAttendance.collectLatest { state ->
                    when (state) {
                        UiState.Loading -> {
                            DialogUtils.showLoading(childFragmentManager)
                        }

                        is UiState.Success -> {
                            DialogUtils.hideLoading(childFragmentManager)
                            val attendanceMap = mutableMapOf<LocalDate, String>()

                            // Flatten data: merge all classes for this user
                            state.data.values.forEach { dateMap ->
                                dateMap.forEach { (dateStr, status) ->
                                    val date = LocalDate.parse(dateStr) // "yyyy-MM-dd" format
                                    attendanceMap[date] = status
                                }
                            }

                            binding.calendarView.dayBinder =
                                object : MonthDayBinder<DayViewContainer> {
                                    override fun create(view: View) = DayViewContainer(view)

                                    override fun bind(
                                        container: DayViewContainer,
                                        data: CalendarDay
                                    ) {
                                        container.textView.text = data.date.dayOfMonth.toString()
                                        container.textView.background = null
                                        container.textView.alpha =
                                            if (data.position == DayPosition.MonthDate) 1f else 0.3f

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
                            DialogUtils.hideLoading(parentFragmentManager)
                        }

                        else -> {
                            DialogUtils.hideLoading(parentFragmentManager)
                        }
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        DialogUtils.hideLoading(childFragmentManager)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Hide any active loading dialog to prevent window leaks when navigating away
        DialogUtils.hideLoading(parentFragmentManager)
        _binding = null
    }

    // ViewContainer for day cells
    class DayViewContainer(view: View) : ViewContainer(view) {
        val binding = CalendarDayLayoutBinding.bind(view)
        val textView = binding.calendarDayText
    }

    // ViewContainer for month header
    class MonthHeaderContainer(view: View) : ViewContainer(view) {
        val binding = CalendarMonthHeaderBinding.bind(view)
        val titleText = binding.tvMonthTitle
        val weekDaysContainer = binding.weekDaysContainer
    }
}
