package com.clashai.bot.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayConfirmationTrackerTest {
    @Test fun shellSuccessAloneDoesNotConfirmPlay() {
        val tracker = PlayConfirmationTracker()
        tracker.begin(Card.HOG_RIDER, 1000L)
        assertEquals(PlayConfirmationTracker.Update.Waiting(Card.HOG_RIDER),
            tracker.update(listOf(Card.HOG_RIDER, Card.MUSKETEER, Card.CANNON, Card.LOG), true, 1200L))
    }

    @Test fun confirmsOnlyAfterPlayedCardLeavesCompleteHand() {
        val tracker = PlayConfirmationTracker()
        tracker.begin(Card.HOG_RIDER, 1000L)
        assertEquals(PlayConfirmationTracker.Update.Confirmed(Card.HOG_RIDER),
            tracker.update(listOf(Card.ICE_SPIRIT, Card.MUSKETEER, Card.CANNON, Card.LOG), true, 1500L))
        assertEquals(PlayConfirmationTracker.Update.Idle, tracker.update(emptyList(), false, 1600L))
    }

    @Test fun incompleteHandCannotConfirmPlay() {
        val tracker = PlayConfirmationTracker()
        tracker.begin(Card.HOG_RIDER, 1000L)
        assertEquals(PlayConfirmationTracker.Update.Waiting(Card.HOG_RIDER),
            tracker.update(listOf(Card.MUSKETEER, Card.CANNON, Card.LOG), false, 1500L))
    }

    @Test fun timesOutWhenCardNeverLeavesHand() {
        val tracker = PlayConfirmationTracker(timeoutMs = 2000L)
        tracker.begin(Card.HOG_RIDER, 1000L)
        assertEquals(PlayConfirmationTracker.Update.Failed(Card.HOG_RIDER),
            tracker.update(listOf(Card.HOG_RIDER, Card.MUSKETEER, Card.CANNON, Card.LOG), true, 3000L))
    }

    @Test fun resetCancelsPendingConfirmation() {
        val tracker = PlayConfirmationTracker()
        tracker.begin(Card.HOG_RIDER, 1000L)
        tracker.reset()
        assertEquals(PlayConfirmationTracker.Update.Idle, tracker.update(emptyList(), false, 1100L))
    }
}
