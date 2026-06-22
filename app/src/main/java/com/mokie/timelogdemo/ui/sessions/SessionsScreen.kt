package com.mokie.timelogdemo.ui.sessions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mokie.timelogdemo.data.SessionAllocationRow
import com.mokie.timelogdemo.data.SessionDao
import com.mokie.timelogdemo.data.TrackDao
import com.mokie.timelogdemo.ui.components.EmptyState
import com.mokie.timelogdemo.ui.components.ManualSessionDialog
import com.mokie.timelogdemo.ui.components.PageTitle
import com.mokie.timelogdemo.ui.components.SessionEditDialog
import com.mokie.timelogdemo.ui.components.TabularNumFeature
import com.mokie.timelogdemo.ui.theme.trackColor
import com.mokie.timelogdemo.ui.util.TimeFormat

private enum class SessionFilter(val label: String) {
    All("全部"),
    Today("今天"),
    Week("本周"),
    Noted("有备注"),
    Multi("多主题")
}

@Composable
fun SessionsScreen(
    sessionDao: SessionDao,
    trackDao: TrackDao
) {
    val rows by sessionDao.observeAllAllocations().collectAsState(initial = emptyList())
    var filter by rememberSaveable { mutableStateOf(SessionFilter.All) }
    var editingSessionId by remember { mutableStateOf<Long?>(null) }
    var manualEntryVisible by remember { mutableStateOf(false) }

    val sessions = remember(rows) { collapseRows(rows) }
    val filtered = remember(sessions, filter) {
        val now = System.currentTimeMillis()
        when (filter) {
            SessionFilter.All -> sessions
            SessionFilter.Today -> {
                val start = TimeFormat.startOfDayMs(now)
                sessions.filter { it.endMs >= start }
            }
            SessionFilter.Week -> {
                val start = TimeFormat.startOfWeekMs(now)
                sessions.filter { it.endMs >= start }
            }
            SessionFilter.Noted -> sessions.filter { it.note.isNotBlank() }
            SessionFilter.Multi -> sessions.filter { it.splits.size > 1 }
        }
    }
    // Group the filtered records by day, newest day first, so the records read
    // as a chronological timeline rather than an undifferentiated list.
    val grouped = remember(filtered) {
        filtered.groupBy { TimeFormat.startOfDayMs(it.startMs) }
            .toSortedMap(compareByDescending { it })
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                PageTitle(
                    eyebrow = "记录",
                    title = "${filtered.size} 条"
                )
            }
            item { FilterChips(current = filter, onSelect = { filter = it }) }
            item { Spacer(Modifier.height(8.dp)) }

            if (filtered.isEmpty()) {
                item {
                    EmptyState(
                        title = "暂无记录",
                        subtitle = "点击 + 补录时间，或在「现在」页开始计时。"
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
                items(items = dayItems, key = { it.sessionId }) { sess ->
                    SessionRow(
                        session = sess,
                        onClick = { editingSessionId = sess.sessionId }
                    )
                }
            }

            item { Spacer(Modifier.height(96.dp)) }
        }

        FloatingActionButton(
            onClick = { manualEntryVisible = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(Icons.Outlined.Add, contentDescription = "补录时间")
        }
    }

    if (manualEntryVisible) {
        ManualSessionDialog(
            sessionDao = sessionDao,
            trackDao = trackDao,
            onDismiss = { manualEntryVisible = false }
        )
    }

    editingSessionId?.let { sid ->
        SessionEditDialog(
            sessionId = sid,
            sessionDao = sessionDao,
            trackDao = trackDao,
            onDismiss = { editingSessionId = null }
        )
    }
}

@Composable
private fun FilterChips(current: SessionFilter, onSelect: (SessionFilter) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SessionFilter.values().forEach { f ->
            val selected = f == current
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.surfaceContainerHigh
                        else MaterialTheme.colorScheme.surfaceContainer
                    )
                    .clickable { onSelect(f) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = f.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DayHeader(dayMs: Long, totalMs: Long) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 22.dp, bottom = 8.dp),
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
private fun SessionRow(session: SessionItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.width(64.dp)) {
            Text(
                text = TimeFormat.hhmm(session.startMs),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFeatureSettings = TabularNumFeature
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = TimeFormat.hhmm(session.endMs),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFeatureSettings = TabularNumFeature
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.width(14.dp))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 7.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(trackColor(session.headlineTrackId))
            )
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(28.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = session.headlineTrack.ifEmpty { "未命名" },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = TimeFormat.shortDuration(
                        (session.endMs - session.startMs).coerceAtLeast(0L)
                    ),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFeatureSettings = TabularNumFeature
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (session.splits.size > 1) {
                Spacer(Modifier.height(2.dp))
                val splitText = session.splits.joinToString(separator = " · ") {
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
            if (session.note.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = session.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** One recorded session, flattened across its per-track allocations. */
internal data class SessionItem(
    val sessionId: Long,
    val startMs: Long,
    val endMs: Long,
    val note: String,
    val headlineTrack: String,
    val headlineTrackId: Long,
    val splits: List<Pair<String, Long>> // (track name, duration)
)

/** Collapse the per-allocation rows back into one item per session. */
internal fun collapseRows(rows: List<SessionAllocationRow>): List<SessionItem> {
    if (rows.isEmpty()) return emptyList()
    val grouped = rows.groupBy { it.sessionId }
    return grouped.values
        .map { group ->
            val first = group.first()
            val biggest = group.maxByOrNull { it.durationMs } ?: first
            SessionItem(
                sessionId = first.sessionId,
                startMs = first.startMs,
                endMs = first.endMs,
                note = first.note,
                headlineTrack = if (group.size == 1) {
                    biggest.trackName
                } else {
                    "${biggest.trackName} +${group.size - 1}"
                },
                headlineTrackId = biggest.trackId,
                splits = group.map { it.trackName to it.durationMs }
            )
        }
        .sortedByDescending { it.startMs }
}
