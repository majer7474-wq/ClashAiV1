package dev.clashvision.agent

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.media.Image
import org.json.JSONObject
import java.nio.FloatBuffer

/** Reads four hand portraits and the smaller next-card portrait in a game-only frame. */
internal class HandReader(context: Context) : AutoCloseable {
    private val env = OrtEnvironment.getEnvironment()
    private val labels = context.assets.open("vision/card_classes.json").bufferedReader().use {
        JSONObject(it.readText())
    }
    private val session = OrtSession.SessionOptions().use { options ->
        options.setIntraOpNumThreads(2)
        context.assets.open("vision/hand.onnx").use { env.createSession(it.readBytes(), options) }
    }
    private val centers = doubleArrayOf(.31, .499, .687, .875, .092)

    fun identify(image: Image): List<String?> = centers.mapIndexed { slot, center ->
        val plane = image.planes[0]
        val raw = plane.buffer
        val width = image.width * if (slot == 4) .078 else .164
        val bottom = image.height * if (slot == 4) .985 else .943
        val left = (center * image.width - width / 2).toInt()
        val top = (bottom.toInt() - width * 1.23).toInt()
        val cropWidth = (center * image.width + width / 2).toInt() - left
        val cropHeight = bottom.toInt() - top
        val pixels = IntArray(cropWidth * cropHeight)
        for (y in 0 until cropHeight) {
            for (x in 0 until cropWidth) {
                val base = raw.position() + (top + y) * plane.rowStride + (left + x) * plane.pixelStride
                pixels[y * cropWidth + x] = android.graphics.Color.rgb(
                    raw.get(base).toInt() and 255, raw.get(base + 1).toInt() and 255,
                    raw.get(base + 2).toInt() and 255)
            }
        }
        val crop = Bitmap.createBitmap(pixels, cropWidth, cropHeight, Bitmap.Config.ARGB_8888)
        val side = 224
        val area = side * side
        val scaled = Bitmap.createScaledBitmap(crop, side, side, true)
        val resized = IntArray(area)
        scaled.getPixels(resized, 0, side, 0, 0, side, side)
        if (scaled !== crop) scaled.recycle()
        crop.recycle()
        val values = FloatArray(area * 3)
        var sum = 0.0
        for (i in resized.indices) {
            val pixel = resized[i]
            for (channel in 0..2) {
                val v = ((pixel ushr (16 - channel * 8)) and 255) / 255f
                values[channel * area + i] = v
                sum += v
            }
        }
        if (sum < .01 * values.size) return@mapIndexed null
        val scale = (.45 * values.size / sum).toFloat()
        for (i in values.indices) values[i] = (values[i] * scale).coerceAtMost(1f)
        OnnxTensor.createTensor(env, FloatBuffer.wrap(values), longArrayOf(1, 3, 224, 224)).use { input ->
            session.run(mapOf("rgb" to input)).use { result ->
                val scores = (result[0].value as Array<*>)[0] as FloatArray
                val index = scores.indices.maxBy { scores[it] }
                if (scores[index] >= .55f) labels.optString(index.toString()).ifEmpty { null } else null
            }
        }
    }

    override fun close() = session.close()
}
