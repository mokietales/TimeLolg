package com.mokie.timelogdemo.tracking

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * In-memory + SharedPreferences-backed state for the currently-running tracking session.
 * One app-wide instance, owned by TimeLogApplication. Mutate on the main thread.
 *
 * One session at a time. While active, the session may be associated with zero or more
 * Tracks. When stopped, time is committed as a SessionEntity plus SessionTrackAllocation
 * rows whose durationMs sums to the session's effective duration.
 */
class TrackingSession(context: Context) {

    data class TrackRef(val id: Long, val name: String)

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var startMs by mutableStateOf(prefs.getLong(KEY_START, 0L).takeIf { it > 0L })
        private set

    var tracks by mutableStateOf(readTracks())
        private set

    var note by mutableStateOf(prefs.getString(KEY_NOTE, "") ?: "")
        private set

    var pausedAtMs by mutableStateOf(prefs.getLong(KEY_PAUSED_AT, 0L).takeIf { it > 0L })
        private set

    private var _pausedAccMs by mutableLongStateOf(prefs.getLong(KEY_PAUSED_ACC, 0L))

    private val _changes = MutableStateFlow(0L)

    /**
     * Ticks whenever start / tracks / pause state change (not on note edits).
     * Observed outside Compose, e.g. by the ongoing-notification controller.
     */
    val changes: StateFlow<Long> = _changes

    val isActive: Boolean get() = startMs != null
    val isPaused: Boolean get() = pausedAtMs != null
    val primaryTrack: TrackRef? get() = tracks.firstOrNull()

    /** Wall millis spent on this session, excluding paused windows. */
    fun elapsedMs(nowMs: Long): Long {
        val s = startMs ?: return 0L
        val pausedWindow = pausedAtMs?.let { (nowMs - it).coerceAtLeast(0L) } ?: 0L
        return ((nowMs - s) - _pausedAccMs - pausedWindow).coerceAtLeast(0L)
    }

    fun start(track: TrackRef) = begin(listOf(track))

    /**
     * Start timing immediately with no theme attached. The theme(s), note, etc.
     * can be filled in while running or when stopping. A theme must be chosen
     * before the session can be committed (enforced at stop).
     */
    fun startBlank() = begin(emptyList())

    private fun begin(initial: List<TrackRef>) {
        startMs = System.currentTimeMillis()
        tracks = initial
        pausedAtMs = null
        _pausedAccMs = 0L
        note = ""
        flush()
    }

    fun removeTrack(trackId: Long) {
        val next = tracks.filterNot { it.id == trackId }
        if (next.isEmpty()) return // the user can't remove the last one by hand
        tracks = next
        flush()
    }

    fun replaceTracks(newTracks: List<TrackRef>) {
        if (newTracks.isEmpty()) return
        tracks = newTracks.distinctBy { it.id }
        flush()
    }

    /** The track no longer exists; drop it even if that leaves the session theme-less. */
    fun onTrackDeleted(trackId: Long) {
        if (tracks.none { it.id == trackId }) return
        tracks = tracks.filterNot { it.id == trackId }
        flush()
    }

    fun onTrackRenamed(trackId: Long, newName: String) {
        if (tracks.none { it.id == trackId }) return
        tracks = tracks.map { if (it.id == trackId) it.copy(name = newName) else it }
        flush()
    }

    fun togglePause() {
        if (!isActive) return
        val now = System.currentTimeMillis()
        val pausedAt = pausedAtMs
        if (pausedAt == null) {
            pausedAtMs = now
        } else {
            _pausedAccMs += (now - pausedAt).coerceAtLeast(0L)
            pausedAtMs = null
        }
        flush()
    }

    fun updateNote(value: String) {
        note = value
        prefs.edit().putString(KEY_NOTE, value).apply()
    }

    /** Capture the session for committing, without clearing it. Null if not committable yet. */
    fun snapshotForStop(): StopSnapshot? {
        val s = startMs ?: return null
        if (tracks.isEmpty()) return null
        val end = System.currentTimeMillis()
        val totalPaused = _pausedAccMs + (pausedAtMs?.let { (end - it).coerceAtLeast(0L) } ?: 0L)
        return StopSnapshot(
            startMs = s,
            endMs = end,
            note = note,
            tracks = tracks,
            pausedAccumulatedMs = totalPaused
        )
    }

    fun clear() {
        startMs = null
        tracks = emptyList()
        note = ""
        pausedAtMs = null
        _pausedAccMs = 0L
        prefs.edit().clear().apply()
        _changes.value++
    }

    private fun flush() {
        prefs.edit().apply {
            startMs?.let { putLong(KEY_START, it) } ?: remove(KEY_START)
            putString(KEY_TRACKS, encodeTracks(tracks))
            putString(KEY_NOTE, note)
            pausedAtMs?.let { putLong(KEY_PAUSED_AT, it) } ?: remove(KEY_PAUSED_AT)
            putLong(KEY_PAUSED_ACC, _pausedAccMs)
        }.apply()
        _changes.value++
    }

    private fun readTracks(): List<TrackRef> = decodeTracks(prefs.getString(KEY_TRACKS, null))

    /**
     * A frozen view of the session at stop time. Bounds are floored to whole seconds
     * and the effective duration is snapped to whole seconds, so the per-track
     * allocation sliders (second precision) can sum exactly to the persisted duration.
     * The real end time is kept; paused time is recorded separately as [sessionPausedMs].
     */
    data class StopSnapshot(
        val startMs: Long,
        val endMs: Long,
        val note: String,
        val tracks: List<TrackRef>,
        val pausedAccumulatedMs: Long
    ) {
        val sessionStartMs: Long get() = floorSec(startMs)
        val sessionEndMs: Long get() = floorSec(endMs)

        val effectiveDurationMs: Long
            get() = floorSec(
                ((sessionEndMs - sessionStartMs) - pausedAccumulatedMs).coerceAtLeast(0L)
            )

        val sessionPausedMs: Long
            get() = (sessionEndMs - sessionStartMs) - effectiveDurationMs
    }

    companion object {
        private const val PREFS_NAME = "tracking_session"
        private const val KEY_START = "start_ms"
        private const val KEY_TRACKS = "tracks_json"
        private const val KEY_NOTE = "note"
        private const val KEY_PAUSED_AT = "paused_at"
        private const val KEY_PAUSED_ACC = "paused_acc"

        private fun floorSec(ms: Long): Long = (ms / 1000L) * 1000L

        private fun encodeTracks(list: List<TrackRef>): String {
            val arr = JSONArray()
            list.forEach { t ->
                arr.put(JSONObject().put("id", t.id).put("name", t.name))
            }
            return arr.toString()
        }

        private fun decodeTracks(json: String?): List<TrackRef> {
            if (json.isNullOrBlank()) return emptyList()
            return try {
                val arr = JSONArray(json)
                buildList(arr.length()) {
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        add(TrackRef(o.getLong("id"), o.getString("name")))
                    }
                }
            } catch (_: Throwable) {
                emptyList()
            }
        }
    }
}
