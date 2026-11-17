package com.clashai.bot.vision

import android.graphics.Bitmap
import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min

/**
 * Finds the elixir-track anchor and derives hand-card candidate regions.
 * Coordinates are normalized to the current captured frame.
 * This locates regions only; it does not identify card artwork.
 */
class AdaptiveHandLocator {
    data class Result(val regions: List<RectF>, val confidence: Float, val anchorY: Float)

    fun locate(bitmap: Bitmap): Result {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 100 || h < 100) return Result(emptyList(), 0f, 0f)

        val yStart = (h * 0.72f).toInt()
        val xStart = (w * 0.08f).toInt()
        val xEnd = (w * 0.96f).toInt().coerceAtMost(w)
        val stepX = max(1, w / 500)
        val stepY = max(1, h / 500)
        var bestY = -1
        var bestLeft = -1
        var bestRight = -1
        var bestSpan = 0

        var y = yStart
        while (y < h) {
            var first = -1
            var last = -1
            var colored = 0
            var x = xStart
            while (x < xEnd) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 255
                val g = (pixel shr 8) and 255
                val b = pixel and 255
                val magenta = r > 125 && b > 135 && g < 125 && r - g > 45 && b - g > 45
                val trackBlue = b > 85 && b - r > 35 && g < 125 && r < 130
                if (magenta || trackBlue) {
                    if (first < 0) first = x
                    last = x
                    colored++
                }
                x += stepX
            }
            val span = last - first
            if (first >= 0 && span > w * 0.30f && colored > span / stepX * 0.35f && span > bestSpan) {
                bestY = y
                bestLeft = first
                bestRight = last
                bestSpan = span
            }
            y += stepY
        }

        if (bestY < 0) return Result(emptyList(), 0f, 0f)

        val left = bestLeft.toFloat() / w
        val right = bestRight.toFloat() / w
        val anchorY = bestY.toFloat() / h
        val usableLeft = left + (right - left) * 0.055f
        val usableRight = right - (right - left) * 0.055f
        val slotWidth = (usableRight - usableLeft) / 4f
        val cardHeight = min(0.205f, max(0.105f, slotWidth * (h.toFloat() / w) * 0.78f))
        val cardBottom = (anchorY - 0.012f).coerceIn(cardHeight, 1f)
        val cardTop = (cardBottom - cardHeight).coerceAtLeast(0f)

        val regions = (0 until 4).map { i ->
            val l = usableLeft + i * slotWidth + slotWidth * 0.08f
            val r = usableLeft + (i + 1) * slotWidth - slotWidth * 0.08f
            RectF(l, cardTop, r, cardBottom)
        }
        val confidence = ((bestSpan.toFloat() / w) * 1.15f).coerceIn(0f, 0.70f)
        return Result(regions, confidence, anchorY)
    }
}
