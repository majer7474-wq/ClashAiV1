package com.clashai.bot.vision

import android.graphics.Bitmap
import android.graphics.Color

data class ImageMetrics(
    val averageRed: Float,
    val averageGreen: Float,
    val averageBlue: Float,
    val brightness: Float
)

object ImageMetricsCalculator {

    fun calculate(bitmap: Bitmap): ImageMetrics {
        val sampleX = 32
        val sampleY = 32

        var red = 0L
        var green = 0L
        var blue = 0L
        var count = 0

        for (y in 0 until bitmap.height step sampleY) {
            for (x in 0 until bitmap.width step sampleX) {
                val pixel = bitmap.getPixel(x, y)

                red += Color.red(pixel)
                green += Color.green(pixel)
                blue += Color.blue(pixel)
                count++
            }
        }

        if (count == 0) {
            return ImageMetrics(0f, 0f, 0f, 0f)
        }

        val r = red.toFloat() / count
        val g = green.toFloat() / count
        val b = blue.toFloat() / count

        return ImageMetrics(
            averageRed = r,
            averageGreen = g,
            averageBlue = b,
            brightness = (r + g + b) / 3f
        )
    }
}
