package com.clashai.bot.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.graphics.RectF
import com.clashai.bot.ai.Card
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import kotlin.math.abs

/**
 * Lightweight, resolution-independent card matcher.
 *
 * Official card thumbnails are cached locally on first use. Every observed hand
 * card is cropped using normalized hand regions and converted to the same small
 * descriptor before comparison. A low-confidence or ambiguous match is rejected.
 */
class CardTemplateMatcher(context: Context) : AutoCloseable {

    data class Match(val card: Card?, val confidence: Float, val distance: Float)

    private data class Template(val card: Card, val descriptor: FloatArray)

    private val appContext = context.applicationContext
    private val worker = Executors.newSingleThreadExecutor()
    @Volatile private var templates: List<Template> = emptyList()
    @Volatile private var loading = true

    private val sources = listOf(
        Card.HOG_RIDER to "https://api-assets.clashroyale.com/cards/300/Ubu0oUl8tZkusnkZf8Xv9Vno5IO29Y-jbZ4fhoNJ5oc.png",
        Card.MUSKETEER to "https://api-assets.clashroyale.com/cards/300/Tex1C48UTq9FKtAX-3tzG0FJmc9jzncUZG3bb5Vf-Ds.png",
        Card.CANNON to "https://api-assets.clashroyale.com/cards/300/nZK1y-beLxO5vnlyUhK6-2zH2NzXJwqykcosqQ1cmZ8.png",
        Card.FIREBALL to "https://api-assets.clashroyale.com/cards/300/lZD9MILQv7O-P3XBr_xOLS5idwuz3_7Ws9G60U36yhc.png",
        Card.SKELETONS to "https://api-assets.clashroyale.com/cards/300/oO7iKMU5m0cdxhYPZA3nWQiAUh2yoGgdThLWB1rVSec.png",
        Card.ICE_GOLEM to "https://api-assets.clashroyale.com/cards/300/r05cmpwV1o7i7FHodtZwW3fmjbXCW34IJCsDEV5cZC4.png",
        Card.ICE_SPIRIT to "https://api-assets.clashroyale.com/cards/300/lv1budiafU9XmSdrDkk0NYyqASAFYyZ06CPysXKZXlA.png",
        Card.LOG to "https://api-assets.clashroyale.com/cards/300/_iDwuDLexHPFZ_x4_a0eP-rxCS6vwWgTs6DLauwwoaY.png"
    )

    init {
        worker.execute { loadTemplates() }
    }

    fun isReady(): Boolean = !loading && templates.size == Card.values().size

    fun recognize(frame: Bitmap, regions: List<RectF>): List<Match> {
        val bank = templates
        if (loading || bank.isEmpty()) return emptyList()

        return regions.take(4).map { region ->
            val left = (region.left * frame.width).toInt().coerceIn(0, frame.width - 1)
            val top = (region.top * frame.height).toInt().coerceIn(0, frame.height - 1)
            val right = (region.right * frame.width).toInt().coerceIn(left + 1, frame.width)
            val bottom = (region.bottom * frame.height).toInt().coerceIn(top + 1, frame.height)
            val crop = Bitmap.createBitmap(frame, left, top, right - left, bottom - top)
            try {
                val observed = descriptor(crop)
                val ranked = bank.map { it.card to distance(observed, it.descriptor) }
                    .sortedBy { it.second }
                val best = ranked.first()
                val second = ranked.getOrNull(1)?.second ?: 1f
                val accepted = best.second <= MAX_DISTANCE &&
                    (second - best.second) >= MIN_MARGIN
                Match(
                    card = if (accepted) best.first else null,
                    confidence = if (accepted) (1f - best.second).coerceIn(0f, 1f) else 0f,
                    distance = best.second
                )
            } finally {
                crop.recycle()
            }
        }
    }

    private fun loadTemplates() {
        val directory = File(appContext.cacheDir, "clashai_card_templates").apply { mkdirs() }
        val loaded = mutableListOf<Template>()
        try {
            for ((card, source) in sources) {
                val file = File(directory, "${card.name.lowercase()}.png")
                if (!file.exists() || file.length() == 0L) download(source, file)
                val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: continue
                try {
                    loaded += Template(card, descriptor(bitmap))
                } finally {
                    bitmap.recycle()
                }
            }
            templates = loaded
        } finally {
            loading = false
        }
    }

    private fun download(source: String, destination: File) {
        val connection = (URL(source).openConnection() as HttpURLConnection).apply {
            connectTimeout = 7000
            readTimeout = 7000
            requestMethod = "GET"
        }
        try {
            connection.inputStream.use { input ->
                destination.outputStream().use { output -> input.copyTo(output) }
            }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * A compact RGB descriptor sampled from the central artwork. Resizing to a
     * fixed grid removes dependence on the source display resolution.
     */
    private fun descriptor(bitmap: Bitmap): FloatArray {
        val side = 18
        val sample = Bitmap.createScaledBitmap(bitmap, side, side, true)
        val values = FloatArray(side * side * 3)
        var index = 0
        try {
            for (y in 0 until side) {
                for (x in 0 until side) {
                    val pixel = sample.getPixel(x, y)
                    values[index++] = ((pixel shr 16) and 255) / 255f
                    values[index++] = ((pixel shr 8) and 255) / 255f
                    values[index++] = (pixel and 255) / 255f
                }
            }
        } finally {
            if (sample !== bitmap) sample.recycle()
        }
        return values
    }

    private fun distance(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 1f
        var total = 0f
        for (i in a.indices) total += abs(a[i] - b[i])
        return total / a.size
    }

    override fun close() {
        worker.shutdownNow()
    }

    companion object {
        // Conservative until validated against real Xiaomi Pad 6 battle frames.
        private const val MAX_DISTANCE = 0.14f
        private const val MIN_MARGIN = 0.025f
    }
}
