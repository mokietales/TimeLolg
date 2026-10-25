package com.mokie.timelogdemo.ui.components

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
import androidx.compose.runtime.collectAsState
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
import com.mokie.timelogdemo.data.Allocation
import com.mokie.timelogdemo.data.SessionAllocationOf
import com.mokie.timelogdemo.data.SessionEntity
import com.mokie.timelogdemo.data.TimeLogRepository
import com.mokie.timelogdemo.tracking.TrackingSession
import com.mokie.timelogdemo.ui.util.TimeFormat
import kotlinx.coroutines.launch

/**
 * Shared session-edit dialog. Lets the user:
 *  - Move the start/end time (allocations rescale to keep the strict sum)
 *  - Edit the session note
 *  - Add/remove track allocations and rebalance them
 *  - Delete the session
 *
 * Used from TrackDetail and from the Sessions tab.
 */
@Composable
fun SessionEditDialog(
    sessionId: Long,
    repository: TimeLogRepository,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var original by remember(sessionId) { mutableStateOf<SessionEntity?>(null) }
    var noteText by remember(sessionId) { mutableStateOf("") }
    var sessionStart by remember(sessionId) { mutableStateOf(0L) }
    var sessionEnd by remember(sessionId) { mutableStateOf(0L) }
    var pausedMs by remember(sessionId) { mutableStateOf(0L) }
    var allocs by remember(sessionId) { mutableStateOf<List<SessionAllocationOf>>(emptyList()) }
    val allTracks by repository.tracks.collectAsState(initial = emptyList())
    var pickerVisible by remember { mutableStateOf(false) }
    var allocDialogVisible by remember { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }

    LaunchedEffect(sessionId) {
        val loaded = repository.sessionForEdit(sessionId)
        if (loaded == null) {
            onDismiss()
            return@LaunchedEffect
        }
        // Floor to whole seconds so second-precision allocations can sum exactly.
        sessionStart = (loaded.session.startMs / 1000L) * 1000L
        sessionEnd = (loaded.session.endMs / 1000L) * 1000L
        pausedMs = (loaded.session.pausedMs / 1000L) * 1000L
        noteText = loaded.session.note
        allocs = loaded.allocations
        original = loaded.session
    }

    val base = original
    if (base == null) {
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

    fun totalFor(start: Long, end: Long): Long {
        val span = (end - start).coerceAtLeast(0L)
        return TimeFormat.snapDurationMs(span - pausedMs.coerceAtMost(span))
    }

    val sessionTotalMs = totalFor(sessionStart, sessionEnd)
    val effectivePausedMs = pausedMs.coerceAtMost((sessionEnd - sessionStart).coerceAtLeast(0L))
    val validRange = sessionEnd > sessionStart && sessionTotalMs > 0L
    val saveEnabled = validRange &&
        allocs.isNotEmpty() &&
        allocs.sumOf { it.durationMs } == sessionTotalMs

    // Moving the span rescales every share proportionally so the strict-sum
    // invariant holds without forcing the user through the allocation dialog.
    fun applyTimes(newStart: Long, newEnd: Long) {
        val newTotal = totalFor(newStart, newEnd)
        if (newTotal > 0L && allocs.isNotEmpty()) {
            val scaled = Allocation.scaleMsToTotal(allocs.map { it.durationMs }, newTotal)
            allocs = allocs.mapIndexed { i, a -> a.copy(durationMs = scaled[i]) }
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
                        val ok = repository.updateSession(
                            session = base.copy(
                                startMs = sessionStart,
                                endMs = sessionStart + effectivePausedMs + sessionTotalMs,
                                pausedMs = effectivePausedMs,
                                note = noteText
                            ),
                            allocMs = allocs.associate { it.trackId to it.durationMs }
                        )
                        if (ok) onDismiss() else saveFailed = true
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
                FieldLabel("时间")
                Spacer(Modifier.height(6.dp))
                SessionTimeEditor(
                    startMs = sessionStart,
                    endMs = sessionEnd,
                    onStartChange = { applyTimes(it, sessionEnd) },
                    onEndChange = { applyTimes(sessionStart, it) }
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = when {
                        !validRange -> "结束时间必须晚于开始时间"
                        effectivePausedMs >= 1000L ->
                            "时长 · ${TimeFormat.durationPrecise(sessionTotalMs)}" +
                                "（已扣除暂停 ${TimeFormat.duration(effectivePausedMs)}）"
                        else -> "时长 · ${TimeFormat.durationPrecise(sessionTotalMs)}"
                    },
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFeatureSettings = TabularNumFeature
                    ),
                    color = if (validRange) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.error
                )
                if (validRange) {
                    OverlapWarning(
                        repository = repository,
                        startMs = sessionStart,
                        endMs = sessionEnd,
                        excludeSessionId = sessionId
                    )
                }
                Spacer(Modifier.height(16.dp))

                FieldLabel("备注")
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

                FieldLabel("时间分配")
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
                            text = TimeFormat.durationPrecise(a.durationMs),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFeatureSettings = TabularNumFeature,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                if (validRange && !saveEnabled && allocs.isNotEmpty()) {
                    val sum = allocs.sumOf { it.durationMs }
                    Text(
                        text = "分配合计 ${TimeFormat.durationPrecise(sum)} ≠ " +
                            "记录总计 ${TimeFormat.durationPrecise(sessionTotalMs)}。" +
                            "请调整分配。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                if (saveFailed) {
                    Text(
                        text = "保存失败，请重试。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }

                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinkAction(text = "调整分配…", bold = true) { allocDialogVisible = true }
                    LinkAction(text = "添加主题…") { pickerVisible = true }
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
                // Kept tracks keep their share; newly added ones split what's left.
                val existing = allocs.associate { it.trackId to it.durationMs }
                val targetIds = refs.map { it.id }.toSet()
                val keptMs = existing.filterKeys { it in targetIds }.values.sum()
                val remaining = (sessionTotalMs - keptMs).coerceAtLeast(0L)
                val newOnes = refs.filter { it.id !in existing }
                val newShares = Allocation.scaleMsToTotal(List(newOnes.size) { 0L }, remaining)
                    .let { shares -> newOnes.mapIndexed { i, r -> r.id to shares[i] }.toMap() }
                val rebuilt = refs.map { ref ->
                    SessionAllocationOf(
                        trackId = ref.id,
                        trackName = ref.name,
                        durationMs = existing[ref.id] ?: newShares[ref.id] ?: 0L
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
                    repository.ensureTrack(name)?.let { onCreated(it.id, it.name) }
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
                        repository.deleteSession(sessionId)
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

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun LinkAction(text: String, bold: Boolean = false, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium.copy(
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal
        ),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    )
}
