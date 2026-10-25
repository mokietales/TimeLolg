package com.mokie.timelogdemo.ui.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object TimeFormat {

    private val locale: Locale get() = Locale.CHINA
    private val numericLocale: Locale get() = Locale.ROOT

    fun hms(seconds: Long): String {
        val s = seconds.coerceAtLeast(0L)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return String.format(numericLocale, "%02d:%02d:%02d", h, m, sec)
    }

    /**
     * Duration with units at minute precision, for lists and totals:
     * "45秒", "13分钟", "1小时5分", "119小时".
     */
    fun duration(ms: Long): String {
        val s = (ms / 1000L).coerceAtLeast(0L)
        if (s < 60) return "${s}秒"
        val m = s / 60
        if (m < 60) return "${m}分钟"
        val h = m / 60
        val mm = m % 60
        return if (mm == 0L) "${h}小时" else "${h}小时${mm}分"
    }

    /** Duration with units at second precision, for editors: "7秒", "13分55秒", "1小时05分12秒". */
    fun durationPrecise(ms: Long): String {
        val s = (ms / 1000L).coerceAtLeast(0L)
        val h = s / 3600
        val m = (s % 3600) / 60
        val ss = s % 60
        return when {
            h > 0 -> String.format(numericLocale, "%d小时%02d分%02d秒", h, m, ss)
            m > 0 -> String.format(numericLocale, "%d分%02d秒", m, ss)
            else -> "${ss}秒"
        }
    }

    fun hhmm(ms: Long): String =
        SimpleDateFormat("HH:mm", numericLocale).format(Date(ms))

    fun monthDayYear(ms: Long): String =
        SimpleDateFormat("yyyy年M月d日", locale).format(Date(ms))

    /** Round epoch ms down to the nearest whole minute. */
    fun roundToMinute(ms: Long): Long = (ms / 60_000L) * 60_000L

    /** Snap a raw span to whole seconds (matches timer stop behaviour). */
    fun snapDurationMs(rawMs: Long): Long {
        val raw = rawMs.coerceAtLeast(0L)
        return (raw / 1000L) * 1000L
    }

    fun withDate(baseMs: Long, dateAnchorMs: Long): Long {
        val time = Calendar.getInstance().apply { timeInMillis = baseMs }
        val date = Calendar.getInstance().apply { timeInMillis = dateAnchorMs }
        date.set(Calendar.HOUR_OF_DAY, time.get(Calendar.HOUR_OF_DAY))
        date.set(Calendar.MINUTE, time.get(Calendar.MINUTE))
        date.set(Calendar.SECOND, 0)
        date.set(Calendar.MILLISECOND, 0)
        return date.timeInMillis
    }

    fun withTime(baseMs: Long, hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = baseMs }
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun monthDay(ms: Long): String =
        SimpleDateFormat("M月d日", locale).format(Date(ms))

    fun weekdayLong(ms: Long): String =
        SimpleDateFormat("M月d日 EEEE", locale).format(Date(ms))

    fun dayHeader(ms: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = ms }
        val today = Calendar.getInstance()
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        return when {
            sameDay(cal, today) -> "今天"
            sameDay(cal, yesterday) -> "昨天"
            else -> SimpleDateFormat("M月d日 EEEE", locale).format(Date(ms))
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
