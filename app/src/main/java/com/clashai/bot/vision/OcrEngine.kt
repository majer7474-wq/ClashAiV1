package com.clashai.bot.vision

import android.graphics.Bitmap
import android.graphics.Rect
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

    private val recognizer = TextRecognition.getClient(
        TextRecognizerOptions.DEFAULT_OPTIONS
    )

    fun analyze(bitmap: Bitmap): OcrResult {
        val image = InputImage.fromBitmap(bitmap, 0)

        val result = try {
            Tasks.await(
                recognizer.process(image),
                2500,
                TimeUnit.MILLISECONDS
            )
        } catch (e: Exception) {
            return OcrResult("", null, emptyList(), 0f)
        }

        val text = result.text
        val height = bitmap.height

        val candidates = mutableListOf<Int>()

        for (block in result.textBlocks) {
            for (line in block.lines) {
                val bounds = line.boundingBox ?: continue

                // Elixir counter is expected in the lower HUD area.
                if (bounds.centerY() < height * 0.70f) continue

                val value = line.text.trim().toIntOrNull()
                    ?: continue

                if (value in 0..10) {
                    candidates.add(value)
                }
            }
        }

        val detected = candidates.firstOrNull()

        return OcrResult(
            allText = text,
            elixir = detected?.toFloat(),
            numbers = candidates,
            confidence = if (detected != null) 0.75f else 0f
        )
    }

    fun close() {
        recognizer.close()
    }
}
