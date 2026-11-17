package com.clashai.bot.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.os.SystemClock
import com.clashai.bot.ai.Card
import com.clashai.bot.ai.Threat

data class ScreenObservation(
    val elixir: Float?,
    val visibleCards: List<Card>,
    val enemyThreat: Threat,
    val confidence: Float,
    val detectedText: String = "",
    val handConfidence: Float = 0f,
    val capturedAtMs: Long = SystemClock.elapsedRealtime(),
    val handRegions: List<RectF> = emptyList()
)

class FrameAnalyzer(context: Context) {

    private val handLocator = AdaptiveHandLocator()
    private val cardMatcher = CardTemplateMatcher(context)
    private val ocr = OcrEngine()

    fun analyze(bitmap: Bitmap): ScreenObservation {
        require(bitmap.width > 0 && bitmap.height > 0)

        val capturedAt = SystemClock.elapsedRealtime()
        val result = ocr.analyze(bitmap)
        val hand = handLocator.locate(bitmap)
        val matches = cardMatcher.recognize(bitmap, hand.regions)

        // The decision engine requires all four hand cards to be identified.
        // Partial or ambiguous matches are rejected instead of guessed.
        val completeHand = matches.size == 4 &&
            matches.all { it.card != null } &&
            matches.mapNotNull { it.card }.distinct().size == 4

        val cards = if (completeHand) matches.mapNotNull { it.card } else emptyList()
        val handConfidence = if (completeHand) {
            matches.minOf { it.confidence }
        } else {
            0f
        }

        return ScreenObservation(
            elixir = result.elixir,
            visibleCards = cards,
            enemyThreat = Threat.NONE,
            confidence = result.confidence,
            detectedText = result.allText,
            handConfidence = handConfidence,
            capturedAtMs = capturedAt,
            handRegions = hand.regions
        )
    }

    fun close() {
        ocr.close()
        cardMatcher.close()
    }
}
