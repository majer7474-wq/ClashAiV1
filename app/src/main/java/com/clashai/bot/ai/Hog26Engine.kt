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
        if (state.elixirObservationAgeMs > 1500L) return wait("Čekam novo očitanje eliksira")
        if (!state.elixirConfidence.isFinite() || state.elixirConfidence < 0.85f) {
            return wait("Očitavanje eliksira nije dovoljno pouzdano")
        }
        if (state.hand.size != 4 || state.handConfidence < 0.85f) {
            return wait("Čekam pouzdano prepoznavanje sve 4 karte")
        }

        val affordable = state.hand.filter { it.cost <= state.elixir }
        if (affordable.isEmpty()) return wait("Čekam eliksir")

        when (state.enemyThreat) {
            Threat.AIR -> return when {
                Card.MUSKETEER in affordable -> action(Card.MUSKETEER, "Protuzračna obrana", 100, "Iza vlastite kule")
                Card.FIREBALL in affordable -> action(Card.FIREBALL, "Protuzračna pomoć", 85, "Grupa protivnika")
                else -> wait("Čuvanje eliksira za protuzračnu obranu")
            }
            Threat.SWARM -> return when {
                Card.LOG in affordable -> action(Card.LOG, "Obrana protiv swarm jedinica", 100, "Putanja protivnika")
                Card.FIREBALL in affordable -> action(Card.FIREBALL, "Čišćenje swarm jedinica", 85, "Grupa protivnika")
                Card.ICE_SPIRIT in affordable -> action(Card.ICE_SPIRIT, "Privremeno zaustavljanje swarma", 65, "Putanja protivnika")
                else -> wait("Čekam kartu za swarm obranu")
            }
            Threat.TANK -> return when {
                Card.CANNON in affordable -> action(Card.CANNON, "Povlačenje tanka", 100, "Središte arene")
                Card.MUSKETEER in affordable -> action(Card.MUSKETEER, "Obrana protiv tanka", 90, "Iza vlastite kule")
                Card.ICE_GOLEM in affordable && Card.SKELETONS in affordable ->
                    action(Card.ICE_GOLEM, "Kiting tanka", 75, "Obrambena pozicija")
                else -> wait("Čekam obrambenu kartu protiv tanka")
            }
            Threat.WIN_CONDITION -> return when {
                Card.CANNON in affordable -> action(Card.CANNON, "Zaustavljanje win conditiona", 100, "Središte arene")
                Card.ICE_GOLEM in affordable -> action(Card.ICE_GOLEM, "Kiting win conditiona", 75, "Obrambena pozicija")
                else -> wait("Čekam odgovor na win condition")
            }
            Threat.HIGH_DANGER -> return chooseDefense(affordable, "Visoka prijetnja")
            Threat.NONE -> Unit
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
