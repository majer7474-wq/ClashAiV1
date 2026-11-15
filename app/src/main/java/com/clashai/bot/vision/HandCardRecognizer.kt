package com.clashai.bot.vision

import android.graphics.Bitmap
import android.graphics.Color
import com.clashai.bot.ai.Card
import kotlin.math.abs
import kotlin.math.max

/**
 * Recognizes the four hand slots against the bundled official-style card art.
 * A match is accepted only with a high score and a clear margin over runner-up.
 * The recognizer intentionally fails closed for cards not yet mapped to the AI
 * card enum; it never silently substitutes a different card.
 */
class HandCardRecognizer(private val catalog: CardTemplateCatalog) {

    data class Result(
        val cards: List<Card>,
        val cardIds: List<String>,
        val confidence: Float,
        val complete: Boolean
    )

    private data class Fingerprint(val gray: FloatArray, val color: FloatArray)
    private data class Match(val id: String, val score: Float)

    private val templateFingerprints: List<Pair<String, Fingerprint>> =
        catalog.all().mapNotNull { template ->
            runCatching { template.id to fingerprint(template.bitmap) }.getOrNull()
        }

    fun recognize(frame: Bitmap): Result {
        if (frame.width < 300 || frame.height < 500 || templateFingerprints.size < 100) {
            return Result(emptyList(), emptyList(), 0f, false)
        }

        // Calibrated to the 960x1536 portrait gameplay screenshot; proportional
        // coordinates also support other device resolutions in the same layout.
        val left = 0.245f
        val right = 0.925f
        val top = 0.825f
        val bottom = 0.965f
        val slotWidth = (right - left) / 4f
        val matches = mutableListOf<Match>()

        for (slot in 0..3) {
            val x0 = (frame.width * (left + slot * slotWidth + slotWidth * 0.10f)).toInt()
            val x1 = (frame.width * (left + (slot + 1) * slotWidth - slotWidth * 0.10f)).toInt()
            val y0 = (frame.height * (top + (bottom - top) * 0.05f)).toInt()
            val y1 = (frame.height * (top + (bottom - top) * 0.78f)).toInt()
            if (x1 <= x0 || y1 <= y0) return Result(emptyList(), emptyList(), 0f, false)

            val crop = runCatching {
                Bitmap.createBitmap(frame, x0, y0, x1 - x0, y1 - y0)
            }.getOrNull() ?: return Result(emptyList(), emptyList(), 0f, false)

            val query = try { fingerprint(crop) } finally { crop.recycle() }
            val ranked = templateFingerprints.map { (id, fp) -> Match(id, similarity(query, fp)) }
                .sortedByDescending { it.score }
            val best = ranked.firstOrNull() ?: return Result(emptyList(), emptyList(), 0f, false)
            val margin = best.score - (ranked.getOrNull(1)?.score ?: 0f)
            if (best.score < MIN_SCORE || margin < MIN_MARGIN) {
                return Result(emptyList(), matches.map { it.id }, minOf(best.score, MIN_SCORE), false)
            }
            matches += best
        }

        val cards = matches.mapNotNull { cardForAsset(it.id) }
        val confidence = matches.minOfOrNull { it.score } ?: 0f
        return Result(cards, matches.map { it.id }, confidence, cards.size == 4)
    }

    private fun cardForAsset(id: String): Card? = when (id.lowercase()) {
        "hog-rider" -> Card.HOG_RIDER
        "musketeer" -> Card.MUSKETEER
        "cannon" -> Card.CANNON
        "fireball" -> Card.FIREBALL
        "skeletons", "skeletons-ev1" -> Card.SKELETONS
        "ice-golem" -> Card.ICE_GOLEM
        "ice-spirit", "ice-spirit-ev1" -> Card.ICE_SPIRIT
        "the-log" -> Card.LOG
        else -> null
    }

    private fun fingerprint(source: Bitmap): Fingerprint {
        val scaled = Bitmap.createScaledBitmap(source, GRID, GRID, true)
        try {
            val gray = FloatArray(GRID * GRID)
            val hist = FloatArray(12)
            var index = 0
            for (y in 0 until GRID) for (x in 0 until GRID) {
                val pixel = scaled.getPixel(x, y)
                val r = Color.red(pixel) / 255f
                val g = Color.green(pixel) / 255f
                val b = Color.blue(pixel) / 255f
                gray[index++] = (0.299f * r + 0.587f * g + 0.114f * b)
                val bin = (r * 4).toInt().coerceIn(0, 3) * 3 +
                    (g * 2).toInt().coerceIn(0, 2)
                hist[bin] += 1f
            }
            val total = (GRID * GRID).toFloat()
            for (i in hist.indices) hist[i] /= total
            val mean = gray.average().toFloat()
            for (i in gray.indices) gray[i] -= mean
            return Fingerprint(gray, hist)
        } finally {
            if (scaled !== source) scaled.recycle()
        }
    }

    private fun similarity(a: Fingerprint, b: Fingerprint): Float {
        var distance = 0f
        for (i in a.gray.indices) distance += abs(a.gray[i] - b.gray[i])
        val grayScore = 1f - distance / (a.gray.size * 0.5f)
        var colorDistance = 0f
        for (i in a.color.indices) colorDistance += abs(a.color[i] - b.color[i])
        val colorScore = 1f - colorDistance / 2f
        return (grayScore * 0.78f + colorScore * 0.22f).coerceIn(0f, 1f)
    }

    companion object {
        private const val GRID = 16
        private const val MIN_SCORE = 0.84f
        private const val MIN_MARGIN = 0.035f
    }
}
