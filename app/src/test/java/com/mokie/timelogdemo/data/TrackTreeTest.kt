package com.mokie.timelogdemo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackTreeTest {

    private fun track(id: Long, name: String = "t$id") = TrackEntity(id = id, name = name, createdAtMs = id)
    private fun edge(child: Long, parent: Long) = TrackInheritanceEntity(childTrackId = child, parentTrackId = parent)
    private fun alloc(track: Long, ms: Long) = TrackTree.SessionTrackAllocationOnly(track, ms)

    /**
     *        1
     *       / \
     *      2   3
     *       \ /
     *        4      (4 has two parents: a diamond)
     */
    private val diamond = TrackTree(
        tracks = listOf(track(1), track(2), track(3), track(4)),
        inheritance = listOf(edge(2, 1), edge(3, 1), edge(4, 2), edge(4, 3)),
        allocations = listOf(alloc(1, 100), alloc(2, 20), alloc(3, 30), alloc(4, 4), alloc(4, 1))
    )

    @Test
    fun selfTimeSumsAllocations() {
        assertEquals(5L, diamond.selfMs[4])
    }

    @Test
    fun totalCountsASharedDescendantOnce() {
        assertEquals(100L + 20 + 30 + 5, diamond.totalMs[1])
        assertEquals(20L + 5, diamond.totalMs[2])
        assertEquals(30L + 5, diamond.totalMs[3])
        assertEquals(3, diamond.descendantCount[1])
    }

    @Test
    fun rootsAreTracksWithoutParents() {
        assertEquals(listOf(1L), diamond.roots.map { it.id })
    }

    @Test
    fun canAddParent_rejectsCyclesSelfLoopsAndDuplicates() {
        assertFalse("cycle", diamond.canAddParent(child = 1, parent = 4))
        assertFalse("self", diamond.canAddParent(child = 2, parent = 2))
        assertFalse("duplicate", diamond.canAddParent(child = 4, parent = 2))
        assertTrue(diamond.canAddParent(child = 3, parent = 2))
    }

    @Test
    fun canAddParent_allowsTracksTheTreeHasNotSeenYet() {
        assertTrue(diamond.canAddParent(child = 99, parent = 1))
    }

    @Test
    fun edgesToUnknownTracksAreIgnored() {
        val tree = TrackTree(
            tracks = listOf(track(1)),
            inheritance = listOf(edge(1, 42)),
            allocations = emptyList()
        )
        assertEquals(listOf(1L), tree.roots.map { it.id })
        assertTrue(tree.parentsOf[1].isNullOrEmpty())
    }
}
