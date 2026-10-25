package com.mokie.timelogdemo.tracking

import com.mokie.timelogdemo.tracking.TrackingSession.StopSnapshot
import com.mokie.timelogdemo.tracking.TrackingSession.TrackRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class StopSnapshotTest {

    private fun snapshot(start: Long, end: Long, paused: Long) = StopSnapshot(
        startMs = start,
        endMs = end,
        note = "",
        tracks = listOf(TrackRef(1, "t")),
        pausedAccumulatedMs = paused
    )

    @Test
    fun keepsRealBoundsAndRecordsPauseSeparately() {
        // 10:00:00.500 → 12:00:00.700 with 30 minutes paused.
        val start = 36_000_500L
        val snap = snapshot(start, start + 2 * 3_600_000 + 200, paused = 30 * 60_000)
        assertEquals(36_000_000L, snap.sessionStartMs)
        assertEquals(43_200_000L, snap.sessionEndMs)
        assertEquals(90 * 60_000L, snap.effectiveDurationMs)
        assertEquals(30 * 60_000L, snap.sessionPausedMs)
    }

    @Test
    fun boundsMinusPauseAlwaysEqualsDurationInWholeSeconds() {
        val rnd = Random(1)
        repeat(1_000) {
            val start = rnd.nextLong(1_000_000_000_000, 2_000_000_000_000)
            val span = rnd.nextLong(0, 10 * 3_600_000)
            val paused = rnd.nextLong(0, span + 1)
            val snap = snapshot(start, start + span, paused)
            val stored = snap.sessionEndMs - snap.sessionStartMs - snap.sessionPausedMs
            assertEquals(snap.effectiveDurationMs, stored)
            assertEquals(0L, snap.effectiveDurationMs % 1000)
            assertEquals(0L, snap.sessionPausedMs % 1000)
            assertTrue(snap.sessionPausedMs >= 0)
        }
    }

    @Test
    fun pauseLongerThanSpanYieldsZeroDuration() {
        val snap = snapshot(1_000_000, 1_060_000, paused = 120_000)
        assertEquals(0L, snap.effectiveDurationMs)
        assertEquals(60_000L, snap.sessionPausedMs)
    }
}
