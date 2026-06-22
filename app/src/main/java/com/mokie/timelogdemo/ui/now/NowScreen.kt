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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.PauseCircleOutline
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
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
import com.mokie.timelogdemo.ui.components.TabularNumFeature
import com.mokie.timelogdemo.ui.components.TrackPickerDialog
import com.mokie.timelogdemo.ui.theme.trackColor
import com.mokie.timelogdemo.ui.util.TimeFormat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val TIMER_PEEK_HEIGHT = 116.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowScreen(
    session: TrackingSession,
    trackDao: TrackDao,
    sessionDao: SessionDao,
    onOpenTrackDetail: (Long) -> Unit,
    onOpenStarMap: () -> Unit
) {
    val summaries by trackDao.observeTrackSummaries().collectAsState(initial = emptyList())
    val allTracks by trackDao.observeTracks().collectAsState(initial = emptyList())
    val tree by remember(trackDao, sessionDao) {
        observeTrackTree(trackDao, sessionDao)
    }.collectAsState(initial = null)
    val scope = rememberCoroutineScope()

    var pickerVisible by remember { mutableStateOf(false) }
    var pendingStop by remember { mutableStateOf<TrackingSession.StopSnapshot?>(null) }
    // Shown when the user stops a blank (theme-less) session: pick a theme, then commit.
    var stopPickVisible by remember { mutableStateOf(false) }

    fun finishStop() {
        session.snapshotForStop()?.let { snap ->
            if (snap.tracks.size == 1) {
                commitSingleTrack(scope, sessionDao, snap)
                session.clear()
            } else {
                pendingStop = snap
            }
        }
    }

    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            initialValue = if (session.isActive) SheetValue.PartiallyExpanded else SheetValue.Hidden,
            skipHiddenState = false
        )
    )

    // Drive the sheet from session state: rise to a peek when a timer starts,
    // slide away entirely when it stops or is discarded.
    LaunchedEffect(session.isActive) {
        if (session.isActive) {
            scaffoldState.bottomSheetState.partialExpand()
        } else {
            scaffoldState.bottomSheetState.hide()
        }
    }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = if (session.isActive) TIMER_PEEK_HEIGHT else 0.dp,
        containerColor = MaterialTheme.colorScheme.background,
        sheetContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        sheetShadowElevation = 4.dp,
        sheetContent = {
            if (session.isActive) {
                ActiveSessionSheet(
                    session = session,
                    onAddTrack = { pickerVisible = true },
                    onRemoveTrack = { id -> session.removeTrack(id) },
                    onStop = {
                        if (session.tracks.isEmpty()) {
                            stopPickVisible = true
                        } else {
                            finishStop()
                        }
                    },
                    onCancel = { session.clear() }
                )
            } else {
                Spacer(Modifier.height(1.dp))
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            PageTitle(
                eyebrow = TimeFormat.weekdayLong(System.currentTimeMillis()),
                title = "现在"
            )

            IdleSection(
                summaries = summaries,
                tree = tree,
                timerActive = session.isActive,
                onOpenDetail = onOpenTrackDetail,
                onOpenStarMap = onOpenStarMap,
                onQuickStartBlank = { session.startBlank() },
                onQuickStart = { item ->
                    session.start(TrackingSession.TrackRef(id = item.trackId, name = item.name))
                },
                onCreateTrack = { name ->
                    scope.launch {
                        ensureTrackId(trackDao, name)?.let { onOpenTrackDetail(it) }
                    }
                }
            )

            Spacer(Modifier.height(if (session.isActive) TIMER_PEEK_HEIGHT + 24.dp else 32.dp))
        }
    }

    if (pickerVisible) {
        TrackPickerDialog(
            title = "此记录的主题",
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

    if (stopPickVisible) {
        TrackPickerDialog(
            title = "为这段时间选择主题",
            available = allTracks,
            initiallySelected = emptySet(),
            onDismiss = { stopPickVisible = false },
            onConfirm = { refs ->
                stopPickVisible = false
                if (refs.isNotEmpty()) {
                    session.replaceTracks(refs)
                    finishStop()
                }
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
            title = "分配此记录的时间",
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

/**
 * The running timer rendered as a bottom pull-up sheet. The top [TIMER_PEEK_HEIGHT]
 * stays visible as a compact bar (theme + live time); dragging up reveals the full
 * controls (themes, note, pause/stop/discard).
 */
@Composable
private fun ActiveSessionSheet(
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
    val primary = session.primaryTrack?.name ?: "未分组"

    Column(modifier = Modifier.fillMaxWidth()) {
        // Compact bar — the part that peeks above the fold.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .height(64.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(
                        if (session.isPaused)
                            MaterialTheme.colorScheme.onSurfaceVariant
                        else
                            MaterialTheme.colorScheme.primary
                    )
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = primary,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = if (session.isPaused) "已暂停" else "计时中 · ${TimeFormat.hhmm(startMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (session.isPaused)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = TimeFormat.hms(elapsedSec),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFeatureSettings = TabularNumFeature
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.width(6.dp))
            CompactControl(
                icon = if (session.isPaused) Icons.Outlined.PlayCircleOutline else Icons.Outlined.PauseCircleOutline,
                contentDescription = if (session.isPaused) "继续" else "暂停",
                onClick = { session.togglePause() }
            )
            CompactControl(
                icon = Icons.Outlined.StopCircle,
                contentDescription = "停止",
                primary = true,
                onClick = onStop
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .height(0.5.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )

        Spacer(Modifier.height(24.dp))

        TrackChips(
            tracks = session.tracks,
            onRemove = onRemoveTrack,
            onAdd = onAddTrack
        )

        Spacer(Modifier.height(24.dp))

        NoteField(
            value = session.note,
            onValueChange = { session.updateNote(it) }
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = "放弃",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onCancel)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )

        Spacer(Modifier.height(24.dp))
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
                text = "主题",
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
                        dotColor = trackColor(track.id),
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
                    text = "点击主题可移除 · 停止时分配时间",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun Chip(
    label: String,
    onClick: (() -> Unit)?,
    dotColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary
) {
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
                .background(dotColor)
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
            contentDescription = "添加主题",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "主题",
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
                            text = "添加备注…",
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

/** Small icon-only control that lives in the peek bar (pause/resume, stop). */
@Composable
private fun CompactControl(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    primary: Boolean = false
) {
    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint = if (primary)
            MaterialTheme.colorScheme.primary
        else
            MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(5.dp)
            .size(28.dp)
    )
}

@Composable
private fun IdleSection(
    summaries: List<TrackSummary>,
    tree: TrackTree?,
    timerActive: Boolean,
    onOpenDetail: (Long) -> Unit,
    onOpenStarMap: () -> Unit,
    onQuickStartBlank: () -> Unit,
    onQuickStart: (TrackSummary) -> Unit,
    onCreateTrack: (String) -> Unit
) {
    var showInput by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(16.dp))

        if (!timerActive) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                Text(
                    text = "还没有记录。",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "先开始计时，主题和备注可以边计时边补，或停止时再填。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(20.dp))

            QuickStartButton(onClick = onQuickStartBlank)
        }

        // Quick-start: tap a recent theme to begin timing right away. Hidden while
        // a timer runs so a tap can't silently discard the in-progress session.
        val quickItems = if (timerActive) emptyList() else summaries
            .filter { it.sessionCount > 0 }
            .sortedByDescending { it.lastSessionStartMs }
            .take(6)
        if (quickItems.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 10.dp)
            ) {
                Text(
                    text = "选主题开始",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                quickItems.forEach { item ->
                    QuickStartChip(label = item.name, onClick = { onQuickStart(item) })
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        InsetGroup {
            StarMapEntryRow(onClick = onOpenStarMap)
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 8.dp)
        ) {
            Text(
                text = "最近",
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

/** Prominent button that starts timing right away with no theme attached. */
@Composable
private fun QuickStartButton(onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.PlayCircleOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(26.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "快速开始",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Text(
                text = "直接计时，主题稍后再选",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
            )
        }
    }
}

/** One-tap chip that immediately starts a session for the given theme. */
@Composable
private fun QuickStartChip(label: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick)
            .padding(start = 10.dp, end = 14.dp, top = 8.dp, bottom = 8.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.PlayCircleOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 1
        )
    }
}

/** Entry into the full-screen Star map — the network view of track relationships. */
@Composable
private fun StarMapEntryRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "星图",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "主题关系网络",
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
                .background(trackColor(item.trackId))
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            val sub = when {
                rolledUpMs <= 0L && item.sessionCount == 0 -> "暂无记录"
                descendantCount > 0 -> {
                    "${TimeFormat.shortDuration(rolledUpMs)} · 含 $descendantCount 个子主题"
                }
                else -> {
                    "${TimeFormat.shortDuration(rolledUpMs)} · ${item.sessionCount} 条记录"
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
            text = "新建主题",
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
                            text = "主题名称",
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
            text = "取消",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onCancel)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "打开",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onSubmit)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        )
    }
}
