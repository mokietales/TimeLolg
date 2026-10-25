package com.mokie.timelogdemo.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A Session is a single span of real time that was logged. It does not own time
 * accumulation by itself; its time is apportioned to one or more Tracks via SessionTrackAllocation.
 *
 * [startMs]/[endMs] are the real wall-clock bounds; [pausedMs] is time inside that
 * span that was paused and is excluded from [durationMs]. All three are whole seconds.
 */
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startMs: Long,
    val endMs: Long,
    val note: String,
    val createdAtMs: Long,
    val pausedMs: Long = 0L
) {
    val durationMs: Long get() = (endMs - startMs - pausedMs).coerceAtLeast(0L)
}
