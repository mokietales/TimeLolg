package com.mokie.timelogdemo.data

import androidx.room.Entity
import androidx.room.Index

/**
 * Apportions a slice of a Session's duration to a Track. Strict mode: the sum of all
 * allocations for a given Session MUST equal the Session's total duration. This invariant
 * is enforced at the DAO layer (SessionDao.insertSessionWithAllocations / updateAllocations).
 *
 * Example — a 2h Triathlon session split across three tracks:
 *   (sessionId=7, trackId=swim,    durationMs=30 * 60_000)
 *   (sessionId=7, trackId=cycling, durationMs=30 * 60_000)
 *   (sessionId=7, trackId=running, durationMs=60 * 60_000)
 */
@Entity(
    tableName = "session_track_allocation",
    primaryKeys = ["sessionId", "trackId"],
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
