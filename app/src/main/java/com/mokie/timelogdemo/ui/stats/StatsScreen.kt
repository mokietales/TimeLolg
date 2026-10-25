package com.mokie.timelogdemo.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mokie.timelogdemo.data.SessionAllocationRow
import com.mokie.timelogdemo.data.TimeLogRepository
import com.mokie.timelogdemo.ui.components.EmptyState
import com.mokie.timelogdemo.ui.components.InsetGroup
import com.mokie.timelogdemo.ui.components.PageTitle
import com.mokie.timelogdemo.ui.components.RowDivider
import com.mokie.timelogdemo.ui.components.SectionHeader
import com.mokie.timelogdemo.ui.components.TabularNumFeature
import com.mokie.timelogdemo.ui.components.ThinBar
import com.mokie.timelogdemo.ui.theme.trackColor
import com.mokie.timelogdemo.ui.util.TimeFormat
import com.mokie.timelogdemo.ui.util.rememberDayStart
import java.util.Calendar
import kotlin.math.roundToInt

private enum class PeriodKind { Week, Month }

private data class Period(val kind: PeriodKind, val startMs: Long, val endMs: Long) {
    operator fun contains(ms: Long): Boolean = ms >= startMs && ms < endMs
}

private data class TrackTotal(val trackId: Long, val name: String, val ms: Long)

private data class PeriodStats(
    val totalMs: Long,
    val sessionCount: Int,
    /** Tracks by time, largest first. Allocations, so shared sessions split correctly. */
    val tracks: List<TrackTotal>,
    val days: List<Long>,
    /** Per day (aligned with [days]): trackId → ms. */
    val perDay: List<Map<Long, Long>>
) {
    val dayTotals: List<Long> = perDay.map { it.values.sum() }
}

/** Distinct track colours in the chart; everything beyond is grouped as "其他". */
private const val CHART_TRACKS = 6

/**
 * 复盘: where the time went over a week or month. Time is bucketed by the day a
 * session started, matching the Records timeline.
 */
@Composable
fun StatsScreen(
    repository: TimeLogRepository,
    onOpenTrackDetail: (Long) -> Unit
) {
    val rows by repository.sessionRows.collectAsState(initial = emptyList())
    val today = rememberDayStart()
    var kind by rememberSaveable { mutableStateOf(PeriodKind.Week) }
    var offset by rememberSaveable { mutableIntStateOf(0) }

    val period = remember(kind, offset, today) { periodFor(kind, offset, today) }
    val previous = remember(kind, offset, today) { periodFor(kind, offset - 1, today) }
    val stats = remember(rows, period) { computeStats(rows, period) }
    val previousTotal = remember(rows, previous) {
        rows.filter { it.startMs in previous }.sumOf { it.durationMs }
    }
    var selectedDay by remember(period) { mutableStateOf<Int?>(null) }

    val chartColors = stats.tracks.take(CHART_TRACKS).associate { it.trackId to trackColor(it.trackId) }
    val otherColor = MaterialTheme.colorScheme.outline

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item(key = "title") {
            PageTitle(eyebrow = rangeLabel(period), title = "复盘")
        }
        item(key = "period") {
            PeriodBar(
                kind = kind,
                offset = offset,
                onKind = {
                    kind = it
                    offset = 0
                },
                onPrev = { offset-- },
                onNext = { if (offset < 0) offset++ }
            )
        }
        item(key = "summary") {
            Summary(
                stats = stats,
                previousTotalMs = previousTotal,
                kind = kind,
                elapsedDays = stats.days.count { it <= today }.coerceAtLeast(1)
            )
        }
        item(key = "chart") {
            SectionHeader(title = "每日分布")
            InsetGroup {
                Column(modifier = Modifier.padding(16.dp)) {
                    SelectedDayLine(stats = stats, selected = selectedDay)
                    Spacer(Modifier.height(12.dp))
                    DailyChart(
                        stats = stats,
                        colorFor = { chartColors[it] ?: otherColor },
                        selected = selectedDay,
                        todayIndex = stats.days.indexOf(today).takeIf { it >= 0 },
                        onSelect = { selectedDay = if (selectedDay == it) null else it }
                    )
                }
            }
        }
        item(key = "tracks-header") { SectionHeader(title = "主题占比") }
        if (stats.tracks.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    title = "这段时间没有记录",
                    subtitle = "换个时间段看看，或去「现在」页开始计时。"
                )
            }
        } else {
            item(key = "tracks") {
                InsetGroup {
                    stats.tracks.forEachIndexed { i, t ->
                        TrackShareRow(
                            total = t,
                            fraction = t.ms.toFloat() / stats.totalMs.coerceAtLeast(1L),
                            onClick = { onOpenTrackDetail(t.trackId) }
                        )
                        if (i != stats.tracks.lastIndex) RowDivider()
                    }
                }
            }
        }
        item { Spacer(Modifier.height(48.dp)) }
    }
}

@Composable
private fun PeriodBar(
    kind: PeriodKind,
    offset: Int,
    onKind: (PeriodKind) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(3.dp)
        ) {
            PeriodKind.entries.forEach { k ->
                val selected = k == kind
                Text(
                    text = if (k == PeriodKind.Week) "周" else "月",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.surfaceContainerHighest
                            else Color.Transparent
                        )
                        .clickable { onKind(k) }
                        .padding(horizontal = 18.dp, vertical = 7.dp)
                )
            }
        }
        Spacer(Modifier.weight(1f))
        NavArrow(icon = Icons.Outlined.ChevronLeft, label = "上一段", enabled = true, onClick = onPrev)
        Text(
            text = relativeLabel(kind, offset),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        NavArrow(icon = Icons.Outlined.ChevronRight, label = "下一段", enabled = offset < 0, onClick = onNext)
    }
}

@Composable
private fun NavArrow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Icon(
        imageVector = icon,
        contentDescription = label,
        tint = if (enabled) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(10.dp)
            .size(22.dp)
    )
}

@Composable
private fun Summary(
    stats: PeriodStats,
    previousTotalMs: Long,
    kind: PeriodKind,
    elapsedDays: Int
) {
    val unit = if (kind == PeriodKind.Week) "周" else "月"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 20.dp)
    ) {
        Text(
            text = TimeFormat.duration(stats.totalMs),
            style = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = TabularNumFeature),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(6.dp))
        val compare = when {
            previousTotalMs == 0L && stats.totalMs == 0L -> null
            previousTotalMs == 0L -> "前一${unit}没有记录"
            else -> {
                val pct = ((stats.totalMs - previousTotalMs) * 100.0 / previousTotalMs).roundToInt()
                when {
                    pct > 0 -> "较前一$unit +$pct%"
                    pct < 0 -> "较前一$unit $pct%"
                    else -> "与前一${unit}持平"
                }
            }
        }
        if (compare != null) {
            Text(
                text = compare,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = if (stats.totalMs >= previousTotalMs) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(2.dp))
        }
        Text(
            text = "${stats.sessionCount} 条记录 · 日均 ${TimeFormat.duration(stats.totalMs / elapsedDays)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SelectedDayLine(stats: PeriodStats, selected: Int?) {
    val (label, value) = if (selected != null) {
        TimeFormat.weekdayLong(stats.days[selected]) to TimeFormat.duration(stats.dayTotals[selected])
    } else {
        val best = stats.dayTotals.withIndex().maxByOrNull { it.value }
        if (best == null || best.value == 0L) {
            "点击柱子查看当天" to ""
        } else {
            "最多 · ${TimeFormat.weekdayLong(stats.days[best.index])}" to TimeFormat.duration(best.value)
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = TabularNumFeature),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun DailyChart(
    stats: PeriodStats,
    colorFor: (Long) -> Color,
    selected: Int?,
    todayIndex: Int?,
    onSelect: (Int) -> Unit
) {
    val n = stats.days.size
    val maxMs = (stats.dayTotals.maxOrNull() ?: 0L).coerceAtLeast(1L)
    // Bottom-up stacking order: the period's biggest tracks sit at the base.
    val order = stats.tracks.map { it.trackId }
    val highlight = MaterialTheme.colorScheme.surfaceContainerHighest
    val baseline = MaterialTheme.colorScheme.outlineVariant

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .pointerInput(n) {
                detectTapGestures { pos ->
                    val i = (pos.x / (size.width.toFloat() / n)).toInt().coerceIn(0, n - 1)
                    onSelect(i)
                }
            }
    ) {
        val slot = size.width / n
        val barW = slot * if (n <= 7) 0.46f else 0.62f
        val r = (if (n <= 7) 6.dp else 2.dp).toPx()
        val radius = CornerRadius(r, r)
        for (i in 0 until n) {
            val x = i * slot + (slot - barW) / 2f
            if (i == selected) {
                drawRoundRect(
                    color = highlight,
                    topLeft = Offset(i * slot + slot * 0.08f, 0f),
                    size = Size(slot * 0.84f, size.height),
                    cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                )
            }
            val day = stats.perDay[i]
            if (day.isEmpty()) {
                val h = 2.dp.toPx()
                drawRoundRect(
                    color = baseline,
                    topLeft = Offset(x, size.height - h),
                    size = Size(barW, h),
                    cornerRadius = CornerRadius(h / 2f, h / 2f)
                )
                continue
            }
            var top = size.height
            val segments = order.mapNotNull { id -> day[id]?.let { id to it } }
            segments.forEachIndexed { s, (id, ms) ->
                val h = (ms.toFloat() / maxMs) * size.height
                top -= h
                if (s == segments.lastIndex) {
                    // Round only the top of the bar: rounded rect, then square off its lower half.
                    drawRoundRect(colorFor(id), Offset(x, top), Size(barW, h), radius)
                    if (h > radius.y) {
                        drawRect(colorFor(id), Offset(x, top + radius.y), Size(barW, h - radius.y))
                    }
                } else {
                    drawRect(colorFor(id), Offset(x, top), Size(barW, h))
                }
            }
        }
    }
    Spacer(Modifier.height(6.dp))
    Row(modifier = Modifier.fillMaxWidth()) {
        stats.days.forEachIndexed { i, day ->
            val label = if (n <= 7) {
                TimeFormat.weekdayShort(day)
            } else {
                val dom = TimeFormat.dayOfMonth(day)
                if (dom == 1 || dom % 5 == 0) dom.toString() else ""
            }
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (i == todayIndex) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun TrackShareRow(total: TrackTotal, fraction: Float, onClick: () -> Unit) {
    val color = trackColor(total.trackId)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = total.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${(fraction * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = TabularNumFeature),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = TimeFormat.duration(total.ms),
                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = TabularNumFeature),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(Modifier.height(8.dp))
        ThinBar(
            fraction = fraction,
            color = color,
            modifier = Modifier.padding(start = 20.dp)
        )
    }
}

// ----- Period math -----

private fun periodFor(kind: PeriodKind, offset: Int, nowMs: Long): Period {
    val cal = Calendar.getInstance()
    val field = if (kind == PeriodKind.Week) Calendar.WEEK_OF_YEAR else Calendar.MONTH
    cal.timeInMillis = if (kind == PeriodKind.Week) TimeFormat.startOfWeekMs(nowMs)
    else TimeFormat.startOfMonthMs(nowMs)
    cal.add(field, offset)
    val start = cal.timeInMillis
    cal.add(field, 1)
    return Period(kind, start, cal.timeInMillis)
}

private fun daysOf(period: Period): List<Long> {
    val cal = Calendar.getInstance().apply { timeInMillis = period.startMs }
    return buildList {
        while (cal.timeInMillis < period.endMs) {
            add(cal.timeInMillis)
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
    }
}

private fun computeStats(rows: List<SessionAllocationRow>, period: Period): PeriodStats {
    val inRange = rows.filter { it.startMs in period }
    val days = daysOf(period)
    val dayIndex = days.withIndex().associate { (i, d) -> d to i }
    val perDay = List(days.size) { HashMap<Long, Long>() }
    inRange.forEach { r ->
        val i = dayIndex[TimeFormat.startOfDayMs(r.startMs)] ?: return@forEach
        perDay[i].merge(r.trackId, r.durationMs, Long::plus)
    }
    val tracks = inRange.groupBy { it.trackId }
        .map { (id, g) -> TrackTotal(id, g.first().trackName, g.sumOf { it.durationMs }) }
        .sortedByDescending { it.ms }
    return PeriodStats(
        totalMs = inRange.sumOf { it.durationMs },
        sessionCount = inRange.map { it.sessionId }.distinct().size,
        tracks = tracks,
        days = days,
        perDay = perDay
    )
}

private fun rangeLabel(period: Period): String = when (period.kind) {
    PeriodKind.Week -> "${TimeFormat.monthDay(period.startMs)} – ${TimeFormat.monthDay(period.endMs - 1)}"
    PeriodKind.Month -> TimeFormat.yearMonth(period.startMs)
}

private fun relativeLabel(kind: PeriodKind, offset: Int): String = when (kind) {
    PeriodKind.Week -> when (offset) {
        0 -> "本周"
        -1 -> "上周"
        else -> "${-offset} 周前"
    }
    PeriodKind.Month -> when (offset) {
        0 -> "本月"
        -1 -> "上月"
        else -> "${-offset} 个月前"
    }
}
