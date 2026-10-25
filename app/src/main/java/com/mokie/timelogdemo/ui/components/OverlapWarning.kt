package com.mokie.timelogdemo.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mokie.timelogdemo.data.OverlapRow
import com.mokie.timelogdemo.data.TimeLogRepository
import com.mokie.timelogdemo.ui.util.TimeFormat
import kotlinx.coroutines.delay

/**
 * Advisory line shown under a time editor when the span overlaps existing records.
 * Overlap is allowed (e.g. listening to a podcast while commuting), so it never
 * blocks saving.
 */
@Composable
fun OverlapWarning(
    repository: TimeLogRepository,
    startMs: Long,
    endMs: Long,
    excludeSessionId: Long = 0L
) {
    var overlaps by remember { mutableStateOf<List<OverlapRow>>(emptyList()) }
    LaunchedEffect(startMs, endMs, excludeSessionId) {
        delay(150) // let picker changes settle
        overlaps = repository.overlapping(startMs, endMs, excludeSessionId)
    }
    val first = overlaps.firstOrNull() ?: return
    val day = if (TimeFormat.startOfDayMs(first.startMs) != TimeFormat.startOfDayMs(startMs)) {
        TimeFormat.monthDay(first.startMs) + " "
    } else {
        ""
    }
    val more = if (overlaps.size > 1) " 等 ${overlaps.size} 条" else ""
    Text(
        text = "与已有记录重叠：$day${TimeFormat.hhmm(first.startMs)}–" +
            "${TimeFormat.hhmm(first.endMs)} ${first.trackNames}$more",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.tertiary,
        modifier = Modifier.padding(top = 4.dp)
    )
}
