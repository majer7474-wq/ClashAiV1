package com.clashai.bot.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Hog26EngineTest {

    private fun validState(
        elixir: Float = 5f,
        hand: List<Card> = listOf(
            Card.HOG_RIDER,
            Card.MUSKETEER,
            Card.CANNON,
            Card.FIREBALL
        ),
        handConfidence: Float = 0.95f,
        frameAgeMs: Long = 100L,
        elixirAgeMs: Long = 100L,
        elixirConfidence: Float = 0.95f
    ) = BattleState(
        elixir = elixir,
        hand = hand,
        handConfidence = handConfidence,
        observationAgeMs = frameAgeMs,
        elixirObservationAgeMs = elixirAgeMs,
        elixirConfidence = elixirConfidence
    )

    @Test
    fun staleFrameMustWait() {
        val action = Hog26Engine().chooseAction(validState(frameAgeMs = 1501L))
        assertNull(action.card)
    }

    @Test
    fun staleElixirMustWait() {
        val action = Hog26Engine().chooseAction(validState(elixirAgeMs = 1501L))
        assertNull(action.card)
    }

    @Test
    fun lowConfidenceElixirMustWait() {
        val action = Hog26Engine().chooseAction(validState(elixirConfidence = 0.84f))
        assertNull(action.card)
    }

    @Test
    fun nonFiniteElixirConfidenceMustWait() {
        val action = Hog26Engine().chooseAction(validState(elixirConfidence = Float.NaN))
        assertNull(action.card)
    }

    @Test
    fun incompleteHandMustWait() {
        val action = Hog26Engine().chooseAction(
            validState(hand = listOf(Card.HOG_RIDER, Card.MUSKETEER, Card.CANNON))
        )
        assertNull(action.card)
    }

    @Test
    fun uncertainHandMustWait() {
        val action = Hog26Engine().chooseAction(validState(handConfidence = 0.84f))
        assertNull(action.card)
    }

    @Test
    fun unaffordableHandMustWait() {
        val action = Hog26Engine().chooseAction(validState(elixir = 2f))
        assertNull(action.card)
    }

    @Test
    fun choosesHogWhenConfirmedAndAffordable() {
        val action = Hog26Engine().chooseAction(validState(elixir = 5f))
        assertEquals(Card.HOG_RIDER, action.card)
    }
}
