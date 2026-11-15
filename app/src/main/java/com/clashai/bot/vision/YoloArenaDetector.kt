package com.clashai.bot.vision

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min

data class ArenaDetection(
    val classId: Int,
    val label: String,
    val confidence: Float,
    val centerX: Float,
    val centerY: Float,
    val width: Float,
    val height: Float
)

class YoloArenaDetector(context: Context) : AutoCloseable {
    companion object {
        private const val INPUT_SIZE = 800
        private const val CONFIDENCE_THRESHOLD = 0.42f
        private const val IOU_THRESHOLD = 0.45f
        private const val CLASS_COUNT = 187

        private val LABELS = listOf(
            "archer","archer-evolution","archer-queen","arrows","axe","baby-dragon","balloon","bandit","barbarian","barbarian-barrel",
            "barbarian-evolution","barbarian-hut","bat","bat-evolution","battle-healer","battle-ram","battle-ram-evolution","bomb","bomb-tower","bomber",
            "bomber-evolution","bowler","cannon","cannon-cart","cannoneer-tower","clock","clone","dagger-duchess-tower","dark-prince","dart-goblin",
            "dirt","earthquake","electro-dragon","electro-giant","electro-spirit","electro-wizard","elite-barbarian","elixir","elixir-collector","elixir-golem-big",
            "elixir-golem-mid","elixir-golem-small","executioner","fire-spirit","fireball","firecracker","firecracker-evolution","fisherman","flying-machine","freeze",
            "furnace","giant","giant-skeleton","giant-snowball","goblin","goblin-ball","goblin-barrel","goblin-brawler","goblin-cage","goblin-drill",
            "goblin-giant","goblin-hut","golden-knight","golem","golemite","graveyard","guard","heal-spirit","hog","hog-rider",
            "hunter","ice-golem","ice-spirit","ice-spirit-evolution","ice-wizard","inferno-dragon","inferno-tower","king-tower","knight","knight-evolution",
            "lava-hound","lava-pup","lightning","little-prince","lumberjack","magic-archer","mega-knight","mega-minion","mighty-miner","miner",
            "mini-pekka","minion","monk","mortar","mortar-evolution","mother-witch","musketeer","night-witch","pekka","phoenix-big",
            "phoenix-egg","phoenix-small","poison","prince","princess","queen-tower","rage","ram-rider","rascal-boy","rascal-girl",
            "rocket","royal-delivery","royal-ghost","royal-giant","royal-giant-evolution","royal-guardian","royal-hog","royal-recruit","royal-recruit-evolution","skeleton",
            "skeleton-barrel","skeleton-dragon","skeleton-evolution","skeleton-king","skeleton-king-skill","sparky","spear-goblin","tesla","tesla-evolution","the-log",
            "tombstone","tornado","valkyrie","valkyrie-evolution","wall-breaker","wall-breaker-evolution","witch","wizard","x-bow","zap",
            "zappy","baby-dragon-evolution","berserker","boss-bandit","cannon-evolution","dart-goblin-evolution","electro-dragon-evolution","executioner-evolution","furnace-evolution","giant-snowball-evolution",
            "goblin-barrel-evolution","goblin-cage-evolution","goblin-curse","goblin-demolisher","goblin-drill-evolution","goblin-giant-evolution","goblin-machine","goblinstein","hunter-evolution","inferno-dragon-evolution",
            "lumberjack-evolution","mega-knight-evolution","musketeer-evolution","pekka-evolution","royal-ghost-evolution","royal-hog-evolution","rune-giant","skeleton-barrel-evolution","spirit-empress","suspicious-bush",
            "vines","void","witch-evolution","wizard-evolution","elite-barbarian-evolution","minion-evolution","princess-evolution","zap-evolution","barbarian-launcher","party-hut",
            "party-rocket","raging-prince","rocket-silo","ronin","baby-goblin","minion-giant","skeleton-army-evolution"
        )
    }

    private val environment = OrtEnvironment.getEnvironment()
    private val session: OrtSession

    init {
        val model = context.assets.open("best.onnx").use { it.readBytes() }
        val options = OrtSession.SessionOptions()
        var nnapiRequested = false
        try {
            // Use Android NNAPI where the device driver supports the graph.
            // Reflection keeps compatibility with ONNX Runtime builds that omit
            // the NNAPI method; unsupported operators are delegated by ORT.
            nnapiRequested = runCatching {
                options.javaClass.getMethod("addNnapi").invoke(options)
                true
            }.getOrDefault(false)
            session = try {
                environment.createSession(model, options).also {
                    Log.i("ClashAI", if (nnapiRequested) "YOLO backend: NNAPI requested (Snapdragon/Android)" else "YOLO backend: CPU")
                }
            } catch (acceleratedError: Exception) {
                Log.w("ClashAI", "NNAPI session failed; falling back to CPU", acceleratedError)
                environment.createSession(model).also {
                    Log.i("ClashAI", "YOLO backend: CPU fallback")
                }
            }
        } finally {
            options.close()
        }
    }

    fun detect(source: Bitmap): List<ArenaDetection> {
        val scale = min(INPUT_SIZE.toFloat() / source.width, INPUT_SIZE.toFloat() / source.height)
        val resizedW = (source.width * scale).toInt()
        val resizedH = (source.height * scale).toInt()
        val padX = (INPUT_SIZE - resizedW) / 2
        val padY = (INPUT_SIZE - resizedH) / 2
        val resized = Bitmap.createScaledBitmap(source, resizedW, resizedH, true)
        val canvas = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888)
        val graphics = android.graphics.Canvas(canvas)
        graphics.drawColor(android.graphics.Color.BLACK)
        graphics.drawBitmap(resized, padX.toFloat(), padY.toFloat(), null)

        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        canvas.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        val input = FloatArray(3 * INPUT_SIZE * INPUT_SIZE)
        val plane = INPUT_SIZE * INPUT_SIZE
        for (i in pixels.indices) {
            val p = pixels[i]
            input[i] = ((p shr 16) and 0xff) / 255f
            input[plane + i] = ((p shr 8) and 0xff) / 255f
            input[2 * plane + i] = (p and 0xff) / 255f
        }
        resized.recycle()
        canvas.recycle()

        val tensor = OnnxTensor.createTensor(
            environment,
            FloatBuffer.wrap(input),
            longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong())
        )
        try {
            val inputName = session.inputNames.first()
            val output = session.run(mapOf(inputName to tensor))
            try {
                val raw = output[0].value as Array<*>
                val matrix = raw[0] as Array<FloatArray>
                if (matrix.size != 4 + CLASS_COUNT) return emptyList()
                val count = matrix[0].size
                val candidates = ArrayList<ArenaDetection>()
                for (i in 0 until count) {
                    var bestClass = -1
                    var bestScore = 0f
                    for (c in 0 until CLASS_COUNT) {
                        val score = matrix[4 + c][i]
                        if (score > bestScore) {
                            bestScore = score
                            bestClass = c
                        }
                    }
                    if (bestScore < CONFIDENCE_THRESHOLD || bestClass !in LABELS.indices) continue
                    val cx = matrix[0][i]
                    val cy = matrix[1][i]
                    val w = matrix[2][i]
                    val h = matrix[3][i]
                    val x1 = ((cx - w / 2f - padX) / scale).coerceIn(0f, source.width.toFloat())
                    val y1 = ((cy - h / 2f - padY) / scale).coerceIn(0f, source.height.toFloat())
                    val x2 = ((cx + w / 2f - padX) / scale).coerceIn(0f, source.width.toFloat())
                    val y2 = ((cy + h / 2f - padY) / scale).coerceIn(0f, source.height.toFloat())
                    if (x2 <= x1 || y2 <= y1) continue
                    candidates.add(ArenaDetection(bestClass, LABELS[bestClass], bestScore,
                        (x1+x2)/2f, (y1+y2)/2f, x2-x1, y2-y1))
                }
                return nonMaximumSuppression(candidates)
            } finally {
                output.close()
            }
        } finally {
            tensor.close()
        }
    }

    private fun nonMaximumSuppression(items: List<ArenaDetection>): List<ArenaDetection> {
        val kept = ArrayList<ArenaDetection>()
        for (candidate in items.sortedByDescending { it.confidence }) {
            if (kept.none { it.classId == candidate.classId && iou(it, candidate) > IOU_THRESHOLD }) {
                kept.add(candidate)
            }
        }
        return kept
    }

    private fun iou(a: ArenaDetection, b: ArenaDetection): Float {
        val left = max(a.centerX-a.width/2f, b.centerX-b.width/2f)
        val top = max(a.centerY-a.height/2f, b.centerY-b.height/2f)
        val right = min(a.centerX+a.width/2f, b.centerX+b.width/2f)
        val bottom = min(a.centerY+a.height/2f, b.centerY+b.height/2f)
        val intersection = max(0f,right-left)*max(0f,bottom-top)
        val union = a.width*a.height+b.width*b.height-intersection
        return if (union <= 0f) 0f else intersection/union
    }

    override fun close() {
        session.close()
    }
}
