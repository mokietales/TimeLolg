package com.mokie.timelogdemo.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Parent → child edge in the Track DAG, e.g. "Reading > Non-fiction > Atomic Habits".
 * A track may have several parents. Edges disappear with either endpoint.
 */
@Entity(
    tableName = "track_inheritance",
    primaryKeys = ["childTrackId", "parentTrackId"],
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["childTrackId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentTrackId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["parentTrackId"]),
        Index(value = ["childTrackId"])
    ]
)
data class TrackInheritanceEntity(
    val childTrackId: Long,
    val parentTrackId: Long
)
