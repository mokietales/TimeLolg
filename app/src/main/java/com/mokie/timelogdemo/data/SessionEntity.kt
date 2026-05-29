package com.mokie.timelogdemo.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A Session is a single continuous span of real time that was logged. It does not own time
 * accumulation by itself; its time is apportioned to one or more Tracks via SessionTrackAllocation.
 */
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startMs: Long,
    val endMs: Long,
    val note: String,
    val createdAtMs: Long
) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0L)
}
