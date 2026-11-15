package com.clashai.bot.ai

/**
 * Confirms a Shizuku swipe against subsequent, complete live hand observations.
 * A successful shell command is not treated as proof that the game accepted a card.
 */
class PlayConfirmationTracker(private val timeoutMs: Long = 2500L) {
    sealed class Update {
        object Idle : Update()
        data class Waiting(val card: Card) : Update()
        data class Confirmed(val card: Card) : Update()
        data class Failed(val card: Card) : Update()
    }

    private data class Pending(val card: Card, val startedAtMs: Long)
    private var pending: Pending? = null

    fun begin(card: Card, nowMs: Long) {
        pending = Pending(card, nowMs)
    }

    fun update(hand: List<Card>, handComplete: Boolean, nowMs: Long): Update {
        val current = pending ?: return Update.Idle
        if (handComplete && current.card !in hand) {
            pending = null
            return Update.Confirmed(current.card)
        }
        if (nowMs - current.startedAtMs >= timeoutMs) {
            pending = null
            return Update.Failed(current.card)
        }
        return Update.Waiting(current.card)
    }

    fun reset() {
        pending = null
    }
}
