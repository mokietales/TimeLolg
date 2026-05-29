package com.mokie.timelogdemo.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Joined row: one session with one of its track allocations. */
data class SessionAllocationRow(
    val sessionId: Long,
    val startMs: Long,
    val endMs: Long,
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
    val note: String,
    val contributedMs: Long,
    val totalAllocationsCount: Int
)

@Dao
interface SessionDao {
    @Insert
    suspend fun insertSession(session: SessionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllocation(allocation: SessionTrackAllocationEntity)

    @Update
    suspend fun updateSession(session: SessionEntity)

    @Query("UPDATE sessions SET note = :note WHERE id = :sessionId")
    suspend fun updateSessionNote(sessionId: Long, note: String)

    @Query("DELETE FROM session_track_allocation WHERE sessionId = :sessionId")
    suspend fun deleteAllocationsFor(sessionId: Long)

    @Query("DELETE FROM sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: Long)

    @Transaction
    suspend fun deleteSessionCascade(sessionId: Long) {
        deleteAllocationsFor(sessionId)
        deleteSession(sessionId)
    }

    @Transaction
    suspend fun insertSessionWithAllocations(
        session: SessionEntity,
        allocations: List<SessionTrackAllocationEntity>
    ): Long {
        require(allocations.isNotEmpty()) { "session must have at least one allocation" }
        val sum = allocations.sumOf { it.durationMs }
        val want = session.durationMs
        require(sum == want) {
            "allocations sum ($sum ms) must equal session duration ($want ms)"
        }
        val sessionId = insertSession(session)
        allocations.forEach { a ->
            insertAllocation(a.copy(sessionId = sessionId))
        }
        return sessionId
    }

    @Transaction
    suspend fun replaceAllocations(
        sessionId: Long,
        allocations: List<SessionTrackAllocationEntity>
    ) {
        require(allocations.isNotEmpty()) { "must have at least one allocation" }
        deleteAllocationsFor(sessionId)
        allocations.forEach { a ->
            insertAllocation(a.copy(sessionId = sessionId))
        }
    }

    @Query("SELECT trackId, durationMs FROM session_track_allocation")
    fun observeAllocationsLight(): Flow<List<TrackTree.SessionTrackAllocationOnly>>

    @Query(
        """
        SELECT s.id        AS sessionId,
               s.startMs   AS startMs,
               s.endMs     AS endMs,
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

    data class SessionAllocationOf(
        val trackId: Long,
        val trackName: String,
        val durationMs: Long
    )
}
