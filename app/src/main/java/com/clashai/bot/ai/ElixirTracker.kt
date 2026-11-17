package com.clashai.bot.ai

import android.os.SystemClock
import kotlin.math.max
import kotlin.math.min

class ElixirTracker(
    private val clock: () -> Long = { SystemClock.elapsedRealtime() }
) {
    private var currentElixir = 5f
    private var lastUpdateMs = clock()
    private var lastVisualMs: Long? = null
    private var regenerationPerSecond = 1f / 2.8f

    @Synchronized
    fun update(value: Float) {
        tick()
        currentElixir = value.coerceIn(0f, 10f)
        lastUpdateMs = clock()
        lastVisualMs = lastUpdateMs
    }

    @Synchronized
    fun setPhase(double: Boolean, triple: Boolean = false) {
        tick()
        regenerationPerSecond = when {
            triple -> 1f / 0.93f
            double -> 1f / 1.4f
            else -> 1f / 2.8f
        }
    }

    @Synchronized
    private fun tick() {
        val now = clock()
        val elapsed = max(0L, now - lastUpdateMs) / 1000f
        currentElixir = min(10f, currentElixir + elapsed * regenerationPerSecond)
        lastUpdateMs = now
    }

    @Synchronized
    fun getElixir(): Float {
        tick()
        return currentElixir
    }

    @Synchronized
    fun getVisualAgeMs(): Long {
        val visualTime = lastVisualMs ?: return Long.MAX_VALUE
        return max(0L, clock() - visualTime)
    }

    @Synchronized
    fun canPlay(card: Card): Boolean = getElixir() >= card.cost

    @Synchronized
    fun spend(card: Card): Boolean {
        tick()
        if (currentElixir + 0.001f < card.cost) return false
        currentElixir = (currentElixir - card.cost).coerceAtLeast(0f)
        return true
    }

    @Synchronized
    fun reset() {
        currentElixir = 5f
        lastUpdateMs = clock()
        lastVisualMs = null
        regenerationPerSecond = 1f / 2.8f
    }
}
