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
        // Crop relative to the actual 1080x2400 game viewport, not the
        // physical tablet frame. This handles centered letterboxing in split view.
        val viewport = GameViewport.fromFrame(bitmap.width, bitmap.height)
            ?: return OcrResult("", null, emptyList(), 0f)
        val left = viewport.mapX(300f) ?: return OcrResult("", null, emptyList(), 0f)
        val top = viewport.mapY(1980f) ?: return OcrResult("", null, emptyList(), 0f)
        val right = (viewport.mapX(780f) ?: return OcrResult("", null, emptyList(), 0f))
            .coerceIn(left + 1, bitmap.width)
        val bottom = (viewport.mapY(2395f) ?: return OcrResult("", null, emptyList(), 0f))
            .coerceIn(top + 1, bitmap.height)
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
            // Ignore elixir-cost digits printed inside the card artwork; the
            // actual elixir counter is on the bottom-most HUD line.
            if (cx !in 0.05f..0.98f || cy !in 0.55f..0.99f) continue

            val raw = line.text.trim()
            val fraction = Regex("""(?<!\d)(10|[0-9])\s*/\s*10(?!\d)""").find(raw)
            val digits = fraction?.groupValues?.get(1)?.toIntOrNull()
                ?: raw.toIntOrNull()
                ?: continue
            if (digits in 0..10) candidates += digits to cy
        }

        val distinct = candidates.map { it.first }.distinct()
        // Prefer the lowest numeric line in the dedicated HUD crop. Card costs
        // sit higher and are excluded; multiple OCR fragments at the same height
        // remain ambiguous rather than being guessed.
        val lowest = candidates.maxOfOrNull { it.second }
        val bottomLine = candidates.filter { lowest != null && kotlin.math.abs(it.second - lowest) < 0.08f }
        val value = bottomLine.map { it.first }.distinct().singleOrNull()
            ?.takeIf { it in 0..10 }
        val confidence = if (value != null) 0.90f else 0f
        return OcrResult(result.text, value?.toFloat(), distinct, confidence)
    }

    fun close() = recognizer.close()
}