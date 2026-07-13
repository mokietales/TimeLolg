package com.mokie.timelogdemo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mokie.timelogdemo.ui.util.TimeFormat
import java.util.Calendar

private enum class PickerTarget { StartDate, StartTime, EndDate, EndTime }

/**
 * Start/end date-time editor rows with self-hosted date & time picker dialogs.
 * Shared by the manual-entry dialog and the session-edit dialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionTimeEditor(
    startMs: Long,
    endMs: Long,
    onStartChange: (Long) -> Unit,
    onEndChange: (Long) -> Unit
) {
    var target by remember { mutableStateOf<PickerTarget?>(null) }

    Column(modifier = Modifier.fillMaxWidth()) {
        DateTimeRow(
            label = "开始",
            dateLabel = TimeFormat.monthDayYear(startMs),
            timeLabel = TimeFormat.hhmm(startMs),
            onDateClick = { target = PickerTarget.StartDate },
            onTimeClick = { target = PickerTarget.StartTime }
        )
        Spacer(Modifier.height(4.dp))
        DateTimeRow(
            label = "结束",
            dateLabel = TimeFormat.monthDayYear(endMs),
            timeLabel = TimeFormat.hhmm(endMs),
            onDateClick = { target = PickerTarget.EndDate },
            onTimeClick = { target = PickerTarget.EndTime }
        )
    }

    when (target) {
        PickerTarget.StartDate -> DatePickerAlert(
            initialMs = startMs,
            onDismiss = { target = null },
            onConfirm = { dateMs ->
                onStartChange(TimeFormat.withDate(startMs, dateMs))
                target = null
            }
        )
        PickerTarget.EndDate -> DatePickerAlert(
            initialMs = endMs,
            onDismiss = { target = null },
            onConfirm = { dateMs ->
                onEndChange(TimeFormat.withDate(endMs, dateMs))
                target = null
            }
        )
        PickerTarget.StartTime -> TimePickerAlert(
            title = "开始时间",
            baseMs = startMs,
            onDismiss = { target = null },
            onConfirm = { hour, minute ->
                onStartChange(TimeFormat.withTime(startMs, hour, minute))
                target = null
            }
        )
        PickerTarget.EndTime -> TimePickerAlert(
            title = "结束时间",
            baseMs = endMs,
            onDismiss = { target = null },
            onConfirm = { hour, minute ->
                onEndChange(TimeFormat.withTime(endMs, hour, minute))
                target = null
            }
        )
        null -> Unit
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerAlert(
    initialMs: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initialMs)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let(onConfirm) ?: onDismiss()
            }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    ) { DatePicker(state = state) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerAlert(
    title: String,
    baseMs: Long,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit
) {
    val cal = Calendar.getInstance().apply { timeInMillis = baseMs }
    val state = rememberTimePickerState(
        initialHour = cal.get(Calendar.HOUR_OF_DAY),
        initialMinute = cal.get(Calendar.MINUTE),
        is24Hour = true
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        title = { Text(title) },
        text = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(state = state)
            }
        }
    )
}

@Composable
private fun DateTimeRow(
    label: String,
    dateLabel: String,
    timeLabel: String,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(44.dp)
        )
        PickerChip(text = dateLabel, onClick = onDateClick, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(6.dp))
        PickerChip(text = timeLabel, onClick = onTimeClick)
    }
}

@Composable
private fun PickerChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium.copy(
            fontFeatureSettings = TabularNumFeature,
            fontWeight = FontWeight.Medium
        ),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    )
}
