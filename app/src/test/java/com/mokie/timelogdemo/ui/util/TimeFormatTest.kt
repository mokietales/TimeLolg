package com.mokie.timelogdemo.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class TimeFormatTest {

    private val sec = 1_000L
    private val min = 60 * sec
    private val hour = 60 * min

    @Test
    fun duration_usesUnitsAtMinutePrecision() {
        assertEquals("0秒", TimeFormat.duration(0))
        assertEquals("45秒", TimeFormat.duration(45 * sec))
        assertEquals("59秒", TimeFormat.duration(59_999))
        assertEquals("1分钟", TimeFormat.duration(min))
        assertEquals("13分钟", TimeFormat.duration(13 * min + 55 * sec))
        assertEquals("1小时", TimeFormat.duration(hour + 30 * sec))
        assertEquals("1小时5分", TimeFormat.duration(hour + 5 * min))
        assertEquals("119小时18分", TimeFormat.duration(119 * hour + 18 * min + 1 * sec))
    }

    @Test
    fun duration_clampsNegativeToZero() {
        assertEquals("0秒", TimeFormat.duration(-5 * sec))
    }

    @Test
    fun durationPrecise_keepsSeconds() {
        assertEquals("7秒", TimeFormat.durationPrecise(7 * sec))
        assertEquals("13分55秒", TimeFormat.durationPrecise(13 * min + 55 * sec))
        assertEquals("1小时05分12秒", TimeFormat.durationPrecise(hour + 5 * min + 12 * sec))
        assertEquals("1小时00分00秒", TimeFormat.durationPrecise(hour))
    }

    @Test
    fun snapDurationMs_floorsToWholeSeconds() {
        assertEquals(59_000L, TimeFormat.snapDurationMs(59_999))
        assertEquals(0L, TimeFormat.snapDurationMs(-1))
    }

    @Test
    fun startOfWeek_isMondayMidnightForEveryDayOfTheWeek() {
        val cal = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 19, 13, 45, 7) } // a Monday
        repeat(7) {
            val t = cal.timeInMillis
            val start = TimeFormat.startOfWeekMs(t)
            val s = Calendar.getInstance().apply { timeInMillis = start }
            assertEquals(Calendar.MONDAY, s.get(Calendar.DAY_OF_WEEK))
            assertEquals(0, s.get(Calendar.HOUR_OF_DAY))
            assertEquals(0, s.get(Calendar.MINUTE))
            assertTrue(start <= t && t - start < 7 * 24 * hour + hour)
            assertEquals(19, s.get(Calendar.DAY_OF_MONTH))
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
    }

    @Test
    fun weekdayShort_mapsChineseNames() {
        val cal = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 25, 12, 0) } // Sunday
        assertEquals("日", TimeFormat.weekdayShort(cal.timeInMillis))
        cal.add(Calendar.DAY_OF_YEAR, 1)
        assertEquals("一", TimeFormat.weekdayShort(cal.timeInMillis))
    }
}
