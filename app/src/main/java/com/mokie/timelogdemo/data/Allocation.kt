package com.mokie.timelogdemo.data

/**
 * Integer apportioning helpers. All functions return lists that sum to exactly
 * the requested target, which is what keeps the strict
 * `SUM(allocation) == session duration` invariant satisfiable.
 */
object Allocation {

    /** Even split with the remainder handed out one unit at a time from the front. */
    fun evenSplit(total: Long, count: Int): List<Long> {
        if (count <= 0) return emptyList()
        val per = total / count
        val rem = (total % count).toInt()
        return List(count) { i -> per + if (i < rem) 1L else 0L }
    }

    /**
     * Scale non-negative [values] to sum exactly to [target], preserving ratios.
     * The last element absorbs rounding. All-zero input falls back to an even split.
     */
    fun proportionalScale(values: List<Long>, target: Long): List<Long> {
        if (values.isEmpty()) return values
        val sum = values.sum()
        if (sum == 0L) return evenSplit(target, values.size)
        val out = LongArray(values.size)
        var assigned = 0L
        for (k in 0 until values.size - 1) {
            val share = (values[k].toDouble() / sum * target).toLong().coerceAtLeast(0L)
            out[k] = share
            assigned += share
        }
        out[values.size - 1] = (target - assigned).coerceAtLeast(0L)
        return out.toList()
    }

    /**
     * [proportionalScale] for millisecond durations at whole-second precision:
     * every result is a whole number of seconds except that any sub-second part of
     * [targetMs] lands on the last element, so the sum is still exactly [targetMs].
     */
    fun scaleMsToTotal(valuesMs: List<Long>, targetMs: Long): List<Long> {
        if (valuesMs.isEmpty()) return valuesMs
        val secs = proportionalScale(valuesMs.map { it / 1000L }, targetMs / 1000L)
        val out = secs.map { it * 1000L }.toMutableList()
        out[out.lastIndex] += targetMs % 1000L
        return out
    }
}
