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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mokie.timelogdemo.data.TrackEntity
import com.mokie.timelogdemo.ui.TrackingSession

/**
 * Multi-select dialog for picking which Tracks a session is associated with.
 * Supports inline new-track creation.
 */
@Composable
fun TrackPickerDialog(
    title: String,
    available: List<TrackEntity>,
    initiallySelected: Set<Long>,
    onDismiss: () -> Unit,
    onConfirm: (selected: List<TrackingSession.TrackRef>) -> Unit,
    onCreate: (name: String, onCreated: (Long, String) -> Unit) -> Unit
) {
    var selected by remember { mutableStateOf(initiallySelected) }
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    // Tracks created inline during this dialog's lifetime. Kept separately so
    // they stay pickable even when the caller's `available` list is a one-shot
    // snapshot that doesn't refresh after the insert.
    var created by remember { mutableStateOf<List<TrackingSession.TrackRef>>(emptyList()) }
    val nameById = remember(available, created) {
        (available.map { it.id to it.name } + created.map { it.id to it.name }).toMap()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val refs = selected.mapNotNull { id ->
                        nameById[id]?.let { TrackingSession.TrackRef(id, it) }
                    }
                    if (refs.isNotEmpty()) onConfirm(refs)
                },
                enabled = selected.isNotEmpty()
            ) { Text("完成") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (available.isEmpty() && !creating) {
                    Text(
                        text = "还没有主题。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    available.forEach { track ->
                        val isSel = track.id in selected
                        PickRow(
                            name = track.name,
                            selected = isSel,
                            onClick = {
                                selected = if (isSel) selected - track.id else selected + track.id
                            }
                        )
                    }
                }
                created
                    .filter { c -> available.none { it.id == c.id } }
                    .forEach { c ->
                        val isSel = c.id in selected
                        PickRow(
                            name = c.name,
                            selected = isSel,
                            onClick = {
                                selected = if (isSel) selected - c.id else selected + c.id
                            }
                        )
                    }

                Spacer(Modifier.height(8.dp))

                if (creating) {
                    NewTrackInline(
                        value = newName,
                        onValueChange = { newName = it },
                        onSubmit = {
                            val n = newName.trim()
                            if (n.isNotEmpty()) {
                                onCreate(n) { newId, createdName ->
                                    created = created + TrackingSession.TrackRef(newId, createdName)
                                    selected = selected + newId
                                    newName = ""
                                    creating = false
                                }
                            }
                        },
                        onCancel = {
                            newName = ""
                            creating = false
                        }
                    )
                } else {
                    Text(
                        text = "+ 新建主题",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { creating = true }
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                    )
                }
            }
        }
    )
}

@Composable
private fun PickRow(name: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline
                )
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun NewTrackInline(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
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
                            "主题名称",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    inner()
                }
            },
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 6.dp)
        )
        Text(
            "添加",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onSubmit)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            "取消",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onCancel)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        )
    }
}
