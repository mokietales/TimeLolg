package com.mokie.timelogdemo.ui.review

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
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
import com.mokie.timelogdemo.data.observeTrackTree
import com.mokie.timelogdemo.ui.components.EmptyState
import com.mokie.timelogdemo.ui.components.PageTitle
import com.mokie.timelogdemo.ui.components.RowDivider
import com.mokie.timelogdemo.ui.components.TabularNumFeature
import com.mokie.timelogdemo.ui.util.TimeFormat

private data class ChildSegment(
    val trackId: Long,
    val name: String,
    val ms: Long
)

private enum class Range(val label: String) {
    Today("Today"),
    Week("This Week"),
    Month("This Month"),
    All("All Time");

    fun startMs(nowMs: Long): Long? = when (this) {
        Today -> TimeFormat.startOfDayMs(nowMs)
        Week -> TimeFormat.startOfWeekMs(nowMs)
        Month -> TimeFormat.startOfMonthMs(nowMs)
        All -> null
    }
}

@Composable
fun ReviewScreen(
    sessionDao: SessionDao,
    trackDao: TrackDao,
    onOpenTrackDetail: (Long) -> Unit
) {
    val rows by sessionDao.observeAllAllocations().collectAsState(initial = emptyList())
    val tree by remember(trackDao, sessionDao) {
        observeTrackTree(trackDao, sessionDao)
    }.collectAsState(initial = null)
    var range by rememberSaveable { mutableStateOf(Range.Week) }
    var rollup by rememberSaveable { mutableStateOf(true) }

    val nowMs = remember(rows, range) { System.currentTimeMillis() }
    val start = remember(range, nowMs) { range.startMs(nowMs) }

    data class Bucket(
        val trackId: Long,
        val trackName: String,
        val selfMs: Long,
        val rollupMs: Long,
        val sessionCount: Int,
        val descendantCount: Int,
        /** Direct-child time slices for the breakdown bar; empty = hide bar. */
        val childSegments: List<ChildSegment>
    )

    // Self-time per track in the time window. This is "leaf-only" attribution —
    // it never double-counts because each allocation belongs to exactly one
    // track.
    val perTrackSelf: Map<Long, Pair<Long, MutableSet<Long>>> = remember(rows, start) {
        val map = HashMap<Long, Pair<Long, MutableSet<Long>>>()
        rows.asSequence()
            .filter { start == null || it.endMs >= start }
            .forEach { r ->
                val cur = map[r.trackId] ?: (0L to mutableSetOf())
                cur.second.add(r.sessionId)
                map[r.trackId] = (cur.first + r.durationMs) to cur.second
            }
        map
    }

    val buckets: List<Bucket> = remember(perTrackSelf, tree, rollup) {
        val t = tree ?: return@remember emptyList()
        t.tracks.map { track ->
            val (self, sessIds) = perTrackSelf[track.id] ?: (0L to emptySet<Long>())
            val descendants = t.collectDescendants(track.id)
            val descIds = descendants - track.id
            val rollupMs = descendants.sumOf { id -> perTrackSelf[id]?.first ?: 0L }

            val directChildren = t.childrenOf[track.id].orEmpty()
            // List every direct child — including zero-time — in stable tree order.
            val childSegments = directChildren.map { childId ->
                val childMs = if (rollup) {
                    t.collectDescendants(childId).sumOf { id ->
                        perTrackSelf[id]?.first ?: 0L
                    }
                } else {
                    perTrackSelf[childId]?.first ?: 0L
                }
                ChildSegment(
                    trackId = childId,
                    name = t.byId[childId]?.name ?: "—",
                    ms = childMs
                )
            }

            Bucket(
                trackId = track.id,
                trackName = track.name,
                selfMs = self,
                rollupMs = rollupMs,
                sessionCount = sessIds.size,
                descendantCount = descIds.size,
                childSegments = childSegments
            )
        }.filter { it.rollupMs > 0L }
            .sortedByDescending { if (rollup) it.rollupMs else it.selfMs }
    }

    // True grand total — sum the (filtered) allocations directly. This never
    // double-counts even with DAG-shared descendants.
    val grandTotalMs = remember(rows, start) {
        rows.asSequence()
            .filter { start == null || it.endMs >= start }
            .sumOf { it.durationMs }
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            PageTitle(
                eyebrow = "Review",
                title = TimeFormat.shortDuration(grandTotalMs).let { d ->
                    if (grandTotalMs == 0L) "No time yet" else d
                }
            )
        }

        item { RangeChips(current = range, onSelect = { range = it }) }

        item { Spacer(Modifier.height(8.dp)) }

        item { RollupToggle(rollup = rollup, onToggle = { rollup = it }) }

        item { Spacer(Modifier.height(12.dp)) }

        if (buckets.isEmpty()) {
            item {
                EmptyState(
                    title = "Nothing in this range",
                    subtitle = "Try a wider range, or log something on the Now tab."
                )
            }
        }

        items(items = buckets, key = { it.trackId }) { bucket ->
            val displayMs = if (rollup) bucket.rollupMs else bucket.selfMs
            ReviewRow(
                name = bucket.trackName,
                totalMs = displayMs,
                sessionCount = bucket.sessionCount,
                descendantCount = if (rollup) bucket.descendantCount else 0,
                childSegments = bucket.childSegments,
                onClick = { onOpenTrackDetail(bucket.trackId) }
            )
        }

        item { Spacer(Modifier.height(48.dp)) }
    }
}

@Composable
private fun RollupToggle(rollup: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(true to "Roll up sub-tracks", false to "Self only").forEach { (value, label) ->
            val selected = value == rollup
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.surfaceContainerHigh
                        else MaterialTheme.colorScheme.surfaceContainer
                    )
                    .clickable { onToggle(value) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RangeChips(current: Range, onSelect: (Range) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Range.values().forEach { range ->
            val selected = range == current
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.surfaceContainerHigh
                        else MaterialTheme.colorScheme.surfaceContainer
                    )
                    .clickable { onSelect(range) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = range.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ReviewRow(
    name: String,
    totalMs: Long,
    sessionCount: Int,
    descendantCount: Int,
    childSegments: List<ChildSegment>,
    onClick: () -> Unit
) {
    val hasChildren = childSegments.isNotEmpty()
    val segTotal = childSegments.sumOf { it.ms }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp)
            .animateContentSize()
    ) {
        // ── Main track ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (sessionCount > 0) {
                Text(
                    text = "$sessionCount",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFeatureSettings = TabularNumFeature
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
            Text(
                text = TimeFormat.shortDuration(totalMs),
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFeatureSettings = TabularNumFeature,
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        if (hasChildren) {
            Spacer(Modifier.height(10.dp))
            SubTrackBreakdown(
                segments = childSegments,
                totalMs = segTotal
            )
        } else if (descendantCount > 0) {
            Spacer(Modifier.height(2.dp))
            val plural = if (descendantCount == 1) "" else "s"
            Text(
                text = "incl. $descendantCount nested sub-track$plural",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(8.dp))
        RowDivider(insetStart = 0.dp)
    }
}

/** Inset panel: stacked bar + labelled row per direct child. */
@Composable
private fun SubTrackBreakdown(
    segments: List<ChildSegment>,
    totalMs: Long
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        // Left accent rail — visually separates from the parent row above.
        Box(
            modifier = Modifier
                .width(3.dp)
                .heightIn(min = 48.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            if (totalMs > 0L) {
                ChildBreakdownBar(
                    segments = segments,
                    totalMs = totalMs,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
            }

            segments.forEachIndexed { index, seg ->
                if (index > 0) Spacer(Modifier.height(6.dp))
                SubTrackRow(segment = seg, colorIndex = index)
            }
        }
    }
}

@Composable
private fun SubTrackRow(segment: ChildSegment, colorIndex: Int) {
    val dotColor = segmentColor(colorIndex)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = segment.name,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (segment.ms > 0L) TimeFormat.shortDuration(segment.ms) else "0m",
            style = MaterialTheme.typography.labelSmall.copy(
                fontFeatureSettings = TabularNumFeature
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                alpha = if (segment.ms > 0L) 0.9f else 0.5f
            )
        )
    }
}

@Composable
private fun segmentColor(index: Int): androidx.compose.ui.graphics.Color {
    val base = MaterialTheme.colorScheme.primary
    val alpha = 1f - (index % 3) * 0.18f
    return base.copy(alpha = alpha.coerceIn(0.55f, 1f))
}

/** Stacked bar: each direct child occupies width proportional to its time. */
@Composable
private fun ChildBreakdownBar(
    segments: List<ChildSegment>,
    totalMs: Long,
    modifier: Modifier = Modifier
) {
    if (segments.isEmpty() || totalMs <= 0L) return
    val height = 4.dp
    val track = MaterialTheme.colorScheme.outlineVariant

    Row(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(track)
    ) {
        segments.forEachIndexed { index, seg ->
            val frac = seg.ms.toFloat() / totalMs.toFloat()
            if (frac <= 0f) return@forEachIndexed
            Box(
                modifier = Modifier
                    .weight(frac.coerceAtLeast(0.001f))
                    .fillMaxHeight()
                    .background(segmentColor(index))
            )
        }
    }
}
