package com.mokie.timelogdemo.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.ListAlt
import androidx.activity.compose.BackHandler
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mokie.timelogdemo.data.SessionDao
import com.mokie.timelogdemo.data.TrackDao
import com.mokie.timelogdemo.ui.now.NowScreen
import com.mokie.timelogdemo.ui.now.TrackDetailScreen
import com.mokie.timelogdemo.ui.review.ReviewScreen
import com.mokie.timelogdemo.ui.starmap.StarMapScreen
import com.mokie.timelogdemo.ui.sessions.SessionsScreen

private enum class Tab(val title: String, val icon: ImageVector) {
    Now("现在", Icons.Outlined.AccessTime),
    Review("导图", Icons.Outlined.Insights),
    Sessions("记录", Icons.Outlined.ListAlt)
}

/**
 * Tiny, hand-rolled navigation stack stored as a list of opaque string tokens
 * so it survives `rememberSaveable` without a custom Saver. Tokens:
 *   - "mindmap"
 *   - "detail:<trackId>"
 *
 * The current tab is the implicit "root" at the bottom of the stack. Switching
 * tabs always clears the stack.
 */
@Composable
fun TimeLogApp(
    trackDao: TrackDao,
    sessionDao: SessionDao
) {
    val context = LocalContext.current
    val session = remember { TrackingSession(context) }

    var current by rememberSaveable { mutableStateOf(Tab.Now) }
    var stack by rememberSaveable { mutableStateOf(emptyList<String>()) }

    fun pushDetail(id: Long) {
        stack = stack + "detail:$id"
    }

    fun pushStarMap() {
        if (stack.lastOrNull() != "starmap") stack = stack + "starmap"
    }

    fun pop() {
        if (stack.isNotEmpty()) stack = stack.dropLast(1)
    }

    // Wire the system back key / gesture to our internal stack. Without this,
    // only the in-screen ← buttons work; the OS back button finishes the Activity.
    BackHandler(enabled = stack.isNotEmpty()) {
        pop()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        bottomBar = {
            BottomTabs(
                current = current,
                onSelect = {
                    stack = emptyList()
                    current = it
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val top = stack.lastOrNull()
            when {
                top == null -> when (current) {
                    Tab.Now -> NowScreen(
                        session = session,
                        trackDao = trackDao,
                        sessionDao = sessionDao,
                        onOpenTrackDetail = { id -> pushDetail(id) },
                        onOpenStarMap = { pushStarMap() }
                    )
                    Tab.Review -> ReviewScreen(
                        sessionDao = sessionDao,
                        trackDao = trackDao,
                        onOpenTrackDetail = { id -> pushDetail(id) }
                    )
                    Tab.Sessions -> SessionsScreen(
                        sessionDao = sessionDao,
                        trackDao = trackDao
                    )
                }
                top == "starmap" -> StarMapScreen(
                    session = session,
                    trackDao = trackDao,
                    sessionDao = sessionDao,
                    onOpenTrackDetail = { id -> pushDetail(id) },
                    onBack = { pop() }
                )
                top.startsWith("detail:") -> {
                    val id = top.removePrefix("detail:").toLong()
                    TrackDetailScreen(
                        trackId = id,
                        session = session,
                        trackDao = trackDao,
                        sessionDao = sessionDao,
                        onBack = { pop() },
                        onOpenTrackDetail = { newId -> pushDetail(newId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomTabs(current: Tab, onSelect: (Tab) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(MaterialTheme.colorScheme.outline)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Tab.values().forEach { tab ->
                TabButton(
                    tab = tab,
                    selected = tab == current,
                    onClick = { onSelect(tab) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun TabButton(
    tab: Tab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val color = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(
                    if (selected) MaterialTheme.colorScheme.primaryContainer
                    else androidx.compose.ui.graphics.Color.Transparent
                )
                .padding(horizontal = 18.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = tab.icon,
                contentDescription = tab.title,
                tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else color,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = tab.title,
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}
