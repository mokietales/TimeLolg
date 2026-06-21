package com.mokie.timelogdemo.ui.timeline

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mokie.timelogdemo.data.SessionAllocationRow
import com.mokie.timelogdemo.data.SessionDao
import com.mokie.timelogdemo.ui.components.EmptyState
import com.mokie.timelogdemo.ui.components.PageTitle
import com.mokie.timelogdemo.ui.components.TabularNumFeature
import com.mokie.timelogdemo.ui.util.TimeFormat

@Composable
fun TimelineScreen(sessionDao: SessionDao) {
    val rows by sessionDao.observeAllAllocations().collectAsState(initial = emptyList())
    val sessions = remember(rows) { collapseRows(rows) }
    val grouped = remember(sessions) {
        sessions.groupBy { TimeFormat.startOfDayMs(it.startMs) }
            .toSortedMap(compareByDescending { it })
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            PageTitle(
                eyebrow = "时间线",
                title = if (sessions.isEmpty()) "暂无记录"
                else "${sessions.size} 条记录"
            )
        }

        if (sessions.isEmpty()) {
            item {
                EmptyState(
                    title = "还没有任何记录",
                    subtitle = "去「现在」页开始计时吧。"
                )
            }
        }

        grouped.forEach { (dayMs, dayItems) ->
            item(key = "header-$dayMs") {
                DayHeader(
                    dayMs = dayMs,
                    totalMs = dayItems.sumOf { (it.endMs - it.startMs).coerceAtLeast(0L) }
                )
            }
            items(items = dayItems, key = { it.sessionId }) { row ->
                TimelineRow(row = row)
            }
        }

        item { Spacer(Modifier.height(48.dp)) }
    }
}

@Composable
private fun DayHeader(dayMs: Long, totalMs: Long) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 24.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = TimeFormat.dayHeader(dayMs),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = TimeFormat.shortDuration(totalMs),
            style = MaterialTheme.typography.labelMedium.copy(
                fontFeatureSettings = TabularNumFeature
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TimelineRow(row: TimelineSession) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(
            modifier = Modifier.width(72.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = TimeFormat.hhmm(row.startMs),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFeatureSettings = TabularNumFeature
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = TimeFormat.hhmm(row.endMs),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFeatureSettings = TabularNumFeature
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.width(16.dp))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 7.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(28.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
        }

        Spacer(Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.headlineTrack,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = TimeFormat.shortDuration((row.endMs - row.startMs).coerceAtLeast(0L)),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFeatureSettings = TabularNumFeature
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (row.splits.size > 1) {
                Spacer(Modifier.height(2.dp))
                val splitText = row.splits.joinToString(separator = " · ") {
                    "${it.first} ${TimeFormat.shortDuration(it.second)}"
                }
                Text(
                    text = splitText,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFeatureSettings = TabularNumFeature
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (row.note.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = row.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

internal data class TimelineSession(
    val sessionId: Long,
    val startMs: Long,
    val endMs: Long,
    val note: String,
    val headlineTrack: String,
    val splits: List<Pair<String, Long>> // (track name, duration)
)

internal fun collapseRows(rows: List<SessionAllocationRow>): List<TimelineSession> {
    if (rows.isEmpty()) return emptyList()
    val grouped = rows.groupBy { it.sessionId }
    return grouped.values
        .map { group ->
            val first = group.first()
            val biggest = group.maxByOrNull { it.durationMs } ?: first
            TimelineSession(
                sessionId = first.sessionId,
                startMs = first.startMs,
                endMs = first.endMs,
                note = first.note,
                headlineTrack = if (group.size == 1) {
                    biggest.trackName
                } else {
                    "${biggest.trackName} +${group.size - 1}"
                },
                splits = group.map { it.trackName to it.durationMs }
            )
        }
        .sortedByDescending { it.startMs }
}
