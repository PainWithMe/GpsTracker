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

    // Formatter for displaying the date, e.g., "15 декабря 2025"
    private val dateFormatter = SimpleDateFormat("dd MMMM yyyy", Locale("ru"))

    // Gets duration from milliseconds
    fun getTime(timeInMillis: Long): String {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timeInMillis
        return timeFormatter.format(calendar.time)
    }

    // Gets the current date as a formatted string
    fun getFormattedDate(timeInMillis: Long): String {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timeInMillis
        return dateFormatter.format(calendar.time)
    }
}