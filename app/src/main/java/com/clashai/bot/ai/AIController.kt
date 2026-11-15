package com.clashai.bot.ai

import com.clashai.bot.vision.ScreenObservation

/**
 * ClashAI's standalone decision layer.
 *
 * It deliberately does not depend on Hog26Engine or any code from another project.
 * Until the vision pipeline provides a trustworthy hand and elixir reading, it
 * returns a no-op instead of guessing and sending a random card.
 */
class AIController {

    fun process(observation: ScreenObservation): AIAction {
        val hand = observation.visibleCards.distinct()
        val elixir = observation.elixir

        if (!observation.handComplete || observation.handConfidence < MIN_HAND_CONFIDENCE || hand.size != 4) {
            return noAction("Hand recognition incomplete or uncertain; no card will be played")
        }

        if (elixir == null || elixir !in 0f..10f) {
            return noAction("Elixir reading unavailable; waiting")
        }
        if (System.currentTimeMillis() - observation.capturedAtMs > MAX_OBSERVATION_AGE_MS) {
            return noAction("Live observation is stale; waiting for a fresh frame")
        }

        val affordable = hand.filter { it.cost <= elixir }
        if (affordable.isEmpty()) {
            return noAction("Not enough elixir for a detected card")
        }

        val selected = chooseCard(affordable, observation.enemyThreat)
            ?: return noAction("No safe move identified")

        return AIAction(
            card = selected,
            reason = reasonFor(selected, observation.enemyThreat),
            priority = if (observation.enemyThreat == Threat.HIGH_DANGER) 100 else 50,
            recommendedPlacement = placementFor(selected, observation.enemyThreat)
        )
    }

    private fun chooseCard(available: List<Card>, threat: Threat): Card? {
        val priorities = when (threat) {
            Threat.HIGH_DANGER, Threat.WIN_CONDITION ->
                listOf(Card.CANNON, Card.MUSKETEER, Card.ICE_GOLEM, Card.SKELETONS)
            Threat.TANK ->
                listOf(Card.CANNON, Card.MUSKETEER, Card.ICE_GOLEM)
            Threat.SWARM ->
                listOf(Card.LOG, Card.FIREBALL, Card.SKELETONS)
            Threat.AIR ->
                listOf(Card.MUSKETEER, Card.FIREBALL)
            Threat.NONE ->
                listOf(Card.HOG_RIDER, Card.ICE_SPIRIT, Card.SKELETONS)
        }
        return priorities.firstNotNullOfOrNull { preferred ->
            available.firstOrNull { it == preferred }
        }
    }

    private fun reasonFor(card: Card, threat: Threat): String = when (threat) {
        Threat.NONE -> "No threat detected; low-cost pressure/support"
        Threat.SWARM -> "$card selected as a response to a swarm"
        Threat.AIR -> "$card selected to answer an air threat"
        Threat.TANK -> "$card selected to defend against a tank"
        Threat.WIN_CONDITION -> "$card selected to defend against a win condition"
        Threat.HIGH_DANGER -> "$card selected for urgent defense"
    }

    private fun placementFor(card: Card, threat: Threat): String = when {
        threat != Threat.NONE -> "DEFENSIVE_CENTER"
        card == Card.HOG_RIDER -> "OPPONENT_LANE"
        else -> "OWN_SIDE"
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
