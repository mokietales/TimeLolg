package com.mokie.timelogdemo.ui.mindmap

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mokie.timelogdemo.data.SessionDao
import com.mokie.timelogdemo.data.TrackDao
import com.mokie.timelogdemo.data.TrackEntity
import com.mokie.timelogdemo.data.TrackTree
import com.mokie.timelogdemo.data.observeTrackTree
import com.mokie.timelogdemo.ui.components.EmptyState
import com.mokie.timelogdemo.ui.components.TabularNumFeature
import com.mokie.timelogdemo.ui.components.TrackPickerDialog
import com.mokie.timelogdemo.ui.theme.trackColor
import com.mokie.timelogdemo.ui.util.TimeFormat
import kotlinx.coroutines.launch

/**
 * Indented outliner over the Track DAG, designed to be embedded as a section
 * (e.g. the Review tab's "Mind map" mode). A node with multiple parents appears
 * under each — annotated with an "↗ N" badge so the user knows it's shared.
 *
 * Each row's right-hand number is the rolled-up total (self + deduped
 * descendants), so glancing at the tree gives an answer to "how much total
 * time does this branch hold".
 */
@Composable
fun MindMapContent(
    tree: TrackTree?,
    trackDao: TrackDao,
    onOpenTrackDetail: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var collapsed by rememberSaveable { mutableStateOf(emptySet<Long>()) }
    var addingChildOf by remember { mutableStateOf<Long?>(null) }
    val scope = rememberCoroutineScope()
    val current = tree

    if (current == null) {
        Spacer(Modifier.height(48.dp))
        return
    }

    if (current.tracks.isEmpty()) {
        EmptyState(
            title = "还没有主题",
            subtitle = "在「现在」页创建一个主题即可开始。"
        )
        return
    }

    val rows = remember(current, collapsed) {
        buildRows(tree = current, collapsed = collapsed)
    }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        rows.forEach { row ->
            item(key = "${row.trackId}-${row.path}") {
                OutlineRow(
                    row = row,
                    tree = current,
                    isCollapsed = row.trackId in collapsed,
                    onToggle = {
                        collapsed = if (row.trackId in collapsed) {
                            collapsed - row.trackId
                        } else {
                            collapsed + row.trackId
                        }
                    },
                    onClickName = { onOpenTrackDetail(row.trackId) },
                    onAddChild = { addingChildOf = row.trackId }
                )
            }
        }
        item { Spacer(Modifier.height(48.dp)) }
    }

    addingChildOf?.let { parentId ->
        AddChildDialog(
            parentId = parentId,
            tree = current,
            onDismiss = { addingChildOf = null },
            onConfirm = { childIds ->
                scope.launch {
                    childIds.forEach { childId ->
                        if (current.canAddParent(childId, parentId)) {
                            trackDao.insertInheritance(
                                com.mokie.timelogdemo.data.TrackInheritanceEntity(
                                    childTrackId = childId,
                                    parentTrackId = parentId
                                )
                            )
                        }
                    }
                    addingChildOf = null
                }
            }
        )
    }
}

private data class OutlineRowSpec(
    val trackId: Long,
    val depth: Int,
    val hasChildren: Boolean,
    val sharedParentCount: Int,
    /** Immediate parent in this position, or null for a root row. */
    val parentId: Long?,
    /** Stable path key like "12.7.3" so the same trackId can appear at multiple positions. */
    val path: String
)

private fun buildRows(tree: TrackTree, collapsed: Set<Long>): List<OutlineRowSpec> {
    val out = mutableListOf<OutlineRowSpec>()
    fun walk(id: Long, depth: Int, parentId: Long?, path: String, branchVisited: Set<Long>) {
        val safeBranch = branchVisited + id
        val children = tree.childrenOf[id].orEmpty()
        val parents = tree.parentsOf[id].orEmpty()
        out.add(
            OutlineRowSpec(
                trackId = id,
                depth = depth,
                hasChildren = children.isNotEmpty(),
                sharedParentCount = parents.size,
                parentId = parentId,
                path = path
            )
        )
        if (id in collapsed) return
        children.forEach { c ->
            if (c in safeBranch) return@forEach // defensive cycle guard
            walk(c, depth + 1, id, "$path.$c", safeBranch)
        }
    }
    tree.roots.forEach { r ->
        walk(r.id, 0, null, r.id.toString(), emptySet())
    }
    return out
}

@Composable
private fun OutlineRow(
    row: OutlineRowSpec,
    tree: TrackTree,
    isCollapsed: Boolean,
    onToggle: () -> Unit,
    onClickName: () -> Unit,
    onAddChild: () -> Unit
) {
    val track = tree.byId[row.trackId] ?: return
    val totalMs = tree.totalMs[row.trackId] ?: 0L
    val descCount = tree.descendantCount[row.trackId] ?: 0
    val indent = (row.depth * 18).dp

    // Share of the immediate parent branch this child accounts for.
    val parentTotalMs = row.parentId?.let { tree.totalMs[it] ?: 0L } ?: 0L
    val sharePercent: Int? = if (row.parentId != null && parentTotalMs > 0L && totalMs > 0L) {
        ((totalMs.toDouble() / parentTotalMs.toDouble()) * 100.0).toInt().coerceIn(0, 100)
    } else {
        null
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width(indent))

        // Disclosure (or placeholder for leaf alignment)
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .let { if (row.hasChildren) it.clickable(onClick = onToggle) else it },
            contentAlignment = Alignment.Center
        ) {
            if (row.hasChildren) {
                val rotation = if (isCollapsed) -90f else 0f
                Icon(
                    imageVector = Icons.Outlined.ExpandMore,
                    contentDescription = if (isCollapsed) "展开" else "收起",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(18.dp)
                        .rotate(rotation)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(trackColor(row.trackId))
                )
            }
        }

        Spacer(Modifier.width(4.dp))

        // Name + total
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClickName)
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = track.name,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = if (row.hasChildren) FontWeight.SemiBold else FontWeight.Normal
                ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            if (row.sharedParentCount >= 2) {
                Text(
                    text = "↗${row.sharedParentCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier
                        .padding(end = 8.dp)
                )
            }
            if (sharePercent != null) {
                Text(
                    text = "$sharePercent%",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFeatureSettings = TabularNumFeature
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
            Text(
                text = if (totalMs > 0L) TimeFormat.shortDuration(totalMs) else "—",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFeatureSettings = TabularNumFeature
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Add-child affordance
        Icon(
            imageVector = Icons.Outlined.Add,
            contentDescription = "添加子主题",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onAddChild)
                .padding(6.dp)
                .size(16.dp)
        )
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier
                .padding(start = 2.dp)
                .size(16.dp)
        )
    }

    if (descCount > 0 && isCollapsed) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp + indent + 32.dp, bottom = 4.dp)
        ) {
            Text(
                text = "已收起 $descCount 个子主题",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun AddChildDialog(
    parentId: Long,
    tree: TrackTree?,
    onDismiss: () -> Unit,
    onConfirm: (Set<Long>) -> Unit
) {
    if (tree == null) {
        onDismiss()
        return
    }
    // Candidates: any track not in {self ∪ descendants ∪ existing children}.
    // Adding a track that is currently an ancestor of `parentId` would create a
    // cycle — TrackTree.canAddParent flips the args (we want parentId -> child,
    // i.e. child gets a new parent).
    val existingChildren = tree.childrenOf[parentId].orEmpty().toSet()
    val candidates = tree.tracks.filter { candidate ->
        candidate.id != parentId &&
            candidate.id !in existingChildren &&
            tree.canAddParent(child = candidate.id, parent = parentId)
    }

    TrackPickerDialog(
        title = "在「${tree.byId[parentId]?.name ?: ""}」下添加子主题",
        available = candidates,
        initiallySelected = emptySet(),
        onDismiss = onDismiss,
        onConfirm = { refs -> onConfirm(refs.map { it.id }.toSet()) },
        onCreate = { _, _ ->
            // Adding a brand-new track and immediately parenting it requires
            // creating it via TrackDao first. For now we keep this dialog
            // strictly for re-parenting existing tracks.
        }
    )
}
