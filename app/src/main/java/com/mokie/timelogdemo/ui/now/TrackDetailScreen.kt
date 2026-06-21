package com.mokie.timelogdemo.ui.now

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Schedule
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
import com.mokie.timelogdemo.data.SessionContribution
import com.mokie.timelogdemo.data.SessionDao
import com.mokie.timelogdemo.data.TrackDao
import com.mokie.timelogdemo.data.TrackEntity
import com.mokie.timelogdemo.data.TrackInheritanceEntity
import com.mokie.timelogdemo.data.TrackTree
import com.mokie.timelogdemo.data.observeTrackTree
import com.mokie.timelogdemo.ui.TrackingSession
import com.mokie.timelogdemo.ui.components.InsetGroup
import com.mokie.timelogdemo.ui.components.ManualSessionDialog
import com.mokie.timelogdemo.ui.components.RowDivider
import com.mokie.timelogdemo.ui.components.SectionHeader
import com.mokie.timelogdemo.ui.components.SessionEditDialog
import com.mokie.timelogdemo.ui.components.TabularNumFeature
import com.mokie.timelogdemo.ui.components.TrackPickerDialog
import com.mokie.timelogdemo.ui.util.TimeFormat
import kotlinx.coroutines.launch

@Composable
fun TrackDetailScreen(
    trackId: Long,
    session: TrackingSession,
    trackDao: TrackDao,
    sessionDao: SessionDao,
    onBack: () -> Unit,
    onOpenTrackDetail: (Long) -> Unit
) {
    val scope = rememberCoroutineScope()
    var track by remember(trackId) { mutableStateOf<TrackEntity?>(null) }

    LaunchedEffect(trackId) {
        track = trackDao.findTrackById(trackId)
    }

    val contributions by sessionDao
        .observeContributionsForTrack(trackId)
        .collectAsState(initial = emptyList())

    val tree by remember(trackDao, sessionDao) {
        observeTrackTree(trackDao, sessionDao)
    }.collectAsState(initial = null)

    var renameVisible by remember { mutableStateOf(false) }
    var deleteConfirmVisible by remember { mutableStateOf(false) }
    var editingSessionId by remember { mutableStateOf<Long?>(null) }
    var addParentVisible by remember { mutableStateOf(false) }
    var addChildVisible by remember { mutableStateOf(false) }
    var manualEntryVisible by remember { mutableStateOf(false) }

    val selfMs = remember(contributions) { contributions.sumOf { it.contributedMs } }
    val rolledUpMs = tree?.totalMs?.get(trackId) ?: selfMs
    val descendantCount = tree?.descendantCount?.get(trackId) ?: 0
    val sessionCount = contributions.size
    val parentIds = tree?.parentsOf?.get(trackId).orEmpty()
    val childIds = tree?.childrenOf?.get(trackId).orEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        DetailTopBar(
            title = track?.name ?: "",
            onBack = onBack,
            onRename = { renameVisible = true },
            onDelete = { deleteConfirmVisible = true }
        )

        Spacer(Modifier.height(8.dp))

        Hero(
            totalMs = rolledUpMs,
            selfMs = selfMs,
            sessionCount = sessionCount,
            descendantCount = descendantCount
        )

        Spacer(Modifier.height(12.dp))

        StartButton(
            enabled = !session.isActive && track != null,
            onClick = {
                track?.let { t ->
                    session.start(TrackingSession.TrackRef(t.id, t.name))
                    onBack()
                }
            },
            disabledReason = if (session.isActive) "已有进行中的记录" else null
        )

        Spacer(Modifier.height(8.dp))

        LogTimeButton(onClick = { manualEntryVisible = true })

        Spacer(Modifier.height(28.dp))

        // ----- Hierarchy section -----
        HierarchySection(
            tree = tree,
            parentIds = parentIds,
            childIds = childIds,
            onOpenTrack = onOpenTrackDetail,
            onAddParent = { addParentVisible = true },
            onAddChild = { addChildVisible = true },
            onRemoveParent = { parentId ->
                scope.launch { trackDao.removeInheritance(trackId, parentId) }
            },
            onRemoveChild = { childId ->
                scope.launch { trackDao.removeInheritance(childId, trackId) }
            }
        )

        Spacer(Modifier.height(28.dp))

        SectionHeader(title = "记录")

        if (contributions.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Text(
                    text = "暂无记录。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val grouped = groupByDay(contributions)
            grouped.forEach { (dayLabel, dayTotal, rows) ->
                DayHeader(dayLabel = dayLabel, dayTotalMs = dayTotal)
                InsetGroup {
                    rows.forEachIndexed { index, row ->
                        ContributionRow(
                            row = row,
                            onClickRow = { editingSessionId = row.sessionId }
                        )
                        if (index != rows.lastIndex) RowDivider()
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }

        Spacer(Modifier.height(32.dp))
    }

    if (renameVisible && track != null) {
        RenameDialog(
            currentName = track!!.name,
            onDismiss = { renameVisible = false },
            onConfirm = { newName ->
                scope.launch {
                    trackDao.renameTrack(trackId, newName.trim())
                    track = trackDao.findTrackById(trackId)
                }
                renameVisible = false
            }
        )
    }

    if (deleteConfirmVisible) {
        AlertDialog(
            onDismissRequest = { deleteConfirmVisible = false },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        trackDao.deleteTrackCascade(trackId)
                        onBack()
                    }
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmVisible = false }) { Text("取消") }
            },
            title = { Text("删除此主题？") },
            text = {
                Text(
                    "子主题会保留，但与此主题的关联会被移除。" +
                        "此主题上的时间分配会被删除，其他主题上的分配不受影响。",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        )
    }

    if (addParentVisible) {
        ParentChildPickerDialog(
            title = "添加父主题",
            tree = tree,
            // candidates that can validly become a parent of this node
            filter = { candidate ->
                candidate.id != trackId &&
                    candidate.id !in parentIds &&
                    tree?.canAddParent(child = trackId, parent = candidate.id) == true
            },
            onDismiss = { addParentVisible = false },
            onConfirm = { newParents ->
                scope.launch {
                    newParents.forEach { p ->
                        if (tree?.canAddParent(child = trackId, parent = p) == true) {
                            trackDao.insertInheritance(
                                TrackInheritanceEntity(
                                    childTrackId = trackId,
                                    parentTrackId = p
                                )
                            )
                        }
                    }
                    addParentVisible = false
                }
            }
        )
    }

    if (addChildVisible) {
        ParentChildPickerDialog(
            title = "添加子主题",
            tree = tree,
            filter = { candidate ->
                candidate.id != trackId &&
                    candidate.id !in childIds &&
                    tree?.canAddParent(child = candidate.id, parent = trackId) == true
            },
            onDismiss = { addChildVisible = false },
            onConfirm = { newChildren ->
                scope.launch {
                    newChildren.forEach { c ->
                        if (tree?.canAddParent(child = c, parent = trackId) == true) {
                            trackDao.insertInheritance(
                                TrackInheritanceEntity(
                                    childTrackId = c,
                                    parentTrackId = trackId
                                )
                            )
                        }
                    }
                    addChildVisible = false
                }
            }
        )
    }

    editingSessionId?.let { sid ->
        SessionEditDialog(
            sessionId = sid,
            sessionDao = sessionDao,
            trackDao = trackDao,
            onDismiss = { editingSessionId = null }
        )
    }

    if (manualEntryVisible) {
        ManualSessionDialog(
            sessionDao = sessionDao,
            trackDao = trackDao,
            initialTrackId = trackId,
            onDismiss = { manualEntryVisible = false }
        )
    }
}

@Composable
private fun HierarchySection(
    tree: TrackTree?,
    parentIds: List<Long>,
    childIds: List<Long>,
    onOpenTrack: (Long) -> Unit,
    onAddParent: () -> Unit,
    onAddChild: () -> Unit,
    onRemoveParent: (Long) -> Unit,
    onRemoveChild: (Long) -> Unit
) {
    val byId = tree?.byId.orEmpty()

    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(title = "父主题")
        InsetGroup {
            if (parentIds.isEmpty()) {
                EmptyHierarchyRow("无父主题 — 这是根主题。")
            } else {
                parentIds.forEachIndexed { i, pid ->
                    val name = byId[pid]?.name ?: "—"
                    val total = tree?.totalMs?.get(pid) ?: 0L
                    HierarchyLinkRow(
                        name = name,
                        totalMs = total,
                        onOpen = { onOpenTrack(pid) },
                        onRemove = { onRemoveParent(pid) }
                    )
                    if (i != parentIds.lastIndex) RowDivider()
                }
                RowDivider()
            }
            HierarchyAddRow(label = "添加父主题…", onClick = onAddParent)
        }

        Spacer(Modifier.height(20.dp))

        SectionHeader(title = "子主题")
        InsetGroup {
            if (childIds.isEmpty()) {
                EmptyHierarchyRow("暂无关联的子主题。")
            } else {
                childIds.forEachIndexed { i, cid ->
                    val name = byId[cid]?.name ?: "—"
                    val total = tree?.totalMs?.get(cid) ?: 0L
                    HierarchyLinkRow(
                        name = name,
                        totalMs = total,
                        onOpen = { onOpenTrack(cid) },
                        onRemove = { onRemoveChild(cid) }
                    )
                    if (i != childIds.lastIndex) RowDivider()
                }
                RowDivider()
            }
            HierarchyAddRow(label = "添加子主题…", onClick = onAddChild)
        }
    }
}

@Composable
private fun EmptyHierarchyRow(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HierarchyLinkRow(
    name: String,
    totalMs: Long,
    onOpen: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f))
        )
        Spacer(Modifier.width(14.dp))
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onOpen)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (totalMs > 0L) TimeFormat.shortDuration(totalMs) else "—",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFeatureSettings = TabularNumFeature
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = "取消关联",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onRemove)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun HierarchyAddRow(label: String, onClick: () -> Unit) {
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
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun ParentChildPickerDialog(
    title: String,
    tree: TrackTree?,
    filter: (TrackEntity) -> Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Set<Long>) -> Unit
) {
    val available = tree?.tracks?.filter(filter).orEmpty()
    TrackPickerDialog(
        title = title,
        available = available,
        initiallySelected = emptySet(),
        onDismiss = onDismiss,
        onConfirm = { refs -> onConfirm(refs.map { it.id }.toSet()) },
        onCreate = { _, _ ->
            // Reuse existing tracks only.
        }
    )
}

private fun groupByDay(
    rows: List<SessionContribution>
): List<Triple<String, Long, List<SessionContribution>>> {
    val groups = linkedMapOf<Long, MutableList<SessionContribution>>()
    rows.forEach { row ->
        val dayStart = TimeFormat.startOfDayMs(row.startMs)
        groups.getOrPut(dayStart) { mutableListOf() }.add(row)
    }
    return groups.entries.map { (dayMs, list) ->
        Triple(
            TimeFormat.dayHeader(dayMs),
            list.sumOf { it.contributedMs },
            list
        )
    }
}

@Composable
private fun DetailTopBar(
    title: String,
    onBack: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.ArrowBack,
            contentDescription = "返回",
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onBack)
                .padding(8.dp)
                .size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.Outlined.Edit,
            contentDescription = "重命名",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onRename)
                .padding(8.dp)
                .size(18.dp)
        )
        Text(
            text = "删除",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onDelete)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun Hero(
    totalMs: Long,
    selfMs: Long,
    sessionCount: Int,
    descendantCount: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        Text(
            text = if (totalMs > 0L) TimeFormat.shortDuration(totalMs) else "0:00",
            style = MaterialTheme.typography.displayLarge.copy(
                fontFeatureSettings = TabularNumFeature
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(6.dp))
        val sessionPart = "${sessionCount} 条记录"
        if (descendantCount > 0) {
            Text(
                text = "含 $descendantCount 个子主题",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "自身 ${TimeFormat.shortDuration(selfMs)} · $sessionPart",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFeatureSettings = TabularNumFeature
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                text = sessionPart,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LogTimeButton(onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.Schedule,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "补录时间",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun StartButton(
    enabled: Boolean,
    disabledReason: String?,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (enabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceContainer
                )
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.PlayArrow,
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "开始计时",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = if (enabled) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (disabledReason != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = disabledReason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DayHeader(dayLabel: String, dayTotalMs: Long) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = dayLabel.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = TimeFormat.shortDuration(dayTotalMs),
            style = MaterialTheme.typography.labelMedium.copy(
                fontFeatureSettings = TabularNumFeature,
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ContributionRow(
    row: SessionContribution,
    onClickRow: () -> Unit
) {
    val sessionTotalMs = (row.endMs - row.startMs).coerceAtLeast(0L)
    val sharedSplit = row.totalAllocationsCount > 1

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClickRow)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${TimeFormat.hhmm(row.startMs)} – ${TimeFormat.hhmm(row.endMs)}",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontFeatureSettings = TabularNumFeature
                ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = TimeFormat.shortDuration(row.contributedMs),
                style = MaterialTheme.typography.titleSmall.copy(
                    fontFeatureSettings = TabularNumFeature,
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        if (sharedSplit) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = "共 ${TimeFormat.shortDuration(sessionTotalMs)} · 分配到 " +
                    "${row.totalAllocationsCount} 个主题",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(4.dp))
        if (row.note.isNotBlank()) {
            Text(
                text = row.note,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                text = "添加备注…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun RenameDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.trim().isNotEmpty()
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        title = { Text("重命名主题") },
        text = {
            BasicTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                textStyle = LocalTextStyle.current.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = MaterialTheme.typography.bodyLarge.fontSize
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    )
}
