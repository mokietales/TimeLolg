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
    fun shownTracks_keepsRecentOrderAndCapsAtSlotCount() {
        assertEquals(
            refs(1, 2, 3, 4),
            WidgetUpdater.shownTracks(refs(1, 2, 3, 4, 5, 6), running = emptyList(), slots = 4)
        )
        assertEquals(
            refs(1, 2),
            WidgetUpdater.shownTracks(refs(1, 2, 3, 4, 5, 6), running = emptyList(), slots = 2)
        )
    }

    @Test
    fun shownTracks_runningTrackOutsideRecentTakesTheLastSlot() {
        assertEquals(
            refs(1, 2, 3, 9),
            WidgetUpdater.shownTracks(refs(1, 2, 3, 4, 5), running = refs(9), slots = 4)
        )
    }

    @Test
    fun shownTracks_neverEvictsAnotherRunningTrack() {
        assertEquals(
            refs(1, 2, 8, 4),
            WidgetUpdater.shownTracks(refs(1, 2, 3, 4), running = refs(4, 8), slots = 4)
        )
    }

    @Test
    fun shownTracks_appendsWhenThereIsRoom() {
        assertEquals(refs(1, 9), WidgetUpdater.shownTracks(refs(1), running = refs(9), slots = 4))
    }

    @Test
    fun frozenClock_matchesChronometerShape() {
        assertEquals("00:09", QuickWidgetProvider.frozenClock(9_300))
        assertEquals("02:42", QuickWidgetProvider.frozenClock(162_000))
        assertEquals("1:05:03", QuickWidgetProvider.frozenClock(3_903_000))
    }

    @Test
    fun rowHeights_growByOneButtonPerRow() {
        assertEquals(128f, TimerWidgetProvider.heightForRows(1))
        assertEquals(266f, TimerWidgetProvider.heightForRows(4))
    }

    @Test
    fun columns_stackWhenEveryThemeFitsOnItsOwnRow() {
        assertEquals(1, TimerWidgetProvider.columnsFor(2, rowCount = 3, themeCount = 2))
        assertEquals(2, TimerWidgetProvider.columnsFor(2, rowCount = 3, themeCount = 4))
        assertEquals(1, TimerWidgetProvider.columnsFor(1, rowCount = 3, themeCount = 2))
        assertEquals(2, TimerWidgetProvider.columnsFor(2, rowCount = 1, themeCount = 2))
    }

    @Test
    fun rowContent_fillsSpareHeightUpToACap() {
        assertEquals(40f, TimerWidgetProvider.rowContentHeightDp(3, usedRows = 3, hasHint = false))
        assertEquals(50f, TimerWidgetProvider.rowContentHeightDp(3, usedRows = 2, hasHint = true))
        assertEquals(52f, TimerWidgetProvider.rowContentHeightDp(4, usedRows = 1, hasHint = false))
    }
}
