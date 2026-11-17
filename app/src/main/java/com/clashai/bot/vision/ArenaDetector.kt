package com.clashai.bot.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import org.json.JSONObject
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min

data class ArenaUnit(
    val name: String,
    val confidence: Float,
    val centerX: Float,
    val centerY: Float,
    val width: Float,
    val height: Float
)

class ArenaDetector(context: Context) : AutoCloseable {
    companion object {
        private const val SIZE = 800
        private const val SOURCE_W = 568f
        private const val SOURCE_H = 896f
        private const val CONFIDENCE = 0.32f
        private const val IOU_LIMIT = 0.45f
    }

    private val env = OrtEnvironment.getEnvironment()
    private val session: OrtSession
    private val inputName: String
    private val labels: List<String>

    init {
        val modelBytes = context.assets.open("clash_yolo.onnx").use { it.readBytes() }
        session = env.createSession(modelBytes)
        inputName = session.inputNames.first()
        val json = context.assets.open("clash_classes.json").bufferedReader().use { it.readText() }
        val obj = JSONObject(json)
        labels = (0 until obj.length()).map { obj.optString(it.toString(), "class-$it") }
    }

    fun detect(arena: Bitmap): List<ArenaUnit> {
        val input = preprocess(arena)
        val tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(input), longArrayOf(1, 3, SIZE.toLong(), SIZE.toLong()))
        try {
            val result = session.run(mapOf(inputName to tensor))
            try {
                val output = result[0] as OnnxTensor
                val shape = output.info.shape
                val values = output.floatBuffer
                val raw = FloatArray(values.remaining())
                values.get(raw)
                return decode(raw, shape)
            } finally {
                result.close()
            }
        } finally {
            tensor.close()
        }
    }

    private fun preprocess(bitmap: Bitmap): FloatArray {
        val scale = min(SIZE / SOURCE_W, SIZE / SOURCE_H)
        val resizedW = (SOURCE_W * scale).toInt()
        val resizedH = (SOURCE_H * scale).toInt()
        val padX = (SIZE - resizedW) / 2
        val padY = (SIZE - resizedH) / 2
        val output = FloatArray(3 * SIZE * SIZE).apply { fill(114f / 255f) }
        val plane = SIZE * SIZE

        for (y in 0 until resizedH) {
            val sy = (y * bitmap.height.toFloat() / resizedH).toInt().coerceIn(0, bitmap.height - 1)
            for (x in 0 until resizedW) {
                val sx = (x * bitmap.width.toFloat() / resizedW).toInt().coerceIn(0, bitmap.width - 1)
                val pixel = bitmap.getPixel(sx, sy)
                val index = (y + padY) * SIZE + x + padX
                output[index] = Color.red(pixel) / 255f
                output[plane + index] = Color.green(pixel) / 255f
                output[2 * plane + index] = Color.blue(pixel) / 255f
            }
        }
        return output
    }

    private fun decode(data: FloatArray, shape: LongArray): List<ArenaUnit> {
        if (shape.size != 3) return emptyList()
        val a = shape[1].toInt()
        val b = shape[2].toInt()
        val channelsFirst = a < b
        val channels = if (channelsFirst) a else b
        val anchors = if (channelsFirst) b else a
        if (channels < 5 || data.size != channels * anchors) return emptyList()
        val classCount = min(labels.size, channels - 4)
        val scale = min(SIZE / SOURCE_W, SIZE / SOURCE_H)
        val padX = (SIZE - SOURCE_W * scale) / 2f
        val padY = (SIZE - SOURCE_H * scale) / 2f
        val candidates = ArrayList<ArenaUnit>()

        fun at(channel: Int, anchor: Int): Float =
            if (channelsFirst) data[channel * anchors + anchor]
            else data[anchor * channels + channel]

        for (i in 0 until anchors) {
            var bestClass = -1
            var bestScore = 0f
            for (c in 0 until classCount) {
                val score = at(c + 4, i)
                if (score > bestScore) { bestScore = score; bestClass = c }
            }
            if (bestScore < CONFIDENCE || bestClass < 0) continue

            val cx = (at(0, i) - padX) / scale
            val cy = (at(1, i) - padY) / scale
            val w = at(2, i) / scale
            val h = at(3, i) / scale
            val x0 = (cx - w / 2f).coerceIn(0f, SOURCE_W)
            val y0 = (cy - h / 2f).coerceIn(0f, SOURCE_H)
            val x1 = (cx + w / 2f).coerceIn(0f, SOURCE_W)
            val y1 = (cy + h / 2f).coerceIn(0f, SOURCE_H)
            if (x1 <= x0 || y1 <= y0) continue
            candidates.add(ArenaUnit(labels[bestClass], bestScore,
                (x0 + x1) / 2f / SOURCE_W, (y0 + y1) / 2f / SOURCE_H,
                (x1 - x0) / SOURCE_W, (y1 - y0) / SOURCE_H))
        }
        return classNms(candidates).take(80)
    }

    private fun classNms(items: List<ArenaUnit>): List<ArenaUnit> {
        val kept = ArrayList<ArenaUnit>()
        for ((_, group) in items.groupBy { it.name }) {
            val pending = group.sortedByDescending { it.confidence }.toMutableList()
            while (pending.isNotEmpty() && kept.size < 120) {
                val best = pending.removeAt(0)
                kept.add(best)
                pending.removeAll { iou(best, it) > IOU_LIMIT }
            }
        }
        return kept.sortedByDescending { it.confidence }
    }

    private fun iou(a: ArenaUnit, b: ArenaUnit): Float {
        val ax0 = a.centerX - a.width / 2f; val ay0 = a.centerY - a.height / 2f
        val ax1 = a.centerX + a.width / 2f; val ay1 = a.centerY + a.height / 2f
        val bx0 = b.centerX - b.width / 2f; val by0 = b.centerY - b.height / 2f
        val bx1 = b.centerX + b.width / 2f; val by1 = b.centerY + b.height / 2f
        val iw = max(0f, min(ax1, bx1) - max(ax0, bx0))
        val ih = max(0f, min(ay1, by1) - max(ay0, by0))
        val inter = iw * ih
        val union = a.width * a.height + b.width * b.height - inter
        return if (union <= 0f) 0f else inter / union
    }

    override fun close() { session.close() }
}
