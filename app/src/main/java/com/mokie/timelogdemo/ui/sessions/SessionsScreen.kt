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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.mokie.timelogdemo.data.SessionDao
import com.mokie.timelogdemo.data.TrackDao
import com.mokie.timelogdemo.ui.components.EmptyState
import com.mokie.timelogdemo.ui.components.PageTitle
import com.mokie.timelogdemo.ui.components.RowDivider
import com.mokie.timelogdemo.ui.components.SessionEditDialog
import com.mokie.timelogdemo.ui.components.TabularNumFeature
import com.mokie.timelogdemo.ui.timeline.TimelineSession
import com.mokie.timelogdemo.ui.timeline.collapseRows
import com.mokie.timelogdemo.ui.util.TimeFormat

private enum class SessionFilter(val label: String) {
    All("All"),
    Today("Today"),
    Week("Week"),
    Noted("With note"),
    Multi("Multi-track")
}

@Composable
fun SessionsScreen(
    sessionDao: SessionDao,
    trackDao: TrackDao
) {
    val rows by sessionDao.observeAllAllocations().collectAsState(initial = emptyList())
    var filter by rememberSaveable { mutableStateOf(SessionFilter.All) }
    var editingSessionId by remember { mutableStateOf<Long?>(null) }

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

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            PageTitle(
                eyebrow = "Sessions",
                title = "${filtered.size} ${if (filtered.size == 1) "record" else "records"}"
            )
        }
        item { FilterChips(current = filter, onSelect = { filter = it }) }
        item { Spacer(Modifier.height(20.dp)) }

        if (filtered.isEmpty()) {
            item { EmptyState(title = "No sessions", subtitle = "Try a different filter.") }
        }

        items(items = filtered, key = { it.sessionId }) { sess ->
            SessionRow(session = sess, onClick = { editingSessionId = sess.sessionId })
            RowDivider(insetStart = 24.dp)
        }

        item { Spacer(Modifier.height(48.dp)) }
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
private fun SessionRow(session: TimelineSession, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = session.headlineTrack.ifEmpty { "Untitled" },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = TimeFormat.shortDuration(
                    (session.endMs - session.startMs).coerceAtLeast(0L)
                ),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFeatureSettings = TabularNumFeature
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = "${TimeFormat.monthDay(session.startMs)} · " +
                "${TimeFormat.hhmm(session.startMs)} – ${TimeFormat.hhmm(session.endMs)}",
            style = MaterialTheme.typography.bodySmall.copy(
                fontFeatureSettings = TabularNumFeature
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (session.splits.size > 1) {
            Spacer(Modifier.height(4.dp))
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
            Spacer(Modifier.height(4.dp))
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
