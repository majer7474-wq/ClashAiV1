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
        ) return noAction("Hand recognition incomplete or uncertain; no card will be played")

        if (elixir == null || elixir !in 0f..10f)
            return noAction("Elixir reading unavailable; waiting")

        val age = System.currentTimeMillis() - observation.capturedAtMs
        if (age < 0L || age > MAX_OBSERVATION_AGE_MS)
            return noAction("Live observation is stale; waiting for a fresh frame")

        val frameWidth = observation.frameWidth.toFloat().coerceAtLeast(1f)
        val frameHeight = observation.frameHeight.toFloat().coerceAtLeast(1f)
        val enemyUnits = observation.detections
            .asSequence()
            .filter { it.centerY < frameHeight * 0.50f }
            .filter { it.confidence >= MIN_ENEMY_CONFIDENCE }
            .filter { it.label !in TOWER_LABELS }
            .map {
                ObservedEnemy(
                    label = it.label,
                    x = (it.centerX / frameWidth).coerceIn(0f, 1f),
                    y = (it.centerY / frameHeight).coerceIn(0f, 1f),
                    confidence = it.confidence
                )
            }
            .toList()

        return engine.chooseAction(
            BattleState(
                elixir = elixir,
                hand = hand,
                enemyThreat = observation.enemyThreat,
                handConfidence = observation.handConfidence,
                observationAgeMs = age,
                enemyUnits = enemyUnits
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
        private const val MIN_ENEMY_CONFIDENCE = 0.50f
        private val TOWER_LABELS = setOf(
            "king-tower", "queen-tower", "dagger-duchess-tower", "cannoneer-tower"
        )
    }
}
