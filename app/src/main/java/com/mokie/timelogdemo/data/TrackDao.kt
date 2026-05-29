package com.mokie.timelogdemo.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class TrackSummary(
    val trackId: Long,
    val name: String,
    val totalMs: Long,
    val sessionCount: Int,
    val lastSessionStartMs: Long
)

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks ORDER BY createdAtMs DESC")
    fun observeTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE id = :id LIMIT 1")
    suspend fun findTrackById(id: Long): TrackEntity?

    @Query("SELECT id FROM tracks WHERE name = :name LIMIT 1")
    suspend fun findTrackIdByName(name: String): Long?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTrack(track: TrackEntity): Long

    @Query("UPDATE tracks SET name = :newName WHERE id = :id")
    suspend fun renameTrack(id: Long, newName: String)

    @Query("DELETE FROM tracks WHERE id = :id")
    suspend fun deleteTrack(id: Long)

    @Query("DELETE FROM track_inheritance WHERE childTrackId = :id OR parentTrackId = :id")
    suspend fun deleteInheritanceTouching(id: Long)

    @Query("DELETE FROM session_track_allocation WHERE trackId = :id")
    suspend fun deleteAllocationsOfTrack(id: Long)

    @androidx.room.Transaction
    suspend fun deleteTrackCascade(id: Long) {
        // Children lose this parent edge but remain (becoming roots if it was their
        // only parent). All allocations referencing this track are removed; sessions
        // that lose all their allocations as a consequence are NOT auto-deleted —
        // they remain as orphan sessions a future cleanup job can reap.
        deleteInheritanceTouching(id)
        deleteAllocationsOfTrack(id)
        deleteTrack(id)
    }

    // ----- DAG inheritance -----

    @Query("SELECT * FROM track_inheritance")
    fun observeInheritance(): Flow<List<TrackInheritanceEntity>>

    @Query("SELECT * FROM track_inheritance")
    suspend fun getAllInheritance(): List<TrackInheritanceEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertInheritance(edge: TrackInheritanceEntity)

    @Query(
        """
        DELETE FROM track_inheritance
        WHERE childTrackId = :childId AND parentTrackId = :parentId
        """
    )
    suspend fun removeInheritance(childId: Long, parentId: Long)

    @Query(
        """
        SELECT t.id AS trackId,
               t.name AS name,
               COALESCE(SUM(a.durationMs), 0) AS totalMs,
               COUNT(DISTINCT a.sessionId) AS sessionCount,
               COALESCE(MAX(s.startMs), 0) AS lastSessionStartMs
        FROM tracks t
        LEFT JOIN session_track_allocation a ON a.trackId = t.id
        LEFT JOIN sessions s ON s.id = a.sessionId
        GROUP BY t.id, t.name
        ORDER BY lastSessionStartMs DESC, t.createdAtMs DESC
        """
    )
    fun observeTrackSummaries(): Flow<List<TrackSummary>>
}
