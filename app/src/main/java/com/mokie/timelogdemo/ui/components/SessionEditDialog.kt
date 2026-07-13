package com.mokie.timelogdemo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mokie.timelogdemo.data.SessionDao
import com.mokie.timelogdemo.data.SessionTrackAllocationEntity
import com.mokie.timelogdemo.data.TrackDao
import com.mokie.timelogdemo.data.TrackEntity
import com.mokie.timelogdemo.ui.TrackingSession
import com.mokie.timelogdemo.ui.util.TimeFormat
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/**
 * Shared session-edit dialog. Lets the user:
 *  - Edit the session note
 *  - Add/remove track allocations
 *  - Re-apportion durations across tracks (strict sum)
 *  - Delete the entire session
 *
 * Used from TrackDetail and from the Sessions tab.
 */
@Composable
fun SessionEditDialog(
    sessionId: Long,
    sessionDao: SessionDao,
    trackDao: TrackDao,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var loaded by remember(sessionId) { mutableStateOf(false) }
    var noteText by remember(sessionId) { mutableStateOf("") }
    var sessionStart by remember(sessionId) { mutableStateOf(0L) }
    var sessionEnd by remember(sessionId) { mutableStateOf(0L) }
    var allocs by remember(sessionId) {
        mutableStateOf<List<SessionDao.SessionAllocationOf>>(emptyList())
    }
    var allTracks by remember { mutableStateOf<List<TrackEntity>>(emptyList()) }
    var pickerVisible by remember { mutableStateOf(false) }
    var allocDialogVisible by remember { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf(false) }

    LaunchedEffect(sessionId) {
        allocs = sessionDao.getAllocationsForSession(sessionId)
        allTracks = trackDao.observeTracks().firstOrNull().orEmpty()
        allocs.firstOrNull()?.let { first ->
            val contrib = sessionDao
                .observeContributionsForTrack(first.trackId)
                .firstOrNull()
                ?.firstOrNull { it.sessionId == sessionId }
            if (contrib != null) {
                // Floor to whole seconds: timer sessions carry a millisecond
                // fraction on both endpoints. Saving writes end = start + total
                // (whole seconds), so an unfloored start would shift the end a
                // sub-second earlier than what the user picked — enough to
                // display the previous minute.
                sessionStart = (contrib.startMs / 1000L) * 1000L
                sessionEnd = (contrib.endMs / 1000L) * 1000L
                noteText = contrib.note
            }
        }
        loaded = true
    }

    if (!loaded) {
        AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("取消") }
            },
            title = { Text("加载中…") },
            text = {}
        )
        return
    }

    // Snap to whole seconds so second-precision allocations can sum exactly.
    val sessionTotalMs = TimeFormat.snapDurationMs(sessionEnd - sessionStart)
    val validRange = sessionEnd > sessionStart && sessionTotalMs > 0L
    val saveEnabled = validRange &&
        allocs.isNotEmpty() &&
        allocs.sumOf { it.durationMs } == sessionTotalMs

    // Keep allocations consistent when the session span changes: rescale all
    // shares proportionally to the new total so the strict-sum invariant holds
    // without forcing the user through the allocation dialog.
    fun applyTimes(newStart: Long, newEnd: Long) {
        val newTotal = TimeFormat.snapDurationMs(newEnd - newStart)
        if (newTotal > 0L && allocs.isNotEmpty()) {
            allocs = rescaleAllocations(allocs, newTotal)
        }
        sessionStart = newStart
        sessionEnd = newEnd
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    scope.launch {
                        sessionDao.updateSessionTimes(
                            sessionId = sessionId,
                            startMs = sessionStart,
                            endMs = sessionStart + sessionTotalMs
                        )
                        sessionDao.updateSessionNote(sessionId, noteText.trim())
                        sessionDao.replaceAllocations(
                            sessionId = sessionId,
                            allocations = allocs.map {
                                SessionTrackAllocationEntity(
                                    sessionId = sessionId,
                                    trackId = it.trackId,
                                    durationMs = it.durationMs
                                )
                            }
                        )
                        onDismiss()
                    }
                },
                enabled = saveEnabled
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        title = { Text("编辑记录") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "时间",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                SessionTimeEditor(
                    startMs = sessionStart,
                    endMs = sessionEnd,
                    onStartChange = { applyTimes(it, sessionEnd) },
                    onEndChange = { applyTimes(sessionStart, it) }
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (validRange) {
                        "时长 · ${TimeFormat.shortDuration(sessionTotalMs)}"
                    } else {
                        "结束时间必须晚于开始时间"
                    },
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFeatureSettings = TabularNumFeature
                    ),
                    color = if (validRange) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(16.dp))

                Text(
                    text = "备注",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                BasicTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    singleLine = false,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    textStyle = LocalTextStyle.current.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = MaterialTheme.typography.bodyLarge.fontSize
                    ),
                    decorationBox = { inner ->
                        Box(modifier = Modifier.fillMaxWidth()) {
                            if (noteText.isEmpty()) {
                                Text(
                                    "添加备注…",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            inner()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                )

                Spacer(Modifier.height(16.dp))

                Text(
                    text = "时间分配",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))

                allocs.forEach { a ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = a.trackName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = TimeFormat.shortDuration(a.durationMs),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFeatureSettings = TabularNumFeature,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                if (!saveEnabled && allocs.isNotEmpty()) {
                    val sum = allocs.sumOf { it.durationMs }
                    Text(
                        text = "分配合计 ${TimeFormat.shortDuration(sum)} ≠ " +
                            "记录总计 ${TimeFormat.shortDuration(sessionTotalMs)}。" +
                            "请调整分配。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }

                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "调整分配…",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { allocDialogVisible = true }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                    Text(
                        text = "添加主题…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { pickerVisible = true }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }

                Spacer(Modifier.height(20.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { askDelete = true }
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "删除记录",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    )

    if (pickerVisible) {
        TrackPickerDialog(
            title = "此记录的主题",
            available = allTracks,
            initiallySelected = allocs.map { it.trackId }.toSet(),
            onDismiss = { pickerVisible = false },
            onConfirm = { refs ->
                val existing = allocs.associate { it.trackId to it.durationMs }
                val targetIds = refs.map { it.id }.toSet()
                val keptMs = existing.filterKeys { it in targetIds }.values.sum()
                val remaining = (sessionTotalMs - keptMs).coerceAtLeast(0L)
                val newOnes = refs.filter { it.id !in existing }
                val perNew = if (newOnes.isNotEmpty()) remaining / newOnes.size else 0L
                val remainderTrackId = newOnes.firstOrNull()?.id
                val rebuilt = refs.map { ref ->
                    val ms = existing[ref.id] ?: (
                        perNew + if (ref.id == remainderTrackId) {
                            remaining - perNew * newOnes.size
                        } else 0L
                        )
                    SessionDao.SessionAllocationOf(
                        trackId = ref.id,
                        trackName = ref.name,
                        durationMs = ms
                    )
                }
                allocs = rebuilt
                pickerVisible = false
                if (rebuilt.sumOf { it.durationMs } != sessionTotalMs) {
                    allocDialogVisible = true
                }
            },
            onCreate = { name, onCreated ->
                scope.launch {
                    val trimmed = name.trim()
                    val existing = trackDao.findTrackIdByName(trimmed)
                    val id = existing ?: run {
                        trackDao.insertTrack(
                            TrackEntity(name = trimmed, createdAtMs = System.currentTimeMillis())
                        )
                        trackDao.findTrackIdByName(trimmed)
                    } ?: return@launch
                    allTracks = trackDao.observeTracks().firstOrNull().orEmpty()
                    onCreated(id, trimmed)
                }
            }
        )
    }

    if (allocDialogVisible) {
        AllocationDialog(
            title = "分配时间",
            totalMs = sessionTotalMs,
            tracks = allocs.map { TrackingSession.TrackRef(it.trackId, it.trackName) },
            initial = allocs.associate { it.trackId to it.durationMs },
            onDismiss = { allocDialogVisible = false },
            onConfirm = { newMap ->
                allocs = allocs.mapNotNull { a ->
                    newMap[a.trackId]?.let { a.copy(durationMs = it) }
                }
                allocDialogVisible = false
            },
            onRemoveTrack = { id ->
                allocs = allocs.filterNot { it.trackId == id }
            }
        )
    }

    if (askDelete) {
        AlertDialog(
            onDismissRequest = { askDelete = false },
            confirmButton = {
                TextButton(onClick = {
                    askDelete = false
                    scope.launch {
                        sessionDao.deleteSessionCascade(sessionId)
                        onDismiss()
                    }
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { askDelete = false }) { Text("取消") }
            },
            title = { Text("删除这条记录？") },
            text = { Text("将永久删除此记录及其所有时间分配。") }
        )
    }
}

/**
 * Scale allocation shares proportionally so they sum exactly to [targetMs]
 * (at second precision). The last row absorbs rounding remainder.
 */
private fun rescaleAllocations(
    allocs: List<SessionDao.SessionAllocationOf>,
    targetMs: Long
): List<SessionDao.SessionAllocationOf> {
    if (allocs.isEmpty()) return allocs
    val targetSec = targetMs / 1000L
    val currentSum = allocs.sumOf { it.durationMs / 1000L }
    val out = ArrayList<SessionDao.SessionAllocationOf>(allocs.size)
    var assigned = 0L
    for ((i, a) in allocs.withIndex()) {
        val sec = if (i == allocs.lastIndex) {
            (targetSec - assigned).coerceAtLeast(0L)
        } else if (currentSum == 0L) {
            targetSec / allocs.size
        } else {
            ((a.durationMs / 1000L).toDouble() / currentSum * targetSec).toLong()
        }
        assigned += sec
        out.add(a.copy(durationMs = sec * 1000L))
    }
    return out
}
