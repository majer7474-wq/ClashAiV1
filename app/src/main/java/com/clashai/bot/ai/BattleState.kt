package com.clashai.bot.ai

data class BattleState(
    val elixir: Float,
    val hand: List<Card>,
    val enemyCards: List<Card> = emptyList(),
    val enemyThreat: Threat = Threat.NONE,
    val doubleElixir: Boolean = false,
    val timeRemaining: Int = 180,
    val towerAdvantage: Int = 0,
    val enemyHasBuilding: Boolean = false,
    val tripleElixir: Boolean = false,
    val handConfidence: Float = 0f,
    val observationAgeMs: Long = Long.MAX_VALUE,
    val elixirObservationAgeMs: Long = Long.MAX_VALUE
) {
    val elixirPhase: ElixirPhase
        get() = when {
            tripleElixir -> ElixirPhase.TRIPLE
            doubleElixir -> ElixirPhase.DOUBLE
            else -> ElixirPhase.SINGLE
        }
}

enum class ElixirPhase { SINGLE, DOUBLE, TRIPLE }

enum class Threat {
    NONE, TANK, SWARM, AIR, WIN_CONDITION, HIGH_DANGER
}

data class AIAction(
    val card: Card?,
    val reason: String,
    val priority: Int,
    val recommendedPlacement: String
)
