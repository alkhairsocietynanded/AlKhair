package com.zabibtech.alkhair.utils

import android.app.DatePickerDialog
import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object DateUtils {

    // --- Current Date / Month / Year Strings ---
    fun today(): String = formatDate(Calendar.getInstance(), "yyyy-MM-dd")

    // 🔹 Updated: Returns "October 2025" (used in fee spinner)
    fun currentMonth(): String {
        val calendar = Calendar.getInstance()
        val monthName = SimpleDateFormat("MMMM", Locale.getDefault()).format(calendar.time)
        val year = calendar.get(Calendar.YEAR)
        return "$monthName $year"
    }

    fun currentYear(): String = formatDate(Calendar.getInstance(), "yyyy")

    // --- Helper: Current month index (0 = Jan, 11 = Dec) ---
    fun getCurrentMonthIndex(): Int {
        return Calendar.getInstance().get(Calendar.MONTH)
    }

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

    fun generateMonthListForPicker(): List<String> {
        val monthList = mutableListOf("All Months")
        val calendar = Calendar.getInstance()
        val monthFormat = SimpleDateFormat("MMM", Locale.getDefault())
        val currentYear = calendar.get(Calendar.YEAR)

        // Generate for previous, current, and next year
        for (year in (currentYear)..(currentYear + 2)) {
            for (month in 0..11) {
                calendar.set(year, month, 1)
                val monthName = monthFormat.format(calendar.time)
                monthList.add("$year-$monthName")
            }
        }
        return monthList
    }

    fun getCurrentTimestamp(): Long {
        return System.currentTimeMillis()
    }

    fun formatDateTime(timestamp: Long): String {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(calendar.time)
    }

    fun convertTimestampToStringDate(timestamp: Long): String {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(calendar.time)
    }
}
