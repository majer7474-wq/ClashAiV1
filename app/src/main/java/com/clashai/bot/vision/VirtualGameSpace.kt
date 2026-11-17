package com.clashai.bot.vision

import kotlin.math.roundToInt

/**
 * Clash Royale input is described in one fixed virtual portrait canvas.
 * Device resolution is used only at the final OS touch-injection boundary.
 */
object VirtualGameSpace {
    const val WIDTH = 1080f
    const val HEIGHT = 2400f

    data class Point(val x: Int, val y: Int)

    fun toScreen(x: Float, y: Float, capturedWidth: Int, capturedHeight: Int): Point {
        require(capturedWidth > 0 && capturedHeight > 0)
        val safeX = x.coerceIn(0f, WIDTH)
        val safeY = y.coerceIn(0f, HEIGHT)
        return Point(
            (safeX * capturedWidth / WIDTH).roundToInt().coerceIn(0, capturedWidth - 1),
            (safeY * capturedHeight / HEIGHT).roundToInt().coerceIn(0, capturedHeight - 1)
        )
    }

    fun handSlot(index: Int): Point {
        require(index in 0..3)
        val x = floatArrayOf(180f, 420f, 660f, 900f)[index]
        return Point(x.roundToInt(), 2160)
    }

    fun placementForHog(): Point = Point(540, 1320)

    fun placementForCycle(): Point = Point(540, 1900)
}
