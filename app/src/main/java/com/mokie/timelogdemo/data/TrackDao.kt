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

    @Query("SELECT id FROM tracks WHERE id IN (:ids)")
    suspend fun existingIds(ids: List<Long>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTrack(track: TrackEntity): Long

    @Query("UPDATE tracks SET name = :newName WHERE id = :id")
    suspend fun renameTrack(id: Long, newName: String)

    /** Inheritance edges and allocations cascade via foreign keys. */
    @Query("DELETE FROM tracks WHERE id = :id")
    suspend fun deleteTrack(id: Long)

    // ----- DAG inheritance -----

    @Query("SELECT * FROM track_inheritance")
    fun observeInheritance(): Flow<List<TrackInheritanceEntity>>

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
