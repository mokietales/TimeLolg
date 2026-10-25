package com.mokie.timelogdemo.tracking

import android.content.Context
import com.mokie.timelogdemo.data.TimeLogRepository
import com.mokie.timelogdemo.tracking.TrackingSession.TrackRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Re-renders every home-screen widget whenever the running timer or the theme list
 * changes (new theme, rename, delete, a session committed reordering "recent").
 */
class WidgetUpdater(
    private val context: Context,
    private val tracking: TrackingSession,
    private val repository: TimeLogRepository
) {
    @Volatile
    private var recent: List<TrackRef> = emptyList()

    fun start(scope: CoroutineScope) {
        scope.launch {
            combine(tracking.changes, repository.trackSummaries) { _, summaries -> summaries }
                .collect { summaries ->
                    recent = summaries.map { TrackRef(it.trackId, it.name) }
                    refresh()
                }
        }
    }

    fun refresh() {
        QuickWidgetProvider.updateAll(context, tracking)
        TimerWidgetProvider.updateAll(context, tracking, shownTracks(recent, tracking.tracks))
    }

    companion object {
        /**
         * The most recent themes, in a stable order so taps stay where the thumb
         * expects them. A running theme that isn't among them takes the last slot,
         * so it's always visible (and highlighted).
         */
        fun shownTracks(recent: List<TrackRef>, running: List<TrackRef>): List<TrackRef> {
            val slots = TimerWidgetProvider.SLOTS
            val runningIds = running.map { it.id }.toSet()
            val shown = recent.take(slots).toMutableList()
            for (r in running) {
                if (shown.any { it.id == r.id }) continue
                if (shown.size < slots) {
                    shown.add(r)
                    continue
                }
                val replaceAt = shown.indexOfLast { it.id !in runningIds }
                if (replaceAt >= 0) shown[replaceAt] = r
            }
            return shown
        }
    }
}
