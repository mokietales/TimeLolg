package com.mokie.timelogdemo.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

/**
 * In-memory + SharedPreferences-backed state for the currently-running tracking session.
 *
 * One session at a time. While active, the session may be associated with one or more Tracks
 * (multi-Track selection). When stopped, time is committed as a SessionEntity plus
 * SessionTrackAllocation rows whose durationMs sums to the session's effective duration.
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

    var pausedAtMs by mutableStateOf(prefs.getLong(KEY_PAUSED_AT, 0L).takeIf { it > 0L })
        private set

    private var _pausedAccMs by mutableLongStateOf(prefs.getLong(KEY_PAUSED_ACC, 0L))
    val pausedAccumulatedMs: Long get() = _pausedAccMs

    val isActive: Boolean get() = startMs != null
    val isPaused: Boolean get() = pausedAtMs != null
    val primaryTrack: TrackRef? get() = tracks.firstOrNull()

    /** Wall millis spent on this session, excluding paused windows. */
    fun elapsedMs(nowMs: Long): Long {
        val s = startMs ?: return 0L
        val pausedWindow = pausedAtMs?.let { (nowMs - it).coerceAtLeast(0L) } ?: 0L
        return ((nowMs - s) - _pausedAccMs - pausedWindow).coerceAtLeast(0L)
    }

    fun start(track: TrackRef) {
        startMs = System.currentTimeMillis()
        tracks = listOf(track)
        pausedAtMs = null
        _pausedAccMs = 0L
        note = ""
        flush()
    }

    fun addTrack(track: TrackRef) {
        if (tracks.any { it.id == track.id }) return
        tracks = tracks + track
        flush()
    }

    fun removeTrack(trackId: Long) {
        val next = tracks.filterNot { it.id == trackId }
        if (next.isEmpty()) return // never empty while active
        tracks = next
        flush()
    }

    fun replaceTracks(newTracks: List<TrackRef>) {
        if (newTracks.isEmpty()) return
        tracks = newTracks.distinctBy { it.id }
        flush()
    }

    fun togglePause() {
        val now = System.currentTimeMillis()
        if (pausedAtMs == null) {
            pausedAtMs = now
        } else {
            _pausedAccMs += (now - pausedAtMs!!).coerceAtLeast(0L)
            pausedAtMs = null
        }
        flush()
    }

    fun updateNote(value: String) {
        note = value
        prefs.edit().putString(KEY_NOTE, value).apply()
    }

    /** Stop, but do NOT clear state yet — caller may need to show an allocation dialog first. */
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
    }

    private fun flush() {
        prefs.edit().apply {
            startMs?.let { putLong(KEY_START, it) } ?: remove(KEY_START)
            putString(KEY_TRACKS, encodeTracks(tracks))
            putString(KEY_NOTE, note)
            pausedAtMs?.let { putLong(KEY_PAUSED_AT, it) } ?: remove(KEY_PAUSED_AT)
            putLong(KEY_PAUSED_ACC, _pausedAccMs)
        }.apply()
    }

    private fun readTracks(): List<TrackRef> = decodeTracks(prefs.getString(KEY_TRACKS, null))

    data class StopSnapshot(
        val startMs: Long,
        val endMs: Long,
        val note: String,
        val tracks: List<TrackRef>,
        val pausedAccumulatedMs: Long
    ) {
        /**
         * Effective duration to be apportioned across tracks (paused time subtracted),
         * snapped to whole seconds. Snapping is required so that the per-track allocation
         * sliders — which operate at second precision — can produce a sum exactly equal
         * to the persisted session duration. The DAO enforces this invariant strictly.
         */
        val effectiveDurationMs: Long
            get() {
                val raw = ((endMs - startMs) - pausedAccumulatedMs).coerceAtLeast(0L)
                return (raw / 1000L) * 1000L
            }
    }

    companion object {
        private const val PREFS_NAME = "tracking_session"
        private const val KEY_START = "start_ms"
        private const val KEY_TRACKS = "tracks_json"
        private const val KEY_NOTE = "note"
        private const val KEY_PAUSED_AT = "paused_at"
        private const val KEY_PAUSED_ACC = "paused_acc"

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
