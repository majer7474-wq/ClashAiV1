package com.clashai.bot.vision

import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Fixed Clash Royale virtual portrait canvas. The game is fitted without
 * stretching; any unused display area is treated as letterboxing.
 * Device pixels are consulted only when translating a virtual touch to Android.
 */
object VirtualGameSpace {
    const val WIDTH = 1080f
    const val HEIGHT = 2400f

    data class Point(val x: Int, val y: Int)

    fun toScreen(x: Float, y: Float, capturedWidth: Int, capturedHeight: Int): Point {
        require(capturedWidth > 0 && capturedHeight > 0)
        val scale = min(capturedWidth / WIDTH, capturedHeight / HEIGHT)
        val drawnWidth = WIDTH * scale
        val drawnHeight = HEIGHT * scale
        val offsetX = (capturedWidth - drawnWidth) / 2f
        val offsetY = (capturedHeight - drawnHeight) / 2f
        val safeX = x.coerceIn(0f, WIDTH)
        val safeY = y.coerceIn(0f, HEIGHT)
        return Point(
            (offsetX + safeX * scale).roundToInt().coerceIn(0, capturedWidth - 1),
            (offsetY + safeY * scale).roundToInt().coerceIn(0, capturedHeight - 1)
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
