package com.clashai.bot.vision

import kotlin.math.roundToInt

/**
 * One portrait Clash Royale playfield mapped into the full captured display.
 * The game remains portrait on tablets; in landscape it is centered with
 * horizontal letterboxing. Every subsystem must use this same transform.
 */
class GameViewport private constructor(
    val frameWidth: Int,
    val frameHeight: Int,
    private val scale: Float,
    private val offsetX: Float,
    private val offsetY: Float
) {
    data class Point(val x: Int, val y: Int)

    fun mapX(referenceX: Float): Int? {
        if (referenceX !in 0f..REFERENCE_WIDTH) return null
        return (offsetX + referenceX * scale).roundToInt()
            .takeIf { it in 0 until frameWidth }
    }

    fun mapY(referenceY: Float): Int? {
        if (referenceY !in 0f..REFERENCE_HEIGHT) return null
        return (offsetY + referenceY * scale).roundToInt()
            .takeIf { it in 0 until frameHeight }
    }

    fun mapPoint(referenceX: Float, referenceY: Float): Point? {
        val x = mapX(referenceX) ?: return null
        val y = mapY(referenceY) ?: return null
        return Point(x, y)
    }

    companion object {
        const val REFERENCE_WIDTH = 1080f
        const val REFERENCE_HEIGHT = 2400f

        fun fromFrame(width: Int, height: Int): GameViewport? {
            if (width < 300 || height < 500) return null
            val scale = minOf(width / REFERENCE_WIDTH, height / REFERENCE_HEIGHT)
            if (!scale.isFinite() || scale <= 0f) return null
            val gameWidth = REFERENCE_WIDTH * scale
            val gameHeight = REFERENCE_HEIGHT * scale
            val offsetX = (width - gameWidth) / 2f
            val offsetY = (height - gameHeight) / 2f
            // Reject geometries where the assumed portrait game viewport
            // cannot be represented inside the captured frame.
            if (offsetX < 0f || offsetY < 0f) return null
            return GameViewport(width, height, scale, offsetX, offsetY)
        }
    }
}
