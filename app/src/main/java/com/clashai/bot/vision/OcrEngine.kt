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
        val result = try {
            Tasks.await(
                recognizer.process(InputImage.fromBitmap(bitmap, 0)),
                2500,
                TimeUnit.MILLISECONDS
            )
        } catch (_: Exception) {
            return OcrResult("", null, emptyList(), 0f)
        }

        val height = bitmap.height.toFloat()
        val width = bitmap.width.toFloat()
        val candidates = mutableListOf<Pair<Int, Float>>()

        // Only inspect the known elixir-counter zone from the supplied 960x1536 HUD layout.
        for (block in result.textBlocks) for (line in block.lines) {
            val bounds = line.boundingBox ?: continue
            val cx = bounds.exactCenterX() / width
            val cy = bounds.exactCenterY() / height
            if (cx !in 0.38f..0.64f || cy !in 0.90f..0.995f) continue

            val raw = line.text.trim()
            val fraction = Regex("""(?<!\d)(10|[0-9])\s*/\s*10(?!\d)""").find(raw)
            val digits = fraction?.groupValues?.get(1)?.toIntOrNull()
                ?: raw.toIntOrNull()
                ?: continue
            if (digits in 0..10) candidates += digits to cy
        }

        val distinct = candidates.map { it.first }.distinct()
        // Ambiguous or absent OCR is not an elixir reading; fail closed.
        val value = distinct.singleOrNull()
        val confidence = if (value != null) 0.90f else 0f
        return OcrResult(result.text, value?.toFloat(), distinct, confidence)
    }

    fun close() = recognizer.close()
}