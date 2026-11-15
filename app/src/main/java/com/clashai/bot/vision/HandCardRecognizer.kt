package com.clashai.bot.vision

import android.graphics.Bitmap
import android.graphics.Color
import com.clashai.bot.ai.Card
import kotlin.math.abs

/**
 * Reads the four visible card slots for the configured Hog 2.6 deck.
 * Slot geometry is proportional to the full portrait game frame.
 */
class HandCardRecognizer(private val catalog: CardTemplateCatalog) {

    data class Result(
        val cards: List<Card>,
        val cardIds: List<String>,
        val confidence: Float,
        val complete: Boolean
    )

    private data class Fingerprint(val gray: FloatArray, val color: FloatArray)
    private data class Match(val id: String, val card: Card, val score: Float)

    private val templateFingerprints: List<Pair<String, Fingerprint>> =
        catalog.all().mapNotNull { template ->
            runCatching { template.id to fingerprintTemplate(template.bitmap) }.getOrNull()
        }

    fun recognize(frame: Bitmap): Result {
        if (frame.width < 300 || frame.height < 500 || templateFingerprints.size < 100) {
            return Result(emptyList(), emptyList(), 0f, false)
        }

        // Four card centres in the standard portrait hand: ~17%, 39%, 61%, 83%.
        // The old 24.5%-92.5% span started inside slot 2 and shifted every crop.
        val left = 0.06f
        val right = 0.94f
        val top = 0.815f
        val bottom = 0.975f
        val slotWidth = (right - left) / 4f
        val matches = mutableListOf<Match>()

        for (slot in 0..3) {
            val x0 = (frame.width * (left + slot * slotWidth + slotWidth * 0.10f)).toInt()
            val x1 = (frame.width * (left + (slot + 1) * slotWidth - slotWidth * 0.10f)).toInt()
            val y0 = (frame.height * (top + (bottom - top) * 0.04f)).toInt()
            val y1 = (frame.height * (top + (bottom - top) * 0.78f)).toInt()
            if (x1 <= x0 || y1 <= y0) return Result(emptyList(), emptyList(), 0f, false)

            val crop = runCatching {
                Bitmap.createBitmap(frame, x0, y0, x1 - x0, y1 - y0)
            }.getOrNull() ?: return Result(emptyList(), emptyList(), 0f, false)

            val query = try { fingerprint(crop) } finally { crop.recycle() }

            // Only accept cards belonging to the active Hog 2.6 deck. This prevents
            // a visually similar unrelated card from being silently substituted.
            val ranked = templateFingerprints.mapNotNull { (id, fp) ->
                val card = cardForAsset(id) ?: return@mapNotNull null
                Match(id, card, similarity(query, fp))
            }.sortedByDescending { it.score }

            val best = ranked.firstOrNull()
                ?: return Result(emptyList(), emptyList(), 0f, false)
            val differentCardRunnerUp = ranked.drop(1).firstOrNull { it.card != best.card }
            val margin = best.score - (differentCardRunnerUp?.score ?: 0f)

            if (best.score < MIN_SCORE || margin < MIN_MARGIN) {
                return Result(emptyList(), matches.map { it.id }, minOf(best.score, MIN_SCORE), false)
            }
            matches += best
        }

        val cards = matches.map { it.card }
        val confidence = matches.minOfOrNull { it.score } ?: 0f
        return Result(cards, matches.map { it.id }, confidence, cards.size == 4 && cards.distinct().size == 4)
    }

    private fun cardForAsset(rawId: String): Card? {
        val id = rawId.lowercase()
            .replace(Regex("\\.(png|webp|jpg)$"), "")
            .replace('_', '-')
            .replace(' ', '-')
        // Match exact base names and known evolution/artwork suffixes.
        return when {
            id == "hog-rider" || id.startsWith("hog-rider-") || id == "hog" -> Card.HOG_RIDER
            id == "musketeer" || id.startsWith("musketeer-") -> Card.MUSKETEER
            id == "cannon" || id.startsWith("cannon-") -> Card.CANNON
            id == "fireball" || id.startsWith("fireball-") -> Card.FIREBALL
            id == "skeletons" || id.startsWith("skeletons-") -> Card.SKELETONS
            id == "ice-golem" || id.startsWith("ice-golem-") -> Card.ICE_GOLEM
            id == "ice-spirit" || id.startsWith("ice-spirit-") -> Card.ICE_SPIRIT
            id == "the-log" || id == "log" || id.startsWith("the-log-") -> Card.LOG
            else -> null
        }
    }

    private fun fingerprintTemplate(source: Bitmap): Fingerprint {
        // Apply the same inner-window normalization to live crops and templates.
        val left = (source.width * 0.10f).toInt().coerceIn(0, source.width - 1)
        val top = (source.height * 0.05f).toInt().coerceIn(0, source.height - 1)
        val right = (source.width * 0.90f).toInt().coerceIn(left + 1, source.width)
        val bottom = (source.height * 0.78f).toInt().coerceIn(top + 1, source.height)
        val crop = Bitmap.createBitmap(source, left, top, right - left, bottom - top)
        return try { fingerprint(crop) } finally { crop.recycle() }
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
                gray[index++] = 0.299f * r + 0.587f * g + 0.114f * b
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
        private const val GRID = 24
        private const val MIN_SCORE = 0.70f
        private const val MIN_MARGIN = 0.025f
    }
}
