package com.clashai.bot.ai

import com.clashai.bot.vision.ScreenObservation

/**
 * Validates live vision data and delegates decisions to the shared ClashAiV1
 * engine. Invalid observations always produce a no-op.
 */
class AIController {
    private val engine = ClashAIEngine()
    private val elixirTracker = ElixirTracker()

    fun process(observation: ScreenObservation): AIAction {
        val hand = observation.visibleCards.distinct()
        val visualElixir = observation.elixir
        if (visualElixir != null) elixirTracker.update(visualElixir)
        val elixir = visualElixir ?: elixirTracker.getElixir().takeIf {
            elixirTracker.getVisualAgeMs() <= MAX_ELIXIR_ESTIMATE_AGE_MS
        }

        if (!observation.handComplete ||
            observation.handConfidence < MIN_HAND_CONFIDENCE ||
            hand.size != 4
        ) {
            return noAction("Hand recognition incomplete or uncertain; no card will be played")
        }
        if (elixir == null || elixir !in 0f..10f) {
            return noAction("Elixir reading unavailable; waiting")
        }

        val age = System.currentTimeMillis() - observation.capturedAtMs
        if (age < 0L || age > MAX_OBSERVATION_AGE_MS) {
            return noAction("Live observation is stale; waiting for a fresh frame")
        }

        return engine.chooseAction(
            BattleState(
                elixir = elixir,
                hand = hand,
                enemyThreat = observation.enemyThreat,
                handConfidence = observation.handConfidence,
                observationAgeMs = age
            )
        )
    }

    fun recordPlayed(card: Card) {
        engine.recordPlay(card)
    }

    private fun noAction(reason: String) = AIAction(
        card = null,
        reason = reason,
        priority = 0,
        recommendedPlacement = "NONE"
    )

    companion object {
        private const val MIN_HAND_CONFIDENCE = 0.70f
        private const val MAX_OBSERVATION_AGE_MS = 900L
        private const val MAX_ELIXIR_ESTIMATE_AGE_MS = 1800L
    }
}
