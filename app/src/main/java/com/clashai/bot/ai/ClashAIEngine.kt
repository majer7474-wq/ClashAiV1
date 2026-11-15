package com.clashai.bot.ai

/**
 * Main ClashAiV1 decision engine.
 *
 * This is the shared decision layer for live observations and the sandbox.
 * It is intentionally not tied to a named deck archetype. It evaluates the
 * current hand, elixir phase, observed threat and available counters.
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
        if (state.hand.isEmpty()) return wait("No recognized cards in hand")
        if (state.elixir !in 0f..10f) return wait("Invalid elixir reading")
        if (state.handConfidence in 0f..0.69f) return wait("Hand confidence too low")
        if (state.observationAgeMs > 1200L) return wait("Battle observation is stale")

        val affordable = state.hand.filter { it.cost <= state.elixir }
        if (affordable.isEmpty()) return wait("Waiting for enough elixir")

        // Defend first: use the cheapest suitable counter where possible.
        when (state.enemyThreat) {
            Threat.HIGH_DANGER -> return chooseBest(
                affordable,
                listOf(Card.CANNON, Card.MUSKETEER, Card.ICE_GOLEM, Card.SKELETONS, Card.LOG, Card.FIREBALL),
                "Emergency defense",
                "DEFENSIVE_CENTER",
                100
            )
            Threat.TANK -> {
                val counter = firstAvailable(affordable, Card.CANNON, Card.MUSKETEER, Card.ICE_GOLEM)
                if (counter != null) return action(counter, "Tank counter", 95,
                    if (counter == Card.CANNON) "DEFENSIVE_CENTER" else "DEFENSIVE_BACK")
            }
            Threat.AIR -> {
                val counter = firstAvailable(affordable, Card.MUSKETEER, Card.FIREBALL)
                if (counter != null) return action(counter, "Air defense", 96, "DEFENSIVE_BACK")
            }
            Threat.SWARM -> {
                val counter = firstAvailable(affordable, Card.LOG, Card.FIREBALL, Card.ICE_SPIRIT)
                if (counter != null) return action(counter, "Swarm control", 96, "OPPONENT_LANE")
            }
            Threat.WIN_CONDITION -> {
                val counter = firstAvailable(affordable, Card.CANNON, Card.ICE_GOLEM, Card.SKELETONS, Card.MUSKETEER)
                if (counter != null) return action(counter, "Stop enemy win condition", 98,
                    if (counter == Card.CANNON) "DEFENSIVE_CENTER" else "DEFENSIVE_BACK")
            }
            Threat.NONE -> Unit
        }

        // Spend a spell only when there is a meaningful target signal.
        if (state.enemyThreat != Threat.NONE && Card.FIREBALL in affordable) {
            return action(Card.FIREBALL, "Spell value against detected threat", 72, "OPPONENT_LANE")
        }

        // Apply pressure with the available win-condition card, but preserve
        // enough elixir to avoid emptying the bar during single elixir.
        if (Card.HOG_RIDER in affordable) {
            val reserve = when (state.elixirPhase) {
                ElixirPhase.SINGLE -> 5f
                ElixirPhase.DOUBLE -> 4f
                ElixirPhase.TRIPLE -> 3f
            }
            if (state.elixir >= reserve) {
                return action(Card.HOG_RIDER, "Available win-condition push", 82, "OPPONENT_LANE")
            }
        }

        // Cycle only when elixir is high and the played card is not already
        // being repeated unnecessarily.
        val cycle = listOf(Card.ICE_SPIRIT, Card.SKELETONS, Card.ICE_GOLEM)
            .firstOrNull { it in affordable && recentPlays.takeLast(2).count { played -> played == it } == 0 }
        if (cycle != null && state.elixir >= 8f) {
            return action(cycle, "Low-cost cycle at high elixir", 48, "SAFE_CYCLE")
        }

        return wait("No high-value play; preserve elixir")
    }

    private fun chooseBest(
        available: List<Card>,
        preference: List<Card>,
        reason: String,
        placement: String,
        priority: Int
    ): AIAction {
        val selected = preference.firstOrNull { it in available }
        return if (selected == null) wait("No suitable counter in current hand")
        else action(selected, reason, priority, placement)
    }

    private fun firstAvailable(available: List<Card>, vararg preference: Card): Card? =
        preference.firstOrNull { it in available }

    private fun action(card: Card, reason: String, priority: Int, placement: String) =
        AIAction(card, reason, priority, placement)

    private fun wait(reason: String) = AIAction(null, reason, 0, "NONE")
}
