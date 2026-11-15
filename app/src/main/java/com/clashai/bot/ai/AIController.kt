package com.clashai.bot.ai

import com.clashai.bot.vision.ScreenObservation

/**
 * Validates live vision data and delegates actual move selection to the
 * Hog 2.6 strategy engine. Invalid observations always produce a no-op.
 */
class AIController {
    private val strategy = Hog26Engine()

    fun process(observation: ScreenObservation): AIAction {
        val hand = observation.visibleCards.distinct()
        val elixir = observation.elixir

        if (!observation.handComplete ||
            observation.handConfidence < MIN_HAND_CONFIDENCE ||
            hand.size != 4
        ) {
            return noAction("Hand recognition incomplete or uncertain; no card will be played")
        }

        if (elixir == null || elixir !in 0f..10f) {
            return noAction("Elixir reading unavailable; waiting")
        }

        if (System.currentTimeMillis() - observation.capturedAtMs > MAX_OBSERVATION_AGE_MS) {
            return noAction("Live observation is stale; waiting for a fresh frame")
        }

        return strategy.chooseAction(
            BattleState(
                elixir = elixir,
                hand = hand,
                enemyThreat = observation.enemyThreat,
                handConfidence = observation.handConfidence,
                observationAgeMs = System.currentTimeMillis() - observation.capturedAtMs
            )
        )
    }

    fun recordPlayed(card: Card) {
        strategy.recordPlay(card)
    }

    private fun noAction(reason: String) = AIAction(
        card = null,
        reason = reason,
        priority = 0,
        recommendedPlacement = "NONE"
    )

    companion object {
        private const val MIN_HAND_CONFIDENCE = 0.84f
        private const val MAX_OBSERVATION_AGE_MS = 900L
    }
}
