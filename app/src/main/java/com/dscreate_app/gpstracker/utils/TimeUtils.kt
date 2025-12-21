package com.dscreate_app.gpstracker.utils

import android.annotation.SuppressLint
import java.text.SimpleDateFormat
import java.util.*

@SuppressLint("SimpleDateFormat")
object TimeUtils {

    // Formatter for track duration HH:mm:ss
    private val timeFormatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    // Formatter for displaying the date and time, e.g., "15 декабря 2025 11:30"
    private val dateTimeFormatter = SimpleDateFormat("dd MMMM yyyy HH:mm", Locale("ru"))

    // Gets duration from milliseconds
    fun getTime(timeInMillis: Long): String {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timeInMillis
        return timeFormatter.format(calendar.time)
    }

    // Gets the current date and time as a timestamp
    fun getCurrentTimeInMillis(): Long {
        return System.currentTimeMillis()
    }

    // Gets the date and time as a formatted string from a timestamp
    fun getFormattedDateTime(timeInMillis: Long): String {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timeInMillis
        return dateTimeFormatter.format(calendar.time)
    }
}