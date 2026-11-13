package com.clashai.bot.ai

class ElixirTracker {

    private var currentElixir = 5f

    fun update(value: Float) {
        currentElixir = value.coerceIn(0f, 10f)
    }

    fun getElixir(): Float = currentElixir

    fun canPlay(card: Card): Boolean {
        return currentElixir >= card.cost
    }

    fun spend(card: Card): Boolean {
        if (!canPlay(card)) return false

        currentElixir -= card.cost
        return true
    }

    fun reset() {
        currentElixir = 5f
    }
}
