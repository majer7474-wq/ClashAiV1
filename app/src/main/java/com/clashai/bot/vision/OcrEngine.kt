package com.clashai.bot.vision

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.android.gms.tasks.Tasks
import java.util.concurrent.TimeUnit

data class OcrResult(
    val allText: String,
    val elixir: Float?,
    val numbers: List<Int>,
    val confidence: Float
)

class OcrEngine {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    fun analyze(bitmap: Bitmap): OcrResult {
        val image = InputImage.fromBitmap(bitmap, 0)
        val result = try {
            // Keep OCR bounded so one slow frame cannot stall the capture loop
            // long enough for the AI to reject every resulting observation.
            Tasks.await(recognizer.process(image), OCR_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (_: Exception) {
            return OcrResult("", null, emptyList(), 0f)
        }

        val candidates = mutableListOf<Int>()
        for (block in result.textBlocks) {
            for (line in block.lines) {
                val bounds = line.boundingBox ?: continue
                val centerX = bounds.centerX().toFloat() / bitmap.width
                val centerY = bounds.centerY().toFloat() / bitmap.height
                // Elixir numeral is in the central bottom HUD, not over card costs.
                if (centerX !in 0.38f..0.62f || centerY < 0.82f) continue
                val value = line.text.trim().toIntOrNull() ?: continue
                if (value in 0..10) candidates.add(value)
            }
        }
        val distinct = candidates.distinct()
        val detected = distinct.singleOrNull()
        return OcrResult(
            allText = result.text,
            elixir = detected?.toFloat(),
            numbers = distinct,
            confidence = if (detected != null) 0.90f else 0f
        )
    }

    fun close() = recognizer.close()

    companion object {
        private const val OCR_TIMEOUT_MS = 650L
    }
}
