package com.mokie.timelogdemo.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Builds a [TrackTree] reactively from the three streams that contribute to it.
 * The TrackTree object is recomputed any time any of the three streams emits;
 * this is fine at the expected scale (tens of tracks, low-thousands of
 * allocations) and avoids the need for a hand-rolled diff.
 */
fun observeTrackTree(
    trackDao: TrackDao,
    sessionDao: SessionDao
): Flow<TrackTree> = combine(
    trackDao.observeTracks(),
    trackDao.observeInheritance(),
    sessionDao.observeAllocationsLight()
) { tracks, edges, allocs ->
    TrackTree(tracks = tracks, inheritance = edges, allocations = allocs)
}
