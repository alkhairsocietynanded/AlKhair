package com.zabibtech.alkhair.utils

import android.app.DatePickerDialog
import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object DateUtils {

    // --- Current Date / Month Strings ---
    fun today(): String = formatDate(Calendar.getInstance(), "yyyy-MM-dd")
    fun currentMonth(): String = formatDate(Calendar.getInstance(), "yyyy-MM")

    // --- Format Calendar to String ---
    fun formatDate(calendar: Calendar, pattern: String = "yyyy-MM-dd"): String {
        val sdf = SimpleDateFormat(pattern, Locale.getDefault())
        return sdf.format(calendar.time)
    }

    // --- Add / Subtract days ---
    fun addDays(calendar: Calendar, days: Int): Calendar {
        val newCal = calendar.clone() as Calendar
        newCal.add(Calendar.DAY_OF_MONTH, days)
        return newCal
    }

    // --- Show DatePickerDialog and update Calendar & TextView ---
    fun showDatePicker(
        context: Context,
        calendar: Calendar,
        onDateSelected: (Calendar) -> Unit
    ) {
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        DatePickerDialog(context, { _, y, m, d ->
            calendar.set(y, m, d)
            onDateSelected(calendar)
        }, year, month, day).show()
    }
}
