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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.mokie.timelogdemo.data.TimeLogRepository
import com.mokie.timelogdemo.tracking.TrackingSession
import com.mokie.timelogdemo.ui.util.TimeFormat
import kotlinx.coroutines.launch

/**
 * Dialog for manually logging a past session (start/end time, note, track allocation).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualSessionDialog(
    repository: TimeLogRepository,
    initialTrackId: Long? = null,
    onDismiss: () -> Unit,
    onSaved: () -> Unit = onDismiss
) {
    val scope = rememberCoroutineScope()
    val now = remember { System.currentTimeMillis() }
    val defaultEnd = remember { TimeFormat.roundToMinute(now) }
    val defaultStart = remember { defaultEnd - 3_600_000L }

    var startMs by remember { mutableStateOf(defaultStart) }
    var endMs by remember { mutableStateOf(defaultEnd) }
    var noteText by remember { mutableStateOf("") }
    var selectedTracks by remember { mutableStateOf<List<TrackingSession.TrackRef>>(emptyList()) }
    val allTracks by repository.tracks.collectAsState(initial = emptyList())
    var trackPickerVisible by remember { mutableStateOf(false) }
    var allocDialogVisible by remember { mutableStateOf(false) }

    LaunchedEffect(initialTrackId) {
        val t = initialTrackId?.let { repository.findTrack(it) } ?: return@LaunchedEffect
        if (selectedTracks.isEmpty()) {
            selectedTracks = listOf(TrackingSession.TrackRef(t.id, t.name))
        }
    }

    val durationMs = remember(startMs, endMs) {
        TimeFormat.snapDurationMs(endMs - startMs)
    }
    val validRange = endMs > startMs && durationMs > 0L
    val canSave = validRange && selectedTracks.isNotEmpty()

    fun save(allocMap: Map<Long, Long>) {
        scope.launch {
            val ok = repository.insertManualSession(
                startMs = startMs,
                durationMs = durationMs,
                note = noteText,
                allocMs = allocMap
            )
            allocDialogVisible = false
            if (ok) onSaved()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    if (!canSave) return@TextButton
                    if (selectedTracks.size == 1) {
                        save(mapOf(selectedTracks.single().id to durationMs))
                    } else {
                        allocDialogVisible = true
                    }
                },
                enabled = canSave
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        title = { Text("补录时间") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "时间",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))

                SessionTimeEditor(
                    startMs = startMs,
                    endMs = endMs,
                    onStartChange = { startMs = it },
                    onEndChange = { endMs = it }
                )

                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (validRange) {
                        "时长 · ${TimeFormat.durationPrecise(durationMs)}"
                    } else {
                        "结束时间必须晚于开始时间"
                    },
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFeatureSettings = TabularNumFeature
                    ),
                    color = if (validRange) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.error
                )

                DurationPresets(
                    onSelectDuration = { spanMs ->
                        startMs = endMs - spanMs
                    }
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
                                    "选填备注…",
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
                    text = "主题",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))

                if (selectedTracks.isEmpty()) {
                    Text(
                        text = "未选择主题",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    selectedTracks.forEach { t ->
                        Text(
                            text = t.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (selectedTracks.isEmpty()) "选择主题…" else "更改主题…",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { trackPickerVisible = true }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }
        }
    )

    if (trackPickerVisible) {
        TrackPickerDialog(
            title = "此记录的主题",
            available = allTracks,
            initiallySelected = selectedTracks.map { it.id }.toSet(),
            onDismiss = { trackPickerVisible = false },
            onConfirm = { refs ->
                selectedTracks = refs
                trackPickerVisible = false
            },
            onCreate = { name, onCreated ->
                scope.launch {
                    repository.ensureTrack(name)?.let { onCreated(it.id, it.name) }
                }
            }
        )
    }

    if (allocDialogVisible && selectedTracks.size > 1) {
        AllocationDialog(
            title = "分配时间到各主题",
            totalMs = durationMs,
            tracks = selectedTracks,
            initial = null,
            onDismiss = { allocDialogVisible = false },
            onConfirm = { allocMap -> save(allocMap) }
        )
    }
}

@Composable
private fun DurationPresets(onSelectDuration: (Long) -> Unit) {
    val presets = listOf(
        "15分" to 15 * 60_000L,
        "30分" to 30 * 60_000L,
        "1小时" to 3_600_000L,
        "2小时" to 7_200_000L
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        presets.forEach { (label, span) ->
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onSelectDuration(span) }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }
    }
}