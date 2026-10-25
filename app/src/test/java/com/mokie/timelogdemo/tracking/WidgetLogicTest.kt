package com.mokie.timelogdemo.tracking

import com.mokie.timelogdemo.tracking.TimerCommands.PickAction
import com.mokie.timelogdemo.tracking.TrackingSession.TrackRef
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetLogicTest {

    @Test
    fun pickAction_coversEveryRunningState() {
        assertEquals(PickAction.Start, TimerCommands.pickAction(false, emptyList(), 1))
        assertEquals(PickAction.Assign, TimerCommands.pickAction(true, emptyList(), 1))
        assertEquals(PickAction.Ignore, TimerCommands.pickAction(true, listOf(1), 1))
        assertEquals(PickAction.Switch, TimerCommands.pickAction(true, listOf(2), 1))
        assertEquals(PickAction.NeedsApp, TimerCommands.pickAction(true, listOf(1, 2), 1))
    }

    private fun refs(vararg ids: Long) = ids.map { TrackRef(it, "t$it") }

    @Test
    fun shownTracks_keepsRecentOrderAndCapsAtFour() {
        assertEquals(
            refs(1, 2, 3, 4),
            WidgetUpdater.shownTracks(refs(1, 2, 3, 4, 5, 6), running = emptyList())
        )
    }

    @Test
    fun shownTracks_runningTrackOutsideRecentTakesTheLastSlot() {
        assertEquals(
            refs(1, 2, 3, 9),
            WidgetUpdater.shownTracks(refs(1, 2, 3, 4, 5), running = refs(9))
        )
    }

    @Test
    fun shownTracks_neverEvictsAnotherRunningTrack() {
        assertEquals(
            refs(1, 2, 8, 4),
            WidgetUpdater.shownTracks(refs(1, 2, 3, 4), running = refs(4, 8))
        )
    }

    @Test
    fun shownTracks_appendsWhenThereIsRoom() {
        assertEquals(refs(1, 9), WidgetUpdater.shownTracks(refs(1), running = refs(9)))
    }
}
