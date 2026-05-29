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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
                sessionStart = contrib.startMs
                sessionEnd = contrib.endMs
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
                TextButton(onClick = onDismiss) { Text("Cancel") }
            },
            title = { Text("Loading…") },
            text = {}
        )
        return
    }

    val sessionTotalMs = (sessionEnd - sessionStart).coerceAtLeast(0L)
    val saveEnabled = allocs.isNotEmpty() && allocs.sumOf { it.durationMs } == sessionTotalMs

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    scope.launch {
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
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        title = { Text("Edit session") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "${TimeFormat.weekdayLong(sessionStart)} · " +
                        "${TimeFormat.hhmm(sessionStart)} – ${TimeFormat.hhmm(sessionEnd)} · " +
                        TimeFormat.shortDuration(sessionTotalMs),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFeatureSettings = TabularNumFeature
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))

                Text(
                    text = "NOTE",
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
                                    "Add a note…",
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
                    text = "ALLOCATION",
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
                        text = "Allocation sum ${TimeFormat.shortDuration(sum)} ≠ " +
                            "session total ${TimeFormat.shortDuration(sessionTotalMs)}. " +
                            "Edit split to fix.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }

                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Edit split…",
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
                        text = "Add track…",
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
                        text = "Delete session",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    )

    if (pickerVisible) {
        TrackPickerDialog(
            title = "Tracks for this session",
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
            title = "Distribute time",
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
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { askDelete = false }) { Text("Cancel") }
            },
            title = { Text("Delete this session?") },
            text = { Text("This will permanently remove the session and all its allocations.") }
        )
    }
}
