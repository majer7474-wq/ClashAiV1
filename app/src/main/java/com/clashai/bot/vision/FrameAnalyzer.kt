package com.clashai.bot.vision

import android.graphics.Bitmap
import com.clashai.bot.ai.Card
import com.clashai.bot.ai.Threat

data class ScreenObservation(
    val elixir: Float?,
    val visibleCards: List<Card>,
    val enemyThreat: Threat,
    val confidence: Float,
    val detectedText: String = ""
)

class FrameAnalyzer {

    private val ocr = OcrEngine()

    fun analyze(bitmap: Bitmap): ScreenObservation {
        require(bitmap.width > 0 && bitmap.height > 0)

        val result = ocr.analyze(bitmap)

        return ScreenObservation(
            elixir = result.elixir,
            visibleCards = emptyList(),
            enemyThreat = Threat.NONE,
            confidence = result.confidence,
            detectedText = result.allText
        )
    }

    fun close() {
        ocr.close()
    }
}
