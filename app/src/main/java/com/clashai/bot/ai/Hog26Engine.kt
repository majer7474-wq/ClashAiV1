package com.clashai.bot.ai

class Hog26Engine {

    private var lastPlayed: Card? = null
    private val playedCards = mutableListOf<Card>()

    fun reset() {
        lastPlayed = null
        playedCards.clear()
    }

    fun recordPlay(card: Card) {
        lastPlayed = card
        playedCards.add(card)
    }

    fun getCycleProgress(): Int = playedCards.size % 8

    fun chooseAction(state: BattleState): AIAction {
        if (state.observationAgeMs > 1500L) return wait("Zastarjela slika")
        if (state.elixirObservationAgeMs > 2500L) return wait("Čekam novo očitanje eliksira")
        if (!state.elixirConfidence.isFinite() || state.elixirConfidence < 0.85f) {
            return wait("Očitavanje eliksira nije dovoljno pouzdano")
        }
        if (state.hand.size != 4 || state.handConfidence < 0.85f) {
            return wait("Čekam pouzdano prepoznavanje sve 4 karte")
        }

        val affordable = state.hand.filter { it.cost <= state.elixir }
        if (affordable.isEmpty()) return wait("Čekam eliksir")

        if (state.enemyThreat == Threat.HIGH_DANGER) {
            return chooseDefense(affordable, "Visoka prijetnja")
        }
        if (state.enemyThreat == Threat.SWARM && Card.LOG in affordable) {
            return action(Card.LOG, "Obrana protiv swarm jedinica", 100, "Putanja protivnika")
        }
        if (state.enemyThreat == Threat.TANK) {
            if (Card.CANNON in affordable) return action(Card.CANNON, "Povlačenje tanka", 95, "Središte arene")
            if (Card.MUSKETEER in affordable) return action(Card.MUSKETEER, "Obrana protiv tanka", 90, "Iza vlastite kule")
        }
        if (state.enemyThreat == Threat.AIR && Card.MUSKETEER in affordable) {
            return action(Card.MUSKETEER, "Protuzračna obrana", 95, "Iza vlastite kule")
        }
        if (state.enemyThreat == Threat.WIN_CONDITION && Card.CANNON in affordable) {
            return action(Card.CANNON, "Zaustavljanje win conditiona", 90, "Središte arene")
        }

        // Main Hog 2.6 win condition. Keep one elixir in reserve when possible.
        if (Card.HOG_RIDER in affordable && state.elixir >= 5f) {
            return action(Card.HOG_RIDER, "Hog Rider napad", 90, "Most")
        }

        // Cycle only when Hog is not currently in hand. Never throw away a
        // defensive card while Hog is waiting to become affordable.
        if (Card.HOG_RIDER !in state.hand) {
            val cycle = listOf(Card.ICE_SPIRIT, Card.SKELETONS, Card.ICE_GOLEM)
                .firstOrNull { it in affordable && state.elixir >= it.cost + 2f }
            if (cycle != null) {
                return action(cycle, "Cikliranje prema Hog Rideru", 55, "Sigurna pozicija")
            }
        }

        if (Card.FIREBALL in affordable && state.enemyThreat == Threat.SWARM) {
            return action(Card.FIREBALL, "Fireball protiv grupiranih jedinica", 70, "Grupa protivnika")
        }

        return wait("Čuvanje eliksira")
    }

    private fun chooseDefense(cards: List<Card>, reason: String): AIAction {
        val priority = listOf(
            Card.CANNON, Card.MUSKETEER, Card.ICE_GOLEM,
            Card.SKELETONS, Card.LOG, Card.ICE_SPIRIT, Card.FIREBALL
        )
        val selected = priority.firstOrNull { it in cards }
        return if (selected != null) action(selected, reason, 100, "Obrambena pozicija")
        else wait("Nema odgovarajuće obrambene karte")
    }

    private fun action(card: Card, reason: String, priority: Int, placement: String) =
        AIAction(card, reason, priority, placement)

    private fun wait(reason: String) = AIAction(null, reason, 0, "Bez poteza")
}
