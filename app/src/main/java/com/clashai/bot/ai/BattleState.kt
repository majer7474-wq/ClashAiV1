package com.clashai.bot.ai

data class BattleState(
    val elixir: Float,
    val hand: List<Card>,
    val enemyCards: List<Card> = emptyList(),
    val enemyThreat: Threat = Threat.NONE,
    val doubleElixir: Boolean = false,
    val timeRemaining: Int = 180,
    val towerAdvantage: Int = 0,
    val enemyHasBuilding: Boolean = false
)

enum class Threat {
    NONE,
    TANK,
    SWARM,
    AIR,
    WIN_CONDITION,
    HIGH_DANGER
}

data class AIAction(
    val card: Card?,
    val reason: String,
    val priority: Int,
    val recommendedPlacement: String
)
