package com.clashai.bot.vision

import com.clashai.bot.ai.Threat

data class ThreatResult(
    val threat: Threat,
    val targetX: Float?,
    val targetY: Float?,
    val enemyUnits: List<ArenaUnit>
)

object EnemyThreatAnalyzer {
    private val tanks = setOf("giant", "golem", "electro-giant", "royal-giant", "lava-hound",
        "mega-knight", "pekka", "elixir-golem", "giant-skeleton", "goblin-giant", "rune-giant")
    private val air = setOf("balloon", "lava-hound", "minion", "minion-horde", "mega-minion",
        "baby-dragon", "inferno-dragon", "phoenix", "skeleton-dragon", "flying-machine")
    private val swarms = setOf("skeleton", "goblin", "spear-goblin", "bat", "minion", "barbarian",
        "rascal", "guard", "guards", "skeleton-army", "goblin-gang")
    private val winConditions = setOf("hog-rider", "ram-rider", "battle-ram", "royal-hog",
        "wall-breaker", "goblin-barrel", "graveyard", "balloon", "miner", "mortar")

    fun assess(units: List<ArenaUnit>): ThreatResult {
        // Dataset arena crop is top-to-bottom: opponent half first, our half second.
        // Keep detections in the opponent half to avoid treating our own defenders as threats.
        val enemies = units.filter { unit ->
            unit.centerY < 0.57f &&
                !unit.name.contains("tower") &&
                unit.name !in setOf("clock", "elixir")
        }
        if (enemies.isEmpty()) return ThreatResult(Threat.NONE, null, null, emptyList())

        val cluster = enemies.filter { it.centerY > 0.34f }
        val relevant = if (cluster.isNotEmpty()) cluster else enemies
        val weightedX = relevant.sumOf { (it.centerX * it.confidence).toDouble() }.toFloat() /
            relevant.sumOf { it.confidence.toDouble() }.toFloat().coerceAtLeast(0.01f)
        val weightedY = relevant.maxOf { it.centerY }
        val names = relevant.map { it.name.substringBefore("-evolution").substringBefore("-hero") }
        val count = relevant.size

        val threat = when {
            weightedY > 0.47f && count >= 2 -> Threat.HIGH_DANGER
            names.any { it in tanks } -> Threat.TANK
            names.any { it in air } -> Threat.AIR
            count >= 3 || names.count { it in swarms } >= 2 -> Threat.SWARM
            names.any { it in winConditions } -> Threat.WIN_CONDITION
            weightedY > 0.40f -> Threat.HIGH_DANGER
            else -> Threat.NONE
        }
        return ThreatResult(threat, weightedX, weightedY, relevant)
    }
}
