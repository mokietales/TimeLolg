package com.mokie.timelogdemo.ui.now

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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.PauseCircleOutline
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mokie.timelogdemo.data.SessionDao
import com.mokie.timelogdemo.data.SessionEntity
import com.mokie.timelogdemo.data.SessionTrackAllocationEntity
import com.mokie.timelogdemo.data.TrackDao
import com.mokie.timelogdemo.data.TrackEntity
import com.mokie.timelogdemo.data.TrackSummary
import com.mokie.timelogdemo.data.TrackTree
import com.mokie.timelogdemo.data.observeTrackTree
import com.mokie.timelogdemo.ui.TrackingSession
import com.mokie.timelogdemo.ui.components.AllocationDialog
import com.mokie.timelogdemo.ui.components.InsetGroup
import com.mokie.timelogdemo.ui.components.PageTitle
import com.mokie.timelogdemo.ui.components.RowDivider
import com.mokie.timelogdemo.ui.components.TabularTimerText
import com.mokie.timelogdemo.ui.components.TrackPickerDialog
import com.mokie.timelogdemo.ui.util.TimeFormat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun NowScreen(
    session: TrackingSession,
    trackDao: TrackDao,
    sessionDao: SessionDao,
    onOpenTrackDetail: (Long) -> Unit,
    onOpenMindMap: () -> Unit
) {
    val summaries by trackDao.observeTrackSummaries().collectAsState(initial = emptyList())
    val allTracks by trackDao.observeTracks().collectAsState(initial = emptyList())
    val tree by remember(trackDao, sessionDao) {
        observeTrackTree(trackDao, sessionDao)
    }.collectAsState(initial = null)
    val scope = rememberCoroutineScope()

    var pickerVisible by remember { mutableStateOf(false) }
    var pendingStop by remember { mutableStateOf<TrackingSession.StopSnapshot?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        PageTitle(
            eyebrow = TimeFormat.weekdayLong(System.currentTimeMillis()),
            title = "Now"
        )

        if (session.isActive) {
            ActiveSession(
                session = session,
                onAddTrack = { pickerVisible = true },
                onRemoveTrack = { id -> session.removeTrack(id) },
                onStop = {
                    val snap = session.snapshotForStop() ?: return@ActiveSession
                    if (snap.tracks.size == 1) {
                        commitSingleTrack(scope, sessionDao, snap)
                        session.clear()
                    } else {
                        pendingStop = snap
                    }
                },
                onCancel = { session.clear() }
            )
        } else {
            IdleSection(
                summaries = summaries,
                tree = tree,
                onOpenDetail = onOpenTrackDetail,
                onOpenMindMap = onOpenMindMap,
                onCreateTrack = { name ->
                    scope.launch {
                        ensureTrackId(trackDao, name)?.let { onOpenTrackDetail(it) }
                    }
                }
            )
        }

        Spacer(Modifier.height(32.dp))
    }

    if (pickerVisible) {
        TrackPickerDialog(
            title = "Tracks for this session",
            available = allTracks,
            initiallySelected = session.tracks.map { it.id }.toSet(),
            onDismiss = { pickerVisible = false },
            onConfirm = { refs ->
                session.replaceTracks(refs)
                pickerVisible = false
            },
            onCreate = { name, onCreated ->
                scope.launch {
                    val newId = ensureTrackId(trackDao, name)
                    val entity = newId?.let { id -> trackDao.findTrackById(id) }
                    if (newId != null && entity != null) {
                        onCreated(newId, entity.name)
                    }
                }
            }
        )
    }

    pendingStop?.let { snap ->
        AllocationDialog(
            title = "Allocate this session",
            totalMs = snap.effectiveDurationMs,
            tracks = snap.tracks,
            initial = null,
            onDismiss = { pendingStop = null },
            onConfirm = { allocMap ->
                commitMultiTrack(scope, sessionDao, snap, allocMap)
                session.clear()
                pendingStop = null
            }
        )
    }
}

private suspend fun ensureTrackId(trackDao: TrackDao, name: String): Long? {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return null
    val existing = trackDao.findTrackIdByName(trimmed)
    if (existing != null) return existing
    trackDao.insertTrack(TrackEntity(name = trimmed, createdAtMs = System.currentTimeMillis()))
    return trackDao.findTrackIdByName(trimmed)
}

private fun commitSingleTrack(
    scope: kotlinx.coroutines.CoroutineScope,
    sessionDao: SessionDao,
    snap: TrackingSession.StopSnapshot
) {
    if (snap.effectiveDurationMs <= 0L) return
    val track = snap.tracks.single()
    scope.launch {
        sessionDao.insertSessionWithAllocations(
            session = SessionEntity(
                startMs = snap.startMs,
                endMs = snap.startMs + snap.effectiveDurationMs,
                note = snap.note.trim(),
                createdAtMs = System.currentTimeMillis()
            ),
            allocations = listOf(
                SessionTrackAllocationEntity(
                    sessionId = 0L,
                    trackId = track.id,
                    durationMs = snap.effectiveDurationMs
                )
            )
        )
    }
}

private fun commitMultiTrack(
    scope: kotlinx.coroutines.CoroutineScope,
    sessionDao: SessionDao,
    snap: TrackingSession.StopSnapshot,
    allocMap: Map<Long, Long>
) {
    val effective = snap.effectiveDurationMs
    if (effective <= 0L) return
    val allocs = allocMap.map { (trackId, ms) ->
        SessionTrackAllocationEntity(sessionId = 0L, trackId = trackId, durationMs = ms)
    }
    scope.launch {
        sessionDao.insertSessionWithAllocations(
            session = SessionEntity(
                startMs = snap.startMs,
                endMs = snap.startMs + effective,
                note = snap.note.trim(),
                createdAtMs = System.currentTimeMillis()
            ),
            allocations = allocs
        )
    }
}

@Composable
private fun ActiveSession(
    session: TrackingSession,
    onAddTrack: () -> Unit,
    onRemoveTrack: (Long) -> Unit,
    onStop: () -> Unit,
    onCancel: () -> Unit
) {
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(session.startMs, session.pausedAtMs) {
        while (session.isActive && !session.isPaused) {
            nowMs = System.currentTimeMillis()
            delay(1_000L)
        }
        nowMs = System.currentTimeMillis()
    }

    val elapsedSec = session.elapsedMs(nowMs) / 1000L
    val startMs = session.startMs ?: 0L
    val primary = session.primaryTrack?.name ?: "Untitled"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(56.dp))

        // Single-line slot: fixed height + clip so Text cannot grow to a second line.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .clipToBounds(),
            contentAlignment = Alignment.Center
        ) {
            TabularTimerText(
                text = TimeFormat.hms(elapsedSec),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = primary,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = if (session.isPaused) "Paused" else "Started at ${TimeFormat.hhmm(startMs)}",
            style = MaterialTheme.typography.bodySmall,
            color = if (session.isPaused)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))

        TrackChips(
            tracks = session.tracks,
            onRemove = onRemoveTrack,
            onAdd = onAddTrack
        )

        Spacer(Modifier.height(32.dp))

        NoteField(
            value = session.note,
            onValueChange = { session.updateNote(it) }
        )

        Spacer(Modifier.height(40.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircleAction(
                icon = if (session.isPaused) Icons.Outlined.PlayCircleOutline else Icons.Outlined.PauseCircleOutline,
                label = if (session.isPaused) "Resume" else "Pause",
                onClick = { session.togglePause() }
            )
            CircleAction(
                icon = Icons.Outlined.StopCircle,
                label = "Stop",
                primary = true,
                onClick = onStop
            )
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = "Discard",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onCancel)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun TrackChips(
    tracks: List<TrackingSession.TrackRef>,
    onRemove: (Long) -> Unit,
    onAdd: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Tracks",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tracks.forEach { track ->
                    Chip(
                        label = track.name,
                        onClick = if (tracks.size > 1) {
                            { onRemove(track.id) }
                        } else null
                    )
                }
                AddChip(onClick = onAdd)
            }
            if (tracks.size > 1) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Tap a track to remove · time split on Stop",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun Chip(label: String, onClick: (() -> Unit)?) {
    val rowModifier = Modifier
        .clip(RoundedCornerShape(20.dp))
        .background(MaterialTheme.colorScheme.surfaceContainer)
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }
        .padding(horizontal = 12.dp, vertical = 6.dp)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = rowModifier) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun AddChip(onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.Add,
            contentDescription = "Add track",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "Track",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun NoteField(
    value: String,
    onValueChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = false,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            textStyle = LocalTextStyle.current.copy(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                textAlign = TextAlign.Center
            ),
            decorationBox = { inner ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = "Add a note…",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    inner()
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun CircleAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    primary: Boolean = false
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 14.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (primary)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(56.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun IdleSection(
    summaries: List<TrackSummary>,
    tree: TrackTree?,
    onOpenDetail: (Long) -> Unit,
    onOpenMindMap: () -> Unit,
    onCreateTrack: (String) -> Unit
) {
    var showInput by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        ) {
            Text(
                text = "Nothing tracked.",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Pick a track to see its detail and start a session.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(20.dp))

        InsetGroup {
            MindMapEntryRow(onClick = onOpenMindMap)
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 8.dp)
        ) {
            Text(
                text = "RECENT",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        InsetGroup {
            val visible = summaries.take(8)
            visible.forEachIndexed { index, item ->
                val totalMs = tree?.totalMs?.get(item.trackId) ?: item.totalMs
                val descCount = tree?.descendantCount?.get(item.trackId) ?: 0
                TrackSummaryRow(
                    item = item,
                    rolledUpMs = totalMs,
                    descendantCount = descCount,
                    onClick = { onOpenDetail(item.trackId) }
                )
                if (index != visible.lastIndex || !showInput) {
                    RowDivider()
                }
            }

            if (showInput) {
                NewTrackRow(
                    value = newName,
                    onValueChange = { newName = it },
                    onSubmit = {
                        val name = newName.trim()
                        if (name.isNotEmpty()) {
                            onCreateTrack(name)
                            newName = ""
                            showInput = false
                        }
                    },
                    onCancel = {
                        newName = ""
                        showInput = false
                    }
                )
            } else {
                AddTrackRow(onClick = { showInput = true })
            }
        }
    }
}

@Composable
private fun MindMapEntryRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.AccountTree,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = "Mind map",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun TrackSummaryRow(
    item: TrackSummary,
    rolledUpMs: Long,
    descendantCount: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f))
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            val sub = when {
                rolledUpMs <= 0L && item.sessionCount == 0 -> "No sessions yet"
                descendantCount > 0 -> {
                    "${TimeFormat.shortDuration(rolledUpMs)} · " +
                        "incl. $descendantCount sub-track" +
                        if (descendantCount == 1) "" else "s"
                }
                else -> {
                    "${TimeFormat.shortDuration(rolledUpMs)} · ${item.sessionCount} session" +
                        if (item.sessionCount == 1) "" else "s"
                }
            }
            Text(
                text = sub,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun AddTrackRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = "New track",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun NewTrackRow(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            textStyle = LocalTextStyle.current.copy(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = MaterialTheme.typography.bodyLarge.fontSize
            ),
            decorationBox = { inner ->
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (value.isEmpty()) {
                        Text(
                            text = "Track name",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    inner()
                }
            },
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp)
        )

        Text(
            text = "Cancel",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onCancel)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "Open",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onSubmit)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        )
    }
}
