package com.clashai.bot.ai

import android.os.SystemClock
import com.clashai.bot.vision.ScreenObservation

class AIController {

    private val engine = Hog26Engine()
    private val elixirTracker = ElixirTracker()
    private val enemyTracker = EnemyTracker()

    fun process(observation: ScreenObservation): AIAction {
        observation.elixir?.let(elixirTracker::update)

        // visibleCards represents our own hand, never the opponent's deck.
        val state = BattleState(
            elixir = elixirTracker.getElixir(),
            hand = observation.visibleCards,
            enemyCards = enemyTracker.getKnownCards(),
            enemyThreat = observation.enemyThreat,
            handConfidence = observation.handConfidence,
            observationAgeMs = (SystemClock.elapsedRealtime() - observation.capturedAtMs)
                .coerceAtLeast(0L),
            elixirObservationAgeMs = elixirTracker.getVisualAgeMs()
        )

        return engine.chooseAction(state)
    }

    fun reset() {
        engine.reset()
        elixirTracker.reset()
        enemyTracker.reset()
    }
}
