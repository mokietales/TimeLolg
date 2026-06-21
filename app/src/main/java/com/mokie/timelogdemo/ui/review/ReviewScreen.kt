package com.mokie.timelogdemo.ui.review

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.mokie.timelogdemo.data.SessionDao
import com.mokie.timelogdemo.data.TrackDao
import com.mokie.timelogdemo.data.observeTrackTree
import com.mokie.timelogdemo.ui.mindmap.MindMapContent

/**
 * The Review tab is now the mind-map outliner over the Track DAG. The old
 * time-breakdown view was removed; per-track time is still visible inline on
 * each node and on the track detail screen.
 */
@Composable
fun ReviewScreen(
    sessionDao: SessionDao,
    trackDao: TrackDao,
    onOpenTrackDetail: (Long) -> Unit
) {
    val tree by remember(trackDao, sessionDao) {
        observeTrackTree(trackDao, sessionDao)
    }.collectAsState(initial = null)

    MindMapContent(
        tree = tree,
        trackDao = trackDao,
        onOpenTrackDetail = onOpenTrackDetail,
        modifier = Modifier.fillMaxSize()
    )
}
