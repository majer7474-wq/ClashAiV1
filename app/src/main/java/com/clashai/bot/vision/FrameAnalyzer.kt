package com.clashai.bot.vision

import android.content.Context
import android.graphics.Bitmap
import com.clashai.bot.ai.Card
import com.clashai.bot.ai.Threat

data class ScreenObservation(
    val elixir: Float?,
    val visibleCards: List<Card>,
    val enemyThreat: Threat,
    val confidence: Float,
    val detectedText: String = "",
    val detections: List<ArenaDetection> = emptyList(),
    val capturedAtMs: Long = System.currentTimeMillis(),
    val handConfidence: Float = 0f,
    val handComplete: Boolean = false,
    val detectedCardIds: List<String> = emptyList(),
    val handDiagnostic: String = ""
)

class FrameAnalyzer(context: Context) : AutoCloseable {
    private val ocr = OcrEngine()
    private val arenaDetector = YoloArenaDetector(context)
    private val cardCatalog = CardTemplateCatalog(context)
    private val handRecognizer = HandCardRecognizer(cardCatalog)

    fun analyze(bitmap: Bitmap): ScreenObservation {
        require(bitmap.width > 0 && bitmap.height > 0)
        val textResult = ocr.analyze(bitmap)
        val handResult = handRecognizer.recognize(bitmap)
        val detections = try {
            arenaDetector.detect(bitmap)
        } catch (_: Exception) {
            emptyList()
        }

        // Clash Royale's opponent half is the upper half in the standard
        // portrait orientation. This is an observation only, not permission
        // to play: hand and elixir confidence are validated independently.
        val enemyObjects = detections.filter {
            it.centerY < bitmap.height * 0.48f &&
                it.label !in setOf("king-tower", "queen-tower", "dagger-duchess-tower", "cannoneer-tower")
        }
        val threat = classifyThreat(enemyObjects)
        val confidence = detections.maxOfOrNull { it.confidence } ?: 0f

        return ScreenObservation(
            elixir = textResult.elixir,
            visibleCards = handResult.cards,
            enemyThreat = threat,
            confidence = confidence,
            detectedText = textResult.allText,
            detections = detections,
            handConfidence = handResult.confidence,
            handComplete = handResult.complete,
            detectedCardIds = handResult.cardIds,
            handDiagnostic = handResult.diagnostic
        )
    }

    private fun classifyThreat(enemies: List<ArenaDetection>): Threat {
        if (enemies.isEmpty()) return Threat.NONE
        val labels = enemies.map { it.label }.toSet()
        val highDanger = setOf("hog-rider", "hog", "ram-rider", "balloon", "giant", "golem", "pekka", "mega-knight")
        val air = setOf("balloon", "baby-dragon", "lava-hound", "inferno-dragon", "phoenix-big", "minion", "minion-giant")
        val swarm = setOf("skeleton", "bat", "goblin", "spear-goblin", "minion", "skeleton-army-evolution")
        val tanks = setOf("giant", "golem", "pekka", "mega-knight", "electro-giant", "lava-hound", "royal-giant")
        return when {
            // Air win conditions must not be classified as generic high danger:
            // Hog 2.6 must answer Balloon/Lava Hound with Musketeer, not Cannon.
            labels.any { it in highDanger && it !in air } -> Threat.HIGH_DANGER
            labels.any { it in air } -> Threat.AIR
            labels.any { it in swarm } -> Threat.SWARM
            labels.any { it in tanks } -> Threat.TANK
            else -> Threat.WIN_CONDITION
        }
    }

    override fun close() {
        ocr.close()
        arenaDetector.close()
        cardCatalog.close()
    }
}
