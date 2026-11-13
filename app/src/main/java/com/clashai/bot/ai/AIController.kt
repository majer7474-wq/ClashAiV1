package com.clashai.bot.ai

import com.clashai.bot.vision.ScreenObservation

class AIController {

    private val engine = Hog26Engine()
    private val elixirTracker = ElixirTracker()
    private val enemyTracker = EnemyTracker()

    fun process(observation: ScreenObservation): AIAction {

        observation.elixir?.let {
            elixirTracker.update(it)
        }

        observation.visibleCards.forEach {
            enemyTracker.recordEnemyCard(it)
        }

        val state = BattleState(
            elixir = elixirTracker.getElixir(),
            hand = observation.visibleCards,
            enemyCards = enemyTracker.getKnownCards(),
            enemyThreat = observation.enemyThreat
        )

        return engine.chooseAction(state)
    }

    fun reset() {
        engine.reset()
        elixirTracker.reset()
        enemyTracker.reset()
    }
}
