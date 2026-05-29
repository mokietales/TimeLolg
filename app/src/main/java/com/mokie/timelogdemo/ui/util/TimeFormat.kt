package com.mokie.timelogdemo.ui.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object TimeFormat {

    private val locale: Locale get() = Locale.getDefault()

    fun hms(seconds: Long): String {
        val s = seconds.coerceAtLeast(0L)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return String.format("%02d:%02d:%02d", h, m, sec)
    }

    fun hmsFromMs(ms: Long): String = hms(ms / 1000L)

    /** Clock-style label that drops the hour when zero: "0:42", "12:34", "1:23:45". */
    fun clockSeconds(seconds: Long): String {
        val s = seconds.coerceAtLeast(0L)
        val h = s / 3600
        val m = (s % 3600) / 60
        val ss = s % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, ss)
        else String.format("%d:%02d", m, ss)
    }

    /** Short duration label, e.g. "24m", "1h 5m", "3s". */
    fun shortDuration(ms: Long): String {
        val totalSec = (ms / 1000L).coerceAtLeast(0L)
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return when {
            h > 0 && m > 0 -> "${h}h ${m}m"
            h > 0 -> "${h}h"
            m > 0 -> "${m}m"
            else -> "${s}s"
        }
    }

    fun hhmm(ms: Long): String =
        SimpleDateFormat("HH:mm", locale).format(Date(ms))

    fun monthDay(ms: Long): String =
        SimpleDateFormat("MMM d", locale).format(Date(ms))

    fun weekdayLong(ms: Long): String =
        SimpleDateFormat("EEEE, MMM d", locale).format(Date(ms))

    fun dayHeader(ms: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = ms }
        val today = Calendar.getInstance()
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        return when {
            sameDay(cal, today) -> "Today"
            sameDay(cal, yesterday) -> "Yesterday"
            else -> SimpleDateFormat("EEEE, MMM d", locale).format(Date(ms))
        }
    }

    private fun sameDay(a: Calendar, b: Calendar): Boolean =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    fun startOfDayMs(ms: Long): Long {
        val cal = Calendar.getInstance(TimeZone.getDefault(), locale).apply {
            timeInMillis = ms
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    fun startOfWeekMs(ms: Long): Long {
        val cal = Calendar.getInstance(TimeZone.getDefault(), locale).apply {
            timeInMillis = ms
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            // Roll back to first day of week.
            val first = firstDayOfWeek
            while (get(Calendar.DAY_OF_WEEK) != first) {
                add(Calendar.DAY_OF_YEAR, -1)
            }
        }
        return cal.timeInMillis
    }

    fun startOfMonthMs(ms: Long): Long {
        val cal = Calendar.getInstance(TimeZone.getDefault(), locale).apply {
            timeInMillis = ms
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }
}
