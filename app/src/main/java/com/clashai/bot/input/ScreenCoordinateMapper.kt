package com.clashai.bot.input

import kotlin.math.roundToInt

/** Maps normalized arena positions to the full physical display coordinate space. */
object ScreenCoordinateMapper {
    data class Point(val x: Int, val y: Int)

    fun normalizedToPixel(x: Float, y: Float, width: Int, height: Int): Point {
        require(width > 0 && height > 0) { "Display dimensions must be positive" }
        val safeX = x.coerceIn(0f, 1f)
        val safeY = y.coerceIn(0f, 1f)
        return Point(
            (safeX * (width - 1)).roundToInt(),
            (safeY * (height - 1)).roundToInt()
        )
    }
}
