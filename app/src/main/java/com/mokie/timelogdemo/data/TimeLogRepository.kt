package com.mokie.timelogdemo.data

import android.util.Log
import androidx.room.withTransaction
import com.mokie.timelogdemo.tracking.TrackingSession
import com.mokie.timelogdemo.tracking.TrackingSession.TrackRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

/**
 * The only entry point for reads and writes from the UI. Owns the data rules that
 * span tables (allocation invariant, track deletion, keeping the running timer in
 * sync with track renames/deletes).
 *
 * Writes run in [appScope], so a screen leaving composition mid-write can't cancel
 * them halfway; callers still suspend until the write finishes.
 */
class TimeLogRepository(
    private val db: TimeLogDatabase,
    private val tracking: TrackingSession,
    private val appScope: CoroutineScope
) {
    private val trackDao = db.trackDao()
    private val sessionDao = db.sessionDao()

    // ----- Reads -----

    /** Shared, recomputed off the main thread whenever tracks, edges or allocations change. */
    val trackTree: StateFlow<TrackTree?> = combine(
        trackDao.observeTracks(),
        trackDao.observeInheritance(),
        sessionDao.observeAllocationsLight()
    ) { tracks, edges, allocs ->
        TrackTree(tracks = tracks, inheritance = edges, allocations = allocs)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(appScope, SharingStarted.WhileSubscribed(5_000), null)

    val tracks: Flow<List<TrackEntity>> = trackDao.observeTracks()
    val trackSummaries: Flow<List<TrackSummary>> = trackDao.observeTrackSummaries()
    val sessionRows: Flow<List<SessionAllocationRow>> = sessionDao.observeAllAllocations()

    fun contributionsFor(trackId: Long): Flow<List<SessionContribution>> =
        sessionDao.observeContributionsForTrack(trackId)

    suspend fun findTrack(id: Long): TrackEntity? = trackDao.findTrackById(id)

    data class SessionForEdit(
        val session: SessionEntity,
        val allocations: List<SessionAllocationOf>
    )

    suspend fun sessionForEdit(sessionId: Long): SessionForEdit? {
        val session = sessionDao.getSession(sessionId) ?: return null
        return SessionForEdit(session, sessionDao.getAllocationsForSession(sessionId))
    }

    /** Sessions whose span intersects [startMs, endMs), other than [excludeSessionId]. */
    suspend fun overlapping(startMs: Long, endMs: Long, excludeSessionId: Long = 0L): List<OverlapRow> =
        if (endMs <= startMs) emptyList()
        else sessionDao.findOverlapping(startMs, endMs, excludeSessionId)

    /** What deleting a track will do, for the confirmation dialog. */
    data class DeleteImpact(val sessionsRemoved: Int, val sessionsRebalanced: Int)

    suspend fun deleteImpact(trackId: Long): DeleteImpact {
        val bySession = sessionDao.getAllocationsOfSessionsTouching(trackId).groupBy { it.sessionId }
        val removed = bySession.values.count { rows -> rows.all { it.trackId == trackId } }
        return DeleteImpact(sessionsRemoved = removed, sessionsRebalanced = bySession.size - removed)
    }

    // ----- Tracks -----

    /** Find a track by name, creating it if needed. Null for a blank name. */
    suspend fun ensureTrack(name: String): TrackRef? = write {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@write null
        val id = trackDao.findTrackIdByName(trimmed) ?: run {
            trackDao.insertTrack(TrackEntity(name = trimmed, createdAtMs = System.currentTimeMillis()))
            trackDao.findTrackIdByName(trimmed)
        }
        id?.let { TrackRef(it, trimmed) }
    }

    enum class RenameResult { Ok, Blank, NameTaken }

    suspend fun renameTrack(trackId: Long, newName: String): RenameResult = write {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return@write RenameResult.Blank
        val holder = trackDao.findTrackIdByName(trimmed)
        if (holder != null && holder != trackId) return@write RenameResult.NameTaken
        trackDao.renameTrack(trackId, trimmed)
        onMain { tracking.onTrackRenamed(trackId, trimmed) }
        RenameResult.Ok
    }

    /**
     * Delete a track without breaking the allocation invariant:
     *  - sessions allocated only to this track are deleted;
     *  - shared sessions hand this track's share to their other tracks, proportionally.
     * Inheritance edges go via the foreign-key cascade; children survive (possibly as roots).
     */
    suspend fun deleteTrack(trackId: Long) = write {
        db.withTransaction {
            val bySession = sessionDao.getAllocationsOfSessionsTouching(trackId)
                .groupBy { it.sessionId }
            for ((sessionId, rows) in bySession) {
                val remaining = rows.filter { it.trackId != trackId }
                if (remaining.isEmpty()) {
                    sessionDao.deleteSession(sessionId)
                    continue
                }
                val session = sessionDao.getSession(sessionId) ?: continue
                val scaled = Allocation.scaleMsToTotal(
                    remaining.map { it.durationMs },
                    session.durationMs
                )
                sessionDao.updateSessionWithAllocations(
                    session,
                    remaining.mapIndexed { i, a -> a.copy(durationMs = scaled[i]) }
                )
            }
            trackDao.deleteTrack(trackId)
        }
        onMain { tracking.onTrackDeleted(trackId) }
    }

    suspend fun addParent(childId: Long, parentId: Long) = write {
        trackDao.insertInheritance(TrackInheritanceEntity(childTrackId = childId, parentTrackId = parentId))
    }

    suspend fun removeParent(childId: Long, parentId: Long) = write {
        trackDao.removeInheritance(childId, parentId)
    }

    // ----- Sessions -----

    suspend fun insertManualSession(
        startMs: Long,
        durationMs: Long,
        note: String,
        allocMs: Map<Long, Long>
    ): Boolean = write {
        if (durationMs <= 0L || allocMs.isEmpty()) return@write false
        runCatching {
            sessionDao.insertSessionWithAllocations(
                session = SessionEntity(
                    startMs = startMs,
                    endMs = startMs + durationMs,
                    note = note.trim(),
                    createdAtMs = System.currentTimeMillis()
                ),
                allocations = allocMs.toAllocations()
            )
        }.logFailure("insertManualSession").isSuccess
    }

    suspend fun updateSession(session: SessionEntity, allocMs: Map<Long, Long>): Boolean = write {
        runCatching {
            sessionDao.updateSessionWithAllocations(
                session.copy(note = session.note.trim()),
                allocMs.toAllocations()
            )
        }.logFailure("updateSession").isSuccess
    }

    suspend fun deleteSession(sessionId: Long) = write {
        sessionDao.deleteSession(sessionId)
    }

    /**
     * Persist the stopped timer, then clear it — and, for a switch, immediately start
     * timing [thenStart]. The running session is cleared only after the row is safely
     * written, so a failed write never loses the time.
     * Returns false if nothing was written (the timer is left running).
     */
    suspend fun commitTimer(
        snap: TrackingSession.StopSnapshot,
        allocMs: Map<Long, Long>,
        thenStart: TrackRef? = null
    ): Boolean =
        write {
            fun finish() {
                if (tracking.startMs != snap.startMs) return
                tracking.clear()
                thenStart?.let(tracking::start)
            }
            if (snap.effectiveDurationMs <= 0L) {
                onMain { finish() }
                return@write true
            }
            val live = trackDao.existingIds(allocMs.keys.toList()).toSet()
            if (live != allocMs.keys) return@write false
            val ok = runCatching {
                sessionDao.insertSessionWithAllocations(
                    session = SessionEntity(
                        startMs = snap.sessionStartMs,
                        endMs = snap.sessionEndMs,
                        pausedMs = snap.sessionPausedMs,
                        note = snap.note.trim(),
                        createdAtMs = System.currentTimeMillis()
                    ),
                    allocations = allocMs.toAllocations()
                )
            }.logFailure("commitTimer").isSuccess
            if (ok) onMain { finish() }
            ok
        }

    // ----- Plumbing -----

    private suspend fun <T> write(block: suspend () -> T): T =
        appScope.async(Dispatchers.IO) { block() }.await()

    private suspend fun onMain(block: () -> Unit) = withContext(Dispatchers.Main) { block() }

    private fun Map<Long, Long>.toAllocations() = map { (trackId, ms) ->
        SessionTrackAllocationEntity(sessionId = 0L, trackId = trackId, durationMs = ms)
    }

    private fun <T> Result<T>.logFailure(op: String): Result<T> =
        onFailure { Log.e("TimeLogRepository", "$op failed", it) }
}
