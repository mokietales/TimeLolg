package com.mokie.timelogdemo.data

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mokie.timelogdemo.tracking.TrackingSession
import com.mokie.timelogdemo.tracking.TrackingSession.StopSnapshot
import com.mokie.timelogdemo.tracking.TrackingSession.TrackRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TimeLogRepositoryTest {

    private lateinit var db: TimeLogDatabase
    private lateinit var tracking: TrackingSession
    private lateinit var scope: CoroutineScope
    private lateinit var repo: TimeLogRepository

    @Before
    fun setUp() {
        // The repository hops to Main to touch TrackingSession; run that inline.
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, TimeLogDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        tracking = TrackingSession(context).also { it.clear() }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        repo = TimeLogRepository(db, tracking, scope)
    }

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
        Dispatchers.resetMain()
    }

    private suspend fun newTrack(name: String): TrackRef = repo.ensureTrack(name)!!

    private suspend fun insert(start: Long, durationMs: Long, alloc: Map<Long, Long>) {
        assertTrue(repo.insertManualSession(start, durationMs, note = "", allocMs = alloc))
    }

    private suspend fun sessions(): Map<Long, List<SessionAllocationRow>> =
        repo.sessionRows.first().groupBy { it.sessionId }

    // ----- Tracks -----

    @Test
    fun ensureTrack_reusesExistingName() = runBlocking {
        val a = newTrack("work")
        val b = newTrack("  work ")
        assertEquals(a.id, b.id)
        assertNull(repo.ensureTrack("   "))
    }

    @Test
    fun renameTrack_rejectsTakenAndBlankNames() = runBlocking {
        val work = newTrack("work")
        val play = newTrack("play")
        assertEquals(TimeLogRepository.RenameResult.NameTaken, repo.renameTrack(play.id, "work"))
        assertEquals(TimeLogRepository.RenameResult.Blank, repo.renameTrack(play.id, " "))
        assertEquals(TimeLogRepository.RenameResult.Ok, repo.renameTrack(work.id, "work")) // own name
        assertEquals(TimeLogRepository.RenameResult.Ok, repo.renameTrack(play.id, "games"))
        assertEquals("games", repo.findTrack(play.id)?.name)
    }

    @Test
    fun renameTrack_updatesTheRunningTimer() = runBlocking {
        val play = newTrack("play")
        tracking.start(play)
        repo.renameTrack(play.id, "games")
        assertEquals("games", tracking.tracks.single().name)
    }

    @Test
    fun deleteTrack_removesOwnSessionsAndRebalancesSharedOnes() = runBlocking {
        val work = newTrack("work")
        val play = newTrack("play")
        val read = newTrack("read")
        insert(0, 60_000, mapOf(work.id to 60_000))                                     // only work
        insert(100_000, 90_000, mapOf(work.id to 30_000, play.id to 40_000, read.id to 20_000))
        insert(300_000, 10_000, mapOf(play.id to 10_000))                                // untouched

        assertEquals(
            TimeLogRepository.DeleteImpact(sessionsRemoved = 1, sessionsRebalanced = 1),
            repo.deleteImpact(work.id)
        )
        repo.deleteTrack(work.id)

        val remaining = sessions()
        assertEquals(2, remaining.size)
        val shared = remaining.values.single { rows -> rows.size == 2 }
        assertEquals(90_000L, shared.sumOf { it.durationMs })
        assertEquals(90_000L, shared.first().sessionDurationMs)
        // play:read was 2:1, so work's 30s splits 20s/10s.
        assertEquals(60_000L, shared.single { it.trackId == play.id }.durationMs)
        assertEquals(30_000L, shared.single { it.trackId == read.id }.durationMs)
        assertNull(repo.findTrack(work.id))
    }

    @Test
    fun deleteTrack_dropsItsEdgesAndLeavesChildrenAsRoots() = runBlocking {
        val parent = newTrack("parent")
        val child = newTrack("child")
        repo.addParent(childId = child.id, parentId = parent.id)
        repo.deleteTrack(parent.id)
        val tree = TrackTree(
            tracks = db.trackDao().observeTracks().first(),
            inheritance = db.trackDao().observeInheritance().first(),
            allocations = emptyList()
        )
        assertEquals(listOf(child.id), tree.roots.map { it.id })
        assertTrue(db.trackDao().observeInheritance().first().isEmpty())
    }

    @Test
    fun deleteTrack_removesItFromTheRunningTimer() = runBlocking {
        val play = newTrack("play")
        tracking.start(play)
        repo.deleteTrack(play.id)
        assertTrue(tracking.isActive)
        assertTrue(tracking.tracks.isEmpty())
    }

    // ----- Sessions -----

    @Test
    fun updateSession_rejectsUnbalancedAllocations() = runBlocking {
        val work = newTrack("work")
        insert(0, 60_000, mapOf(work.id to 60_000))
        val id = sessions().keys.single()
        val session = repo.sessionForEdit(id)!!.session
        assertFalse(repo.updateSession(session.copy(endMs = 120_000), mapOf(work.id to 60_000)))
        assertTrue(repo.updateSession(session.copy(endMs = 120_000), mapOf(work.id to 120_000)))
        assertEquals(120_000L, sessions().values.single().single().sessionDurationMs)
    }

    @Test
    fun overlapping_excludesSelfAndTouchingSpans() = runBlocking {
        val work = newTrack("work")
        insert(1_000_000, 60_000, mapOf(work.id to 60_000))
        val id = sessions().keys.single()
        assertEquals(1, repo.overlapping(1_030_000, 1_100_000).size)
        assertTrue(repo.overlapping(1_060_000, 1_100_000).isEmpty())   // starts as it ends
        assertTrue(repo.overlapping(900_000, 1_000_000).isEmpty())     // ends as it starts
        assertTrue(repo.overlapping(1_000_000, 1_060_000, excludeSessionId = id).isEmpty())
    }

    // ----- Timer commit -----

    private fun snapshotOfRunning(spanMs: Long, pausedMs: Long): StopSnapshot {
        val start = tracking.startMs!!
        return StopSnapshot(
            startMs = start,
            endMs = start + spanMs,
            note = " note ",
            tracks = tracking.tracks,
            pausedAccumulatedMs = pausedMs
        )
    }

    @Test
    fun commitTimer_storesRealSpanAndPauseThenClears() = runBlocking {
        val work = newTrack("work")
        tracking.start(work)
        val snap = snapshotOfRunning(spanMs = 125_000, pausedMs = 30_000)
        assertTrue(repo.commitTimer(snap, mapOf(work.id to snap.effectiveDurationMs)))

        val row = sessions().values.single().single()
        assertEquals(snap.sessionStartMs, row.startMs)
        assertEquals(snap.sessionEndMs, row.endMs)
        assertEquals(95_000L, row.sessionDurationMs)
        assertEquals(snap.sessionPausedMs, row.pausedMs)
        assertEquals("note", row.note)
        assertFalse(tracking.isActive)
    }

    @Test
    fun commitTimer_withSwitchStartsTheNextTrack() = runBlocking {
        val work = newTrack("work")
        val play = newTrack("play")
        tracking.start(work)
        val snap = snapshotOfRunning(spanMs = 60_000, pausedMs = 0)
        assertTrue(repo.commitTimer(snap, mapOf(work.id to 60_000), thenStart = play))
        assertTrue(tracking.isActive)
        assertEquals(listOf(play), tracking.tracks)
    }

    @Test
    fun commitTimer_keepsTimerRunningWhenTheWriteFails() = runBlocking {
        val work = newTrack("work")
        tracking.start(work)
        val snap = snapshotOfRunning(spanMs = 60_000, pausedMs = 0)
        // Unbalanced: 59s allocated for a 60s session.
        assertFalse(repo.commitTimer(snap, mapOf(work.id to 59_000)))
        assertTrue(tracking.isActive)
        assertTrue(sessions().isEmpty())
    }

    @Test
    fun commitTimer_refusesTracksThatNoLongerExist() = runBlocking {
        val work = newTrack("work")
        tracking.start(work)
        val snap = snapshotOfRunning(spanMs = 60_000, pausedMs = 0)
        db.trackDao().deleteTrack(work.id)
        assertFalse(repo.commitTimer(snap, mapOf(work.id to 60_000)))
        assertTrue(tracking.isActive)
    }

    @Test
    fun commitTimer_doesNotClearANewerSession() = runBlocking {
        val work = newTrack("work")
        tracking.start(work)
        val stale = snapshotOfRunning(spanMs = 60_000, pausedMs = 0)
        tracking.clear()
        tracking.startBlank()
        val newer = tracking.startMs
        // The stale snapshot's start won't match the running session.
        repo.commitTimer(stale.copy(startMs = stale.startMs - 5_000), mapOf(work.id to 60_000))
        assertEquals(newer, tracking.startMs)
    }
}
