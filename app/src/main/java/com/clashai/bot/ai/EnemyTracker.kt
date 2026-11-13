package com.clashai.bot.ai

class EnemyTracker {

    private val discoveredCards = mutableSetOf<Card>()

    fun recordEnemyCard(card: Card) {
        discoveredCards.add(card)
    }

    fun getKnownCards(): List<Card> {
        return discoveredCards.toList()
    }

    fun getRemainingUnknownSlots(): Int {
        return (8 - discoveredCards.size).coerceAtLeast(0)
    }

    fun reset() {
        discoveredCards.clear()
    }
}
