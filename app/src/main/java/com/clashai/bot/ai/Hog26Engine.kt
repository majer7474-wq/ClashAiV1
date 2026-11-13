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

    fun getCycleProgress(): Int {
        return playedCards.size % 8
    }

    fun chooseAction(state: BattleState): AIAction {

        if (state.hand.isEmpty()) {
            return wait("Nema dostupnih karata")
        }

        val affordable = state.hand.filter {
            it.cost <= state.elixir
        }

        if (affordable.isEmpty()) {
            return wait("Čekanje eliksira")
        }

        // EMERGENCY DEFENSE
        if (state.enemyThreat == Threat.HIGH_DANGER) {
            return chooseDefense(
                affordable,
                "Visoka prijetnja"
            )
        }

        // SWARM DEFENSE
        if (state.enemyThreat == Threat.SWARM &&
            Card.LOG in affordable
        ) {
            return action(
                Card.LOG,
                "Obrana protiv swarm jedinica",
                100,
                "Putanja protivničkih jedinica"
            )
        }

        // TANK DEFENSE
        if (state.enemyThreat == Threat.TANK) {
            if (Card.CANNON in affordable) {
                return action(
                    Card.CANNON,
                    "Povlačenje tanka u sredinu",
                    95,
                    "Središte arene"
                )
            }

            if (Card.MUSKETEER in affordable) {
                return action(
                    Card.MUSKETEER,
                    "Podrška obrani protiv tanka",
                    90,
                    "Iza vlastite kule"
                )
            }
        }

        // AIR DEFENSE
        if (state.enemyThreat == Threat.AIR &&
            Card.MUSKETEER in affordable
        ) {
            return action(
                Card.MUSKETEER,
                "Protuzračna obrana",
                95,
                "Iza vlastite kule"
            )
        }

        // WIN CONDITION DEFENSE
        if (state.enemyThreat == Threat.WIN_CONDITION &&
            Card.CANNON in affordable
        ) {
            return action(
                Card.CANNON,
                "Zaustavljanje protivničkog win conditiona",
                90,
                "Središte arene"
            )
        }

        // HOG RIDER OFFENSE
        if (Card.HOG_RIDER in affordable) {

            val minimumElixir = if (state.doubleElixir) 4f else 5f

            if (state.elixir >= minimumElixir) {
                return action(
                    Card.HOG_RIDER,
                    "Hog Rider napad",
                    80,
                    "Most prema protivničkoj kuli"
                )
            }
        }

        // SUPPORT AND CYCLE
        val cycleCard = listOf(
            Card.ICE_SPIRIT,
            Card.SKELETONS,
            Card.ICE_GOLEM
        ).firstOrNull { it in affordable }

        if (cycleCard != null && state.elixir >= 8f) {
            return action(
                cycleCard,
                "Cikliranje karata",
                50,
                "Sigurna pozicija"
            )
        }

        // SPELL VALUE
        if (Card.FIREBALL in affordable &&
            state.enemyThreat == Threat.SWARM
        ) {
            return action(
                Card.FIREBALL,
                "Fireball protiv grupiranih jedinica",
                70,
                "Grupa protivničkih jedinica"
            )
        }

        return wait("Čuvanje eliksira i čekanje boljeg poteza")
    }

    private fun chooseDefense(
        cards: List<Card>,
        reason: String
    ): AIAction {
        val priority = listOf(
            Card.CANNON,
            Card.MUSKETEER,
            Card.ICE_GOLEM,
            Card.SKELETONS,
            Card.LOG,
            Card.ICE_SPIRIT,
            Card.FIREBALL
        )

        val selected = priority.firstOrNull {
            it in cards
        }

        return if (selected != null) {
            action(
                selected,
                reason,
                100,
                "Obrambena pozicija"
            )
        } else {
            wait("Nema odgovarajuće obrambene karte")
        }
    }

    private fun action(
        card: Card,
        reason: String,
        priority: Int,
        placement: String
    ) = AIAction(
        card,
        reason,
        priority,
        placement
    )

    private fun wait(reason: String) = AIAction(
        null,
        reason,
        0,
        "Bez poteza"
    )
}
