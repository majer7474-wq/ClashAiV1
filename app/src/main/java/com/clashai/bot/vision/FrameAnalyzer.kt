package com.clashai.bot.vision

import android.graphics.Bitmap
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
    val capturedAtMs: Long = SystemClock.elapsedRealtime()
)

class FrameAnalyzer {

    private val ocr = OcrEngine()

    fun analyze(bitmap: Bitmap): ScreenObservation {
        require(bitmap.width > 0 && bitmap.height > 0)

        val capturedAt = SystemClock.elapsedRealtime()
        val result = ocr.analyze(bitmap)

        // Card recognition is intentionally not guessed from OCR text.
        // Until a validated visual card detector is installed, confidence stays 0
        // and the autoplay decision gate must refuse to play.
        return ScreenObservation(
            elixir = result.elixir,
            visibleCards = emptyList(),
            enemyThreat = Threat.NONE,
            confidence = result.confidence,
            detectedText = result.allText,
            handConfidence = 0f,
            capturedAtMs = capturedAt
        )
    }

    fun close() {
        ocr.close()
    }
}
