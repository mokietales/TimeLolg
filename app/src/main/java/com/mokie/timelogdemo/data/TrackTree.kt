package com.mokie.timelogdemo.data

/**
 * In-memory snapshot of the Track DAG plus per-track time stats. Built once per
 * observed change of (tracks, inheritance, allocations) and read many times by
 * the UI. The expected scale is small (dozens of tracks), so we walk the graph
 * naively and keep the math simple.
 *
 * Semantics:
 *   - `selfMs[id]`  = time directly allocated to this track
 *   - `totalMs[id]` = self + sum over **deduped descendant set**
 *
 * DAG note: a descendant reachable via multiple paths is counted ONCE in its
 * own ancestor's total. But the same descendant time may legitimately appear
 * in multiple ancestor totals (different "lenses"). When the UI needs a true
 * grand total without double counting, sum from sessions, not from track totals.
 */
class TrackTree(
    val tracks: List<TrackEntity>,
    inheritance: List<TrackInheritanceEntity>,
    allocations: List<SessionTrackAllocationOnly>
) {
    /** Minimal allocation row needed to compute self-time totals. */
    data class SessionTrackAllocationOnly(val trackId: Long, val durationMs: Long)

    val byId: Map<Long, TrackEntity> = tracks.associateBy { it.id }

    /** child -> parents */
    val parentsOf: Map<Long, List<Long>>

    /** parent -> children (alphabetic by child name for stable display) */
    val childrenOf: Map<Long, List<Long>>

    /** Tracks that have no parent. */
    val roots: List<TrackEntity>

    /** Time directly attributed to this track (sum of its allocations). */
    val selfMs: Map<Long, Long>

    /** Self-time + sum over deduped descendant set. */
    val totalMs: Map<Long, Long>

    /** Number of unique descendants (excluding self). */
    val descendantCount: Map<Long, Int>

    init {
        val pMap = HashMap<Long, MutableList<Long>>()
        val cMap = HashMap<Long, MutableList<Long>>()
        val knownIds = byId.keys
        inheritance.forEach { e ->
            if (e.childTrackId !in knownIds || e.parentTrackId !in knownIds) return@forEach
            pMap.getOrPut(e.childTrackId) { mutableListOf() }.add(e.parentTrackId)
            cMap.getOrPut(e.parentTrackId) { mutableListOf() }.add(e.childTrackId)
        }
        // Sort children for stable display order.
        val nameOf = byId.mapValues { it.value.name }
        cMap.replaceAll { _, kids ->
            kids.distinct().sortedBy { nameOf[it].orEmpty() }.toMutableList()
        }
        pMap.replaceAll { _, ps ->
            ps.distinct().sortedBy { nameOf[it].orEmpty() }.toMutableList()
        }
        parentsOf = pMap
        childrenOf = cMap

        roots = tracks.filter { (parentsOf[it.id].isNullOrEmpty()) }

        val self = HashMap<Long, Long>()
        allocations.forEach { a ->
            self.merge(a.trackId, a.durationMs) { x, y -> x + y }
        }
        selfMs = self

        val totalAcc = HashMap<Long, Long>()
        val descCount = HashMap<Long, Int>()
        for (t in tracks) {
            val descendants = collectDescendants(t.id)
            descCount[t.id] = descendants.size - 1 // exclude self
            totalAcc[t.id] = descendants.sumOf { selfMs[it] ?: 0L }
        }
        totalMs = totalAcc
        descendantCount = descCount
    }

    /** Returns set including [rootId] itself. Cycle-safe. */
    fun collectDescendants(rootId: Long): Set<Long> {
        val out = LinkedHashSet<Long>()
        val stack = ArrayDeque<Long>()
        stack.addLast(rootId)
        while (stack.isNotEmpty()) {
            val cur = stack.removeLast()
            if (!out.add(cur)) continue
            childrenOf[cur]?.forEach { stack.addLast(it) }
        }
        return out
    }

    /** Returns set including [leafId] itself. Cycle-safe. */
    fun collectAncestors(leafId: Long): Set<Long> {
        val out = LinkedHashSet<Long>()
        val stack = ArrayDeque<Long>()
        stack.addLast(leafId)
        while (stack.isNotEmpty()) {
            val cur = stack.removeLast()
            if (!out.add(cur)) continue
            parentsOf[cur]?.forEach { stack.addLast(it) }
        }
        return out
    }

    /**
     * Whether the edge [child] -> [parent] (i.e. [parent] becomes a parent of
     * [child]) is allowed. Disallowed if it would create a cycle, if it's a
     * self-loop, or if the edge already exists.
     */
    fun canAddParent(child: Long, parent: Long): Boolean {
        if (child == parent) return false
        if (parentsOf[child]?.contains(parent) == true) return false
        // Cycle iff `parent` is already a descendant of `child`.
        return parent !in collectDescendants(child)
    }

    /** Tracks that are valid candidates as a new parent of [child]. */
    fun candidateParentsFor(child: Long): List<TrackEntity> {
        val forbidden = collectDescendants(child) +
            (parentsOf[child]?.toSet() ?: emptySet())
        return tracks.filter { it.id !in forbidden }
    }
}
