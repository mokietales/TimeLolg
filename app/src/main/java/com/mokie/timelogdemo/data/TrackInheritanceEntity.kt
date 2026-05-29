package com.mokie.timelogdemo.data

import androidx.room.Entity
import androidx.room.Index

/**
 * (Reserved) Hierarchical relation between Tracks, e.g. "Reading > Non-fiction > Atomic Habits".
 * Not exposed in the UI yet; kept in schema for future drill-down rollup statistics.
 */
@Entity(
    tableName = "track_inheritance",
    primaryKeys = ["childTrackId", "parentTrackId"],
    indices = [
        Index(value = ["parentTrackId"]),
        Index(value = ["childTrackId"])
    ]
)
data class TrackInheritanceEntity(
    val childTrackId: Long,
    val parentTrackId: Long
)
