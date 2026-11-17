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
    val handRegions: List<RectF> = emptyList(),
    val enemyTargetX: Float? = null,
    val enemyTargetY: Float? = null,
    val enemyCount: Int = 0
)

class FrameAnalyzer(context: Context) {
    private val handLocator = AdaptiveHandLocator()
    private val cardMatcher = CardTemplateMatcher(context)
    private val ocr = OcrEngine()
    private val arenaDetector = ArenaDetector(context)

    fun analyze(bitmap: Bitmap): ScreenObservation {
        require(bitmap.width > 0 && bitmap.height > 0)
        val ocrResult = ocr.analyze(bitmap)
        val hand = handLocator.locate(bitmap)
        val matches = cardMatcher.recognize(bitmap, hand.regions)

        val allFourCertain = matches.size == 4 &&
            matches.all { it.card != null && it.confidence >= 0.85f } &&
            matches.mapNotNull { it.card }.distinct().size == 4
        val cards = if (allFourCertain) matches.mapNotNull { it.card } else emptyList()
        val handConfidence = if (allFourCertain) matches.minOf { it.confidence } else 0f

        // Model was trained on a 568x896 arena-only crop. Extract that region
        // from the fixed 1080x2400 virtual game canvas, excluding hand and HUD.
        val topLeft = VirtualGameSpace.toScreen(0f, 300f, bitmap.width, bitmap.height)
        val bottomRight = VirtualGameSpace.toScreen(1080f, 2004f, bitmap.width, bitmap.height)
        val left = topLeft.x.coerceIn(0, bitmap.width - 1)
        val top = topLeft.y.coerceIn(0, bitmap.height - 1)
        val right = bottomRight.x.coerceIn(left + 1, bitmap.width)
        val bottom = bottomRight.y.coerceIn(top + 1, bitmap.height)
        val arenaRaw = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
        val arena = Bitmap.createScaledBitmap(arenaRaw, 568, 896, true)
        val threat = try {
            EnemyThreatAnalyzer.assess(arenaDetector.detect(arena))
        } finally {
            if (arena !== arenaRaw) arena.recycle()
            arenaRaw.recycle()
        }

        return ScreenObservation(
            elixir = ocrResult.elixir,
            visibleCards = cards,
            enemyThreat = threat.threat,
            confidence = ocrResult.confidence,
            detectedText = ocrResult.allText,
            handConfidence = handConfidence,
            // Timestamp after vision processing so inference latency does not reject every decision.\n            capturedAtMs = SystemClock.elapsedRealtime(),
            handRegions = hand.regions,
            enemyTargetX = threat.targetX?.times(VirtualGameSpace.WIDTH),
            enemyTargetY = threat.targetY?.let { 300f + it * 1704f },
            enemyCount = threat.enemyUnits.size
        )
    }

    fun close() {
        ocr.close()
        cardMatcher.close()
        arenaDetector.close()
    }
}
