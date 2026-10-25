package com.mokie.timelogdemo.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Apportions a slice of a Session's duration to a Track. Strict mode: the sum of all
 * allocations for a given Session MUST equal the Session's total duration. This invariant
 * is enforced at the DAO layer (SessionDao.insertSessionWithAllocations / updateSessionWithAllocations).
 *
 * Example — a 2h Triathlon session split across three tracks:
 *   (sessionId=7, trackId=swim,    durationMs=30 * 60_000)
 *   (sessionId=7, trackId=cycling, durationMs=30 * 60_000)
 *   (sessionId=7, trackId=running, durationMs=60 * 60_000)
 *
 * The track foreign key cascades only as a safety net: deleting a track goes through
 * TimeLogRepository.deleteTrack, which first re-apportions shared sessions so the
 * invariant still holds once this track's rows disappear.
 */
@Entity(
    tableName = "session_track_allocation",
    primaryKeys = ["sessionId", "trackId"],
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["trackId"]),
        Index(value = ["sessionId"])
    ]
)
data class SessionTrackAllocationEntity(
    val sessionId: Long,
    val trackId: Long,
    val durationMs: Long
)
