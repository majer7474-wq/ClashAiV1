package com.clashai.bot.vision

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Read-only pixel meter for a calibrated elixir-bar rectangle.
 * The caller supplies the complete track bounds; no screen coordinates are guessed.
 */
data class ElixirBarReading(
    val fill: Float?,
    val confidence: Float,
    val activeColumns: Int,
    val sampledColumns: Int
)

class ElixirBarDetector {
    fun detect(bitmap: Bitmap, trackBounds: Rect): ElixirBarReading {
        if (bitmap.isRecycled || trackBounds.width() < 8 || trackBounds.height() < 3) {
            return ElixirBarReading(null, 0f, 0, 0)
        }

        val left = trackBounds.left.coerceIn(0, bitmap.width)
        val right = trackBounds.right.coerceIn(0, bitmap.width)
        val top = trackBounds.top.coerceIn(0, bitmap.height)
        val bottom = trackBounds.bottom.coerceIn(0, bitmap.height)
        val width = right - left
        val height = bottom - top
        if (width < 8 || height < 3) return ElixirBarReading(null, 0f, 0, 0)

        val sampleTop = top + (height * 0.20f).roundToInt()
        val sampleBottom = max(sampleTop + 1, bottom - (height * 0.20f).roundToInt())
        val hsv = FloatArray(3)
        val active = BooleanArray(width)
        var activePixels = 0
        var sampledPixels = 0

        for (x in 0 until width) {
            var columnHits = 0
            for (y in sampleTop until sampleBottom) {
                Color.colorToHSV(bitmap.getPixel(left + x, y), hsv)
                sampledPixels++
                if (hsv[0] in 250f..335f && hsv[1] >= 0.35f && hsv[2] >= 0.25f) {
                    columnHits++
                    activePixels++
                }
            }
            val rows = sampleBottom - sampleTop
            active[x] = columnHits.toFloat() / rows >= 0.5f
        }

        var filled = 0
        while (filled < width && active[filled]) filled++
        val confidence = if (sampledPixels == 0) 0f
            else (activePixels.toFloat() / sampledPixels).coerceIn(0f, 1f)

        return ElixirBarReading(
            fill = (filled.toFloat() / width).coerceIn(0f, 1f),
            confidence = confidence,
            activeColumns = active.count { it },
            sampledColumns = width
        )
    }
}
