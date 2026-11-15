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
        // The elixir counter and purple meter are in the lower-centre HUD,
        // directly below the card row. Do not OCR the lower-left emote/next-card area.
        val left = (bitmap.width * 0.30f).toInt().coerceIn(0, bitmap.width - 1)
        val top = (bitmap.height * 0.84f).toInt().coerceIn(0, bitmap.height - 1)
        val right = (bitmap.width * 0.70f).toInt().coerceIn(left + 1, bitmap.width)
        val bottom = (bitmap.height * 0.998f).toInt().coerceIn(top + 1, bitmap.height)
        val hudCrop = try {
            Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
        } catch (_: Exception) {
            return OcrResult("", null, emptyList(), 0f)
        }

        val result = try {
            Tasks.await(
                recognizer.process(InputImage.fromBitmap(hudCrop, 0)),
                1200,
                TimeUnit.MILLISECONDS
            )
        } catch (_: Exception) {
            return OcrResult("", null, emptyList(), 0f)
        } finally {
            hudCrop.recycle()
        }

        val height = (bottom - top).toFloat()
        val width = (right - left).toFloat()
        val candidates = mutableListOf<Pair<Int, Float>>()

        // Coordinates are now relative to the cropped lower-center HUD region.
        for (block in result.textBlocks) for (line in block.lines) {
            val bounds = line.boundingBox ?: continue
            val cx = bounds.exactCenterX() / width
            val cy = bounds.exactCenterY() / height
            if (cx !in 0.05f..0.98f || cy !in 0.05f..0.99f) continue

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