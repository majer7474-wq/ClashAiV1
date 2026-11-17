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
        val ocrResult = ocr.analyze(bitmap)
        val hand = handLocator.locate(bitmap)
        val matches = cardMatcher.recognize(bitmap, hand.regions)

        val allFourCertain = matches.size == 4 &&
            matches.all { it.card != null && it.confidence >= 0.85f } &&
            matches.mapNotNull { it.card }.distinct().size == 4

        val cards = if (allFourCertain) matches.mapNotNull { it.card } else emptyList()
        val confidence = if (allFourCertain) matches.minOf { it.confidence } else 0f

        return ScreenObservation(
            elixir = ocrResult.elixir,
            visibleCards = cards,
            enemyThreat = Threat.NONE,
            confidence = ocrResult.confidence,
            detectedText = ocrResult.allText,
            handConfidence = confidence,
            capturedAtMs = capturedAt,
            handRegions = hand.regions
        )
    }

    fun close() {
        ocr.close()
        cardMatcher.close()
    }
}
