package com.clashai.bot.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ElixirTrackerTest {

    @Test
    fun visualUpdateSetsValueAndFreshness() {
        var now = 1_000L
        val tracker = ElixirTracker { now }

        tracker.update(7f)

        assertEquals(7f, tracker.getElixir(), 0.001f)
        assertEquals(0L, tracker.getVisualAgeMs())
    }

    @Test
    fun visualTimestampAtZeroIsStillFresh() {
        var now = 0L
        val tracker = ElixirTracker { now }

        tracker.update(6f)

        assertEquals(0L, tracker.getVisualAgeMs())

        now = 250L
        assertEquals(250L, tracker.getVisualAgeMs())
    }

    @Test
    fun elixirRegeneratesAtSingleElixirRate() {
        var now = 1_000L
        val tracker = ElixirTracker { now }
        tracker.update(4f)

        now += 2_800L

        assertEquals(5f, tracker.getElixir(), 0.01f)
    }

    @Test
    fun spendingCardReducesElixir() {
        var now = 1_000L
        val tracker = ElixirTracker { now }
        tracker.update(5f)

        assertTrue(tracker.spend(Card.HOG_RIDER))
        assertEquals(1f, tracker.getElixir(), 0.001f)
    }

    @Test
    fun cannotSpendUnaffordableCard() {
        var now = 1_000L
        val tracker = ElixirTracker { now }
        tracker.update(3f)

        assertFalse(tracker.spend(Card.HOG_RIDER))
        assertEquals(3f, tracker.getElixir(), 0.001f)
    }

    @Test
    fun resetInvalidatesVisualReading() {
        var now = 1_000L
        val tracker = ElixirTracker { now }
        tracker.update(8f)

        now += 500L
        tracker.reset()

        assertEquals(Long.MAX_VALUE, tracker.getVisualAgeMs())
        assertEquals(5f, tracker.getElixir(), 0.001f)
    }
}
