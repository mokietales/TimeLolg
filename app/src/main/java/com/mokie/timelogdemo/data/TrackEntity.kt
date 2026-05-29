package com.mokie.timelogdemo.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A Track is "something I want to accumulate time against" — e.g. Swimming, Coding, Reading.
 * Tracks are persistent buckets; time is poured into them by Sessions via SessionTrackAllocation.
 */
@Entity(
    tableName = "tracks",
    indices = [Index(value = ["name"], unique = true)]
)
data class TrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAtMs: Long
)
