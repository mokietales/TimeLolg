package com.mokie.timelogdemo.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Joined row: one session with one of its track allocations. */
data class SessionAllocationRow(
    val sessionId: Long,
    val startMs: Long,
    val endMs: Long,
    val pausedMs: Long,
    /** Effective session duration (span minus paused time). */
    val sessionDurationMs: Long,
    val note: String,
    val trackId: Long,
    val trackName: String,
    val durationMs: Long
)

/** Allocation contribution to a specific track, used in TrackDetail screen. */
data class SessionContribution(
    val sessionId: Long,
    val startMs: Long,
    val endMs: Long,
    val sessionDurationMs: Long,
    val note: String,
    val contributedMs: Long,
    val totalAllocationsCount: Int
)

/** One allocation with its track name, for editing a single session. */
data class SessionAllocationOf(
    val trackId: Long,
    val trackName: String,
    val durationMs: Long
)

@Dao
interface SessionDao {
    @Insert
    suspend fun insertSession(session: SessionEntity): Long

    @Insert
    suspend fun insertAllocation(allocation: SessionTrackAllocationEntity)

    @Update
    suspend fun updateSession(session: SessionEntity)

    @Query("SELECT * FROM sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSession(sessionId: Long): SessionEntity?

    @Query("DELETE FROM session_track_allocation WHERE sessionId = :sessionId")
    suspend fun deleteAllocationsFor(sessionId: Long)

    /** Allocation rows cascade via the foreign key. */
    @Query("DELETE FROM sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: Long)

    @Transaction
    suspend fun insertSessionWithAllocations(
        session: SessionEntity,
        allocations: List<SessionTrackAllocationEntity>
    ): Long {
        requireBalanced(session, allocations)
        val sessionId = insertSession(session)
        allocations.forEach { a -> insertAllocation(a.copy(sessionId = sessionId)) }
        return sessionId
    }

    @Transaction
    suspend fun updateSessionWithAllocations(
        session: SessionEntity,
        allocations: List<SessionTrackAllocationEntity>
    ) {
        requireBalanced(session, allocations)
        updateSession(session)
        deleteAllocationsFor(session.id)
        allocations.forEach { a -> insertAllocation(a.copy(sessionId = session.id)) }
    }

    /** Every allocation row of every session that has at least one allocation on [trackId]. */
    @Query(
        """
        SELECT * FROM session_track_allocation
        WHERE sessionId IN (
            SELECT sessionId FROM session_track_allocation WHERE trackId = :trackId
        )
        """
    )
    suspend fun getAllocationsOfSessionsTouching(trackId: Long): List<SessionTrackAllocationEntity>

    @Query("SELECT trackId, durationMs FROM session_track_allocation")
    fun observeAllocationsLight(): Flow<List<TrackTree.SessionTrackAllocationOnly>>

    @Query(
        """
        SELECT s.id        AS sessionId,
               s.startMs   AS startMs,
               s.endMs     AS endMs,
               s.pausedMs  AS pausedMs,
               (s.endMs - s.startMs - s.pausedMs) AS sessionDurationMs,
               s.note      AS note,
               a.trackId   AS trackId,
               t.name      AS trackName,
               a.durationMs AS durationMs
        FROM sessions s
        JOIN session_track_allocation a ON a.sessionId = s.id
        JOIN tracks t ON t.id = a.trackId
        ORDER BY s.startMs DESC, a.trackId ASC
        """
    )
    fun observeAllAllocations(): Flow<List<SessionAllocationRow>>

    @Query(
        """
        SELECT a.sessionId    AS sessionId,
               s.startMs      AS startMs,
               s.endMs        AS endMs,
               (s.endMs - s.startMs - s.pausedMs) AS sessionDurationMs,
               s.note         AS note,
               a.durationMs   AS contributedMs,
               (SELECT COUNT(*) FROM session_track_allocation a2 WHERE a2.sessionId = s.id)
                              AS totalAllocationsCount
        FROM session_track_allocation a
        JOIN sessions s ON s.id = a.sessionId
        WHERE a.trackId = :trackId
        ORDER BY s.startMs DESC
        """
    )
    fun observeContributionsForTrack(trackId: Long): Flow<List<SessionContribution>>

    @Query(
        """
        SELECT t.id AS trackId, t.name AS trackName, a.durationMs AS durationMs
        FROM session_track_allocation a
        JOIN tracks t ON t.id = a.trackId
        WHERE a.sessionId = :sessionId
        ORDER BY t.name ASC
        """
    )
    suspend fun getAllocationsForSession(sessionId: Long): List<SessionAllocationOf>
}

private fun requireBalanced(
    session: SessionEntity,
    allocations: List<SessionTrackAllocationEntity>
) {
    require(allocations.isNotEmpty()) { "session must have at least one allocation" }
    val sum = allocations.sumOf { it.durationMs }
    require(sum == session.durationMs) {
        "allocations sum ($sum ms) must equal session duration (${session.durationMs} ms)"
    }
}
