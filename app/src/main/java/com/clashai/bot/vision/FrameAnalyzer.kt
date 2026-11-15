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
    val handDiagnostic: String = "",
    val frameWidth: Int = 1080,
    val frameHeight: Int = 2400
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

        val enemyObjects = detections.filter {
            it.centerY < bitmap.height * 0.50f &&
                it.label !in TOWER_LABELS &&
                it.confidence >= MIN_ENEMY_CONFIDENCE
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
            handDiagnostic = handResult.diagnostic,
            frameWidth = bitmap.width,
            frameHeight = bitmap.height
        )
    }

    private fun classifyThreat(enemies: List<ArenaDetection>): Threat {
        if (enemies.isEmpty()) return Threat.NONE
        val labels = enemies.map { it.label }.toSet()
        val air = labels.any { it in AIR_UNITS }
        val heavy = labels.any { it in HEAVY_GROUND }
        val swarm = labels.any { it in SWARM_UNITS }
        val closestToBridge = enemies.maxOfOrNull { it.centerY } ?: 0f
        return when {
            air -> Threat.AIR
            heavy && closestToBridge > 0.38f -> Threat.HIGH_DANGER
            heavy -> Threat.TANK
            swarm -> Threat.SWARM
            else -> Threat.WIN_CONDITION
        }
    }

    override fun close() {
        ocr.close()
        arenaDetector.close()
        cardCatalog.close()
    }

    companion object {
        private const val MIN_ENEMY_CONFIDENCE = 0.50f
        private val TOWER_LABELS = setOf(
            "king-tower", "queen-tower", "dagger-duchess-tower", "cannoneer-tower"
        )
        private val AIR_UNITS = setOf(
            "balloon", "baby-dragon", "lava-hound", "inferno-dragon", "phoenix-big",
            "phoenix-small", "minion", "minion-giant", "bat", "electro-dragon",
            "mega-minion", "flying-machine", "lava-pup"
        )
        private val HEAVY_GROUND = setOf(
            "giant", "golem", "pekka", "mega-knight", "electro-giant", "royal-giant",
            "goblin-giant", "ram-rider", "battle-ram", "hog-rider", "hog", "miner"
        )
        private val SWARM_UNITS = setOf(
            "skeleton", "bat", "goblin", "spear-goblin", "minion",
            "skeleton-army-evolution", "goblin-gang"
        )
    }
}
