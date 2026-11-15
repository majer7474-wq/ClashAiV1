package com.clashai.bot.ai

/**
 * Deterministic, aggressive Hog 2.6 policy shared by live analysis and sandbox.
 * It is not a trained model: it applies explicit matchup, elixir and placement
 * rules to the detected game state. It never invents a card outside the hand.
 */
class ClashAIEngine {
    private val recentPlays = ArrayDeque<Card>()

    fun reset() {
        recentPlays.clear()
    }

    fun recordPlay(card: Card) {
        recentPlays.addLast(card)
        while (recentPlays.size > 8) recentPlays.removeFirst()
    }

    fun chooseAction(state: BattleState): AIAction {
        if (state.hand.size != 4 || state.hand.distinct().size != 4)
            return wait("Waiting for four unique recognized hand cards")
        if (state.elixir !in 0f..10f) return wait("Invalid elixir reading")
        if (state.handConfidence < 0.70f) return wait("Hand confidence too low")
        if (state.observationAgeMs > 1200L) return wait("Battle observation is stale")

        val affordable = state.hand.filter { it.cost <= state.elixir }
        if (affordable.isEmpty()) return wait("Waiting for enough elixir")

        val enemies = state.enemyUnits
            .filter { it.confidence >= 0.50f && it.y < 0.52f }
        val hasAir = enemies.any { it.label in AIR_UNITS }
        val hasHeavyGround = enemies.any { it.label in HEAVY_GROUND }
        val enemyCount = enemies.size

        // Defend the detected threat first; do not spend spells without a target.
        if (state.enemyThreat != Threat.NONE || enemies.isNotEmpty()) {
            if (hasAir || state.enemyThreat == Threat.AIR) {
                if (Card.MUSKETEER in affordable)
                    return musketeerDefense(enemies, "Air threat: Musketeer is the primary Hog 2.6 answer")
                if (Card.FIREBALL in affordable && (enemyCount >= 2 || hasHeavyGround))
                    return spellOnEnemyGroup(enemies, "Fireball emergency air support")
                return wait("Air threat detected; waiting for Musketeer or a valuable Fireball")
            }

            if (hasHeavyGround || state.enemyThreat == Threat.TANK ||
                state.enemyThreat == Threat.HIGH_DANGER ||
                state.enemyThreat == Threat.WIN_CONDITION
            ) {
                if (Card.CANNON in affordable)
                    return cannonDefense(enemies, "Cannon pull against ground win condition/tank")
                if (Card.ICE_GOLEM in affordable)
                    return kiteWithIceGolem(enemies, "Ice Golem kite: pull ground unit away from tower")
                if (Card.MUSKETEER in affordable)
                    return musketeerDefense(enemies, "Musketeer support against ground push")
                if (Card.SKELETONS in affordable)
                    return skeletonDefense(enemies, "Skeleton distraction")
                return wait("Ground push detected; waiting for a defensive counter")
            }

            if (state.enemyThreat == Threat.SWARM) {
                if (Card.LOG in affordable)
                    return logOnEnemyGroup(enemies, "Log clears detected swarm")
                if (Card.ICE_SPIRIT in affordable)
                    return action(Card.ICE_SPIRIT, "Ice Spirit freezes swarm for tower support", 82,
                        "DEFENSIVE_CENTER", 0.50f, 0.61f)
                if (Card.FIREBALL in affordable && enemyCount >= 3)
                    return spellOnEnemyGroup(enemies, "Fireball on clustered swarm")
                return wait("Swarm detected; waiting for Log or a useful spell window")
            }

            if (Card.CANNON in affordable)
                return cannonDefense(enemies, "Cannon defensive pull")
            if (Card.MUSKETEER in affordable)
                return musketeerDefense(enemies, "Musketeer defensive support")
        }

        // RAGE pressure: Hog is the win condition. No random lane toggling.
        if (Card.HOG_RIDER in affordable) {
            val reserve = when (state.elixirPhase) {
                ElixirPhase.SINGLE -> 4.0f
                ElixirPhase.DOUBLE -> 3.5f
                ElixirPhase.TRIPLE -> 3.0f
            }
            if (state.elixir >= reserve) {
                val lane = selectPressureLane(enemies)
                return action(Card.HOG_RIDER, "RAGE: immediate Hog pressure in the weaker lane",
                    90, "OPPONENT_LANE", lane, 0.515f)
            }
        }

        // Keep the Hog 2.6 cycle moving instead of waiting until 9 elixir.
        // Preserve a small reserve so a cheap cycle card does not empty the bar
        // immediately before a Hog/defensive response becomes available.
        val cycle = listOf(Card.ICE_SPIRIT, Card.SKELETONS, Card.ICE_GOLEM)
            .firstOrNull { candidate ->
                candidate in affordable &&
                    state.elixir >= candidate.cost + CYCLE_RESERVE_ELIXIR &&
                    recentPlays.takeLast(2).count { it == candidate } == 0
            }
        if (cycle != null) {
            val y = if (cycle == Card.ICE_GOLEM) 0.78f else 0.84f
            return action(cycle, "Hog 2.6 cycle: rotate cheaply while no immediate defense is needed",
                45, "SAFE_CYCLE", 0.50f, y)
        }

        return wait("No safe high-value action; preserve elixir for Hog or defense")
    }

    private fun cannonDefense(enemies: List<ObservedEnemy>, reason: String): AIAction {
        val x = if (enemies.isEmpty()) 0.50f else laneCenter(enemies)
        return action(Card.CANNON, reason, 100, "DEFENSIVE_CENTER",
            0.50f + (x - 0.50f).coerceIn(-0.08f, 0.08f), 0.645f)
    }

    private fun musketeerDefense(enemies: List<ObservedEnemy>, reason: String): AIAction {
        val x = if (enemies.isEmpty()) 0.50f else laneCenter(enemies).coerceIn(0.22f, 0.78f)
        return action(Card.MUSKETEER, reason, 98, "DEFENSIVE_BACK", x, 0.735f)
    }

    private fun kiteWithIceGolem(enemies: List<ObservedEnemy>, reason: String): AIAction {
        val lane = laneCenter(enemies)
        val opposite = if (lane < 0.50f) 0.68f else 0.32f
        return action(Card.ICE_GOLEM, reason, 94, "KITE", opposite, 0.635f)
    }

    private fun skeletonDefense(enemies: List<ObservedEnemy>, reason: String): AIAction {
        val x = if (enemies.isEmpty()) 0.50f else laneCenter(enemies).coerceIn(0.18f, 0.82f)
        return action(Card.SKELETONS, reason, 88, "SURROUND", x, 0.605f)
    }

    private fun logOnEnemyGroup(enemies: List<ObservedEnemy>, reason: String): AIAction {
        val target = groupCenter(enemies)
        return action(Card.LOG, reason, 96, "ENEMY_GROUP", target.first, target.second)
    }

    private fun spellOnEnemyGroup(enemies: List<ObservedEnemy>, reason: String): AIAction {
        val target = groupCenter(enemies)
        return action(Card.FIREBALL, reason, 91, "ENEMY_GROUP", target.first, target.second)
    }

    private fun groupCenter(enemies: List<ObservedEnemy>): Pair<Float, Float> {
        if (enemies.isEmpty()) return 0.50f to 0.48f
        return enemies.map { it.x }.average().toFloat().coerceIn(0.12f, 0.88f) to
            enemies.map { it.y }.average().toFloat().coerceIn(0.25f, 0.49f)
    }

    private fun laneCenter(enemies: List<ObservedEnemy>): Float =
        enemies.map { it.x }.average().toFloat().coerceIn(0.18f, 0.82f)

    private fun selectPressureLane(enemies: List<ObservedEnemy>): Float {
        if (enemies.isEmpty()) return 0.70f
        val left = enemies.count { it.x < 0.50f }
        val right = enemies.size - left
        return if (left <= right) 0.30f else 0.70f
    }

    private fun action(
        card: Card,
        reason: String,
        priority: Int,
        placement: String,
        x: Float,
        y: Float
    ) = AIAction(card, reason, priority, placement, x.coerceIn(0.08f, 0.92f), y.coerceIn(0.20f, 0.90f))

    private fun wait(reason: String) = AIAction(null, reason, 0, "NONE")

    companion object {
        private const val CYCLE_RESERVE_ELIXIR = 1.5f
        private val AIR_UNITS = setOf(
            "balloon", "baby-dragon", "lava-hound", "inferno-dragon", "phoenix-big",
            "phoenix-small", "minion", "minion-giant", "bat", "electro-dragon",
            "mega-minion", "flying-machine", "lava-pup"
        )
        private val HEAVY_GROUND = setOf(
            "giant", "golem", "pekka", "mega-knight", "electro-giant", "royal-giant",
            "goblin-giant", "ram-rider", "battle-ram", "hog-rider", "hog", "miner"
        )
    }
}
