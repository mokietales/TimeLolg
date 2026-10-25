package com.mokie.timelogdemo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class AllocationTest {

    @Test
    fun evenSplit_handsRemainderToTheFront() {
        assertEquals(listOf(4L, 3L, 3L), Allocation.evenSplit(10, 3))
        assertEquals(listOf(5L, 5L), Allocation.evenSplit(10, 2))
        assertEquals(emptyList<Long>(), Allocation.evenSplit(10, 0))
    }

    @Test
    fun proportionalScale_sumsExactlyToTarget() {
        val rnd = Random(42)
        repeat(500) {
            val values = List(rnd.nextInt(1, 6)) { rnd.nextLong(0, 10_000) }
            val target = rnd.nextLong(0, 100_000)
            val out = Allocation.proportionalScale(values, target)
            assertEquals("values=$values target=$target", target, out.sum())
            assertTrue(out.all { it >= 0 })
        }
    }

    @Test
    fun proportionalScale_preservesRatios() {
        assertEquals(listOf(200L, 600L, 200L), Allocation.proportionalScale(listOf(1, 3, 1), 1000))
    }

    @Test
    fun proportionalScale_allZerosFallsBackToEvenSplit() {
        assertEquals(listOf(4L, 3L, 3L), Allocation.proportionalScale(listOf(0, 0, 0), 10))
    }

    @Test
    fun scaleMsToTotal_keepsWholeSecondsAndExactSum() {
        val out = Allocation.scaleMsToTotal(listOf(30_000, 30_000, 60_000), 90_500)
        assertEquals(90_500L, out.sum())
        assertTrue(out.dropLast(1).all { it % 1000 == 0L })
    }

    @Test
    fun scaleMsToTotal_randomCasesStayExact() {
        val rnd = Random(7)
        repeat(500) {
            val values = List(rnd.nextInt(1, 5)) { rnd.nextLong(0, 3_600) * 1000 }
            val target = rnd.nextLong(1, 7_200_000)
            assertEquals(target, Allocation.scaleMsToTotal(values, target).sum())
        }
    }
}
