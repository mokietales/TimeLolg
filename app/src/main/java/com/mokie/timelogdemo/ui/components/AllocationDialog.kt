package com.mokie.timelogdemo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mokie.timelogdemo.ui.TrackingSession
import com.mokie.timelogdemo.ui.util.TimeFormat

/**
 * Apportions [totalMs] across [tracks] with second precision.
 *
 * Sliders are coupled: dragging one redistributes the remainder across the
 * others proportionally to their current shares. As a result the sum of all
 * track allocations is always exactly [totalMs] (rounded to whole seconds),
 * so the strict DAO invariant `SUM(allocation.durationMs) == session.durationMs`
 * always holds and Save can never produce an inconsistent state.
 */
@Composable
fun AllocationDialog(
    title: String,
    totalMs: Long,
    tracks: List<TrackingSession.TrackRef>,
    initial: Map<Long, Long>?,
    onDismiss: () -> Unit,
    onConfirm: (Map<Long, Long>) -> Unit,
    onRemoveTrack: ((Long) -> Unit)? = null
) {
    if (tracks.isEmpty()) {
        onDismiss()
        return
    }

    val totalSec = (totalMs / 1000L).coerceAtLeast(1L)

    val seedSec: List<Long> = remember(tracks, initial, totalSec) {
        val keysMatch = initial != null &&
            initial.keys == tracks.map { it.id }.toSet() &&
            initial.values.all { it >= 0L }
        if (keysMatch) {
            val raw = tracks.map { (initial!![it.id] ?: 0L) / 1000L }
            val s = raw.sum()
            when {
                s == totalSec -> raw
                s == 0L -> evenSplit(totalSec, tracks.size)
                else -> proportionalScale(raw, totalSec)
            }
        } else {
            evenSplit(totalSec, tracks.size)
        }
    }

    val values = remember(seedSec) {
        mutableStateListOf<Long>().apply { addAll(seedSec) }
    }

    fun setValueAt(i: Int, newVal: Long) {
        rebalance(values, i, newVal, totalSec)
    }

    fun resetEven() {
        val seed = evenSplit(totalSec, tracks.size)
        values.clear()
        values.addAll(seed)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val map = tracks
                        .mapIndexed { i, t -> t.id to (values.getOrElse(i) { 0L } * 1000L) }
                        .toMap()
                    onConfirm(map)
                }
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "总计 ${TimeFormat.clockSeconds(totalSec)}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFeatureSettings = TabularNumFeature
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "均分",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = ::resetEven)
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }

                Spacer(Modifier.height(8.dp))

                tracks.forEachIndexed { i, track ->
                    AllocationSliderRow(
                        trackName = track.name,
                        valueSec = values.getOrElse(i) { 0L },
                        maxSec = totalSec,
                        onValueChange = { newSec -> setValueAt(i, newSec) },
                        onRemove = onRemoveTrack?.let { remove ->
                            { remove(track.id) }
                        }?.takeIf { tracks.size > 1 }
                    )
                    if (i != tracks.lastIndex) Spacer(Modifier.height(4.dp))
                }
            }
        }
    )
}

@Composable
private fun AllocationSliderRow(
    trackName: String,
    valueSec: Long,
    maxSec: Long,
    onValueChange: (Long) -> Unit,
    onRemove: (() -> Unit)?
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = trackName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = TimeFormat.clockSeconds(valueSec),
                style = MaterialTheme.typography.titleSmall.copy(
                    fontFeatureSettings = TabularNumFeature,
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            if (onRemove != null) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "移除",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onRemove)
                        .padding(4.dp)
                        .size(16.dp)
                )
            }
        }
        Slider(
            value = valueSec.toFloat().coerceIn(0f, maxSec.toFloat()),
            onValueChange = { v -> onValueChange(v.toLong().coerceIn(0L, maxSec)) },
            valueRange = 0f..maxSec.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Even split with the remainder added to the first N tracks (one per track). */
private fun evenSplit(totalSec: Long, count: Int): List<Long> {
    if (count <= 0) return emptyList()
    val per = totalSec / count
    val rem = (totalSec % count).toInt()
    return List(count) { i -> per + if (i < rem) 1L else 0L }
}

/** Scale a list of non-negative seconds to sum exactly [target], preserving ratios. */
private fun proportionalScale(values: List<Long>, target: Long): List<Long> {
    val s = values.sum()
    if (s == 0L) return evenSplit(target, values.size)
    var assigned = 0L
    val out = LongArray(values.size)
    for (k in 0 until values.size - 1) {
        val share = (values[k].toDouble() / s * target).toLong().coerceAtLeast(0L)
        out[k] = share
        assigned += share
    }
    out[values.size - 1] = (target - assigned).coerceAtLeast(0L)
    return out.toList()
}

/**
 * Move slider [i] to [newVal] and redistribute the difference across the
 * other tracks proportionally to their current values. Maintains
 * `values.sum() == total` exactly.
 */
private fun rebalance(
    values: androidx.compose.runtime.snapshots.SnapshotStateList<Long>,
    i: Int,
    newVal: Long,
    total: Long
) {
    if (values.isEmpty()) return
    val clamped = newVal.coerceIn(0L, total)
    if (values.size == 1) {
        values[0] = total
        return
    }
    val others = (0 until values.size).filter { it != i }
    val currentOthersSum = others.sumOf { values[it] }
    val targetOthersSum = (total - clamped).coerceAtLeast(0L)

    if (currentOthersSum == 0L) {
        val per = targetOthersSum / others.size
        val rem = (targetOthersSum % others.size).toInt()
        others.forEachIndexed { k, j -> values[j] = per + if (k < rem) 1L else 0L }
    } else {
        var assigned = 0L
        for (k in 0 until others.size - 1) {
            val j = others[k]
            val share = (values[j].toDouble() / currentOthersSum * targetOthersSum)
                .toLong()
                .coerceAtLeast(0L)
            values[j] = share
            assigned += share
        }
        values[others.last()] = (targetOthersSum - assigned).coerceAtLeast(0L)
    }
    values[i] = clamped
}
