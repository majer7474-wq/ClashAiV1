package com.clashai.bot

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.graphics.Color
import android.view.Gravity
import android.widget.*
import java.util.ArrayDeque
import android.content.Intent
import android.content.Context
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.media.projection.MediaProjectionManager
import android.os.Build
import com.clashai.bot.ai.AIController
import com.clashai.bot.ai.Card
import com.clashai.bot.ai.Threat
import com.clashai.bot.capture.ScreenCaptureService
import com.clashai.bot.input.ShizukuInput
import com.clashai.bot.vision.ScreenObservation
import com.clashai.bot.vision.ArenaDetection

class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private val ai = AIController()
    private lateinit var status: TextView
    private lateinit var liveState: TextView
    private lateinit var autoSwitch: Switch
    private var autoplay = false
    private var latestObservation: ScreenObservation? = null
    private var lastPlayedAt = 0L
    private var lastPlayedCard: Card? = null
    // A card cannot be replayed until the hand visibly cycles and that card returns.
    private var cardAwaitingRotation: Card? = null
    private var useRightHogLane = false
    private var receiverRegistered = false
    private val recentFrames = ArrayDeque<ScreenObservation>()

    private val observationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ScreenCaptureService.ACTION_OBSERVATION) return
            val captureError = intent.getStringExtra(ScreenCaptureService.EXTRA_ERROR)
            if (!captureError.isNullOrBlank()) {
                status.text = "SCREEN ANALYSIS ERROR: $captureError"
                liveState.text = "ANALIZA NIJE POKRENUTA. PROVJERI MODEL I ASSET DATOTEKE."
                return
            }
            val elixirValue = intent.getFloatExtra(ScreenCaptureService.EXTRA_ELIXIR, -1f)
            val handNames = intent.getStringExtra(ScreenCaptureService.EXTRA_HAND_CARDS).orEmpty()
            val cards = handNames.split(",").mapNotNull { name ->
                runCatching { Card.valueOf(name) }.getOrNull()
            }
            val threat = runCatching {
                Threat.valueOf(intent.getStringExtra(ScreenCaptureService.EXTRA_THREAT) ?: "NONE")
            }.getOrDefault(Threat.NONE)
            val capturedAt = intent.getLongExtra(
                ScreenCaptureService.EXTRA_CAPTURED_AT, System.currentTimeMillis()
            )
            val detectionData = intent.getStringExtra(ScreenCaptureService.EXTRA_DETECTIONS).orEmpty()
            val detections = detectionData.split(";").mapNotNull { item ->
                val parts = item.split(",")
                if (parts.size != 4) null else runCatching {
                    ArenaDetection(
                        classId = -1,
                        label = parts[0],
                        confidence = parts[3].toFloat(),
                        centerX = parts[1].toFloat(),
                        centerY = parts[2].toFloat(),
                        width = 0f,
                        height = 0f
                    )
                }.getOrNull()
            }
            val observation = ScreenObservation(
                elixir = elixirValue.takeIf { it in 0f..10f },
                visibleCards = cards,
                enemyThreat = threat,
                confidence = intent.getFloatExtra(ScreenCaptureService.EXTRA_CONFIDENCE, 0f),
                capturedAtMs = capturedAt,
                handConfidence = intent.getFloatExtra(ScreenCaptureService.EXTRA_HAND_CONFIDENCE, 0f),
                handComplete = intent.getBooleanExtra(ScreenCaptureService.EXTRA_HAND_COMPLETE, false),
                detectedCardIds = emptyList(),
                detectedText = intent.getStringExtra(ScreenCaptureService.EXTRA_OCR_TEXT).orEmpty(),
                handDiagnostic = intent.getStringExtra(ScreenCaptureService.EXTRA_HAND_DIAGNOSTIC).orEmpty(),
                detections = detections,
                frameWidth = intent.getIntExtra(ScreenCaptureService.EXTRA_FRAME_WIDTH, 1080),
                frameHeight = intent.getIntExtra(ScreenCaptureService.EXTRA_FRAME_HEIGHT, 2400)
            )
            latestObservation = observation
            recentFrames.addLast(observation)
            while (recentFrames.size > 3) recentFrames.removeFirst()
            liveState.text = "ELIKSIR: ${observation.elixir?.toString() ?: "?"}/10" +
                "\nOCR: ${observation.detectedText.ifBlank { "NEMA TEKSTA" }.take(90)}" +
                "\nKARTE: " + (if (observation.handComplete) cards.joinToString { it.displayName } else "NEPOTPUNO OČITAVANJE") +
                "\nHAND: ${observation.handDiagnostic}" +
                "\nPRIJETNJA: ${threat.name} | CONF: ${"%.2f".format(observation.handConfidence)}"
            if (autoplay) {
                if (hasStableConsensus()) autoplayStep(observation)
                else status.text = "WAIT: VALIDATING 3 CONSECUTIVE FRAMES"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(Color.rgb(13, 18, 30))
        }
        fun heading(text: String) = TextView(this).apply {
            this.text = text
            textSize = 19f
            setTextColor(Color.WHITE)
            setPadding(0, 20, 0, 10)
        }
        fun button(text: String, action: () -> Unit) = Button(this).apply {
            this.text = text
            setOnClickListener { action() }
        }
        val title = TextView(this).apply {
            text = "CLASH AI"
            textSize = 30f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(0, 12, 0, 12)
        }
        status = TextView(this).apply {
            text = "SYSTEM: READY"
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(Color.GREEN)
            setPadding(8, 12, 8, 12)
        }
        liveState = TextView(this).apply {
            text = "ČEKAM NA OČITAVANJE MEČA"
            textSize = 16f
            setTextColor(Color.WHITE)
            setPadding(8, 16, 8, 16)
        }
        autoSwitch = Switch(this).apply {
            text = "ENABLE AUTOPLAY"
            textSize = 19f
            setTextColor(Color.WHITE)
            setPadding(0, 15, 0, 15)
            setOnCheckedChangeListener { _, enabled -> setAutoplay(enabled) }
        }
        root.addView(title)
        root.addView(status)
        root.addView(button("OPEN LIVE MIRROR SIMULATOR") { startActivity(Intent(this, SimulatorActivity::class.java)) })
        root.addView(heading("LIVE GAME STATE"))
        root.addView(liveState)
        root.addView(heading("SCREEN ANALYSIS"))
        root.addView(button("START SCREEN ANALYSIS") { requestCapture() })
        root.addView(button("STOP SCREEN ANALYSIS") {
            stopAutoplay()
            recentFrames.clear()
            latestObservation = null
            startService(Intent(this, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_STOP
            })
            status.text = "ANALIZA ZAUSTAVLJENA"
        })
        root.addView(heading("AUTOPLAY"))
        root.addView(autoSwitch)
        root.addView(button("STOP AUTOPLAY") { autoSwitch.isChecked = false })
        root.addView(button("CHECK SHIZUKU") {
            status.text = if (ShizukuInput.isReady()) "SHIZUKU: CONNECTED" else "SHIZUKU: NOT READY"
        })
        root.addView(button("REQUEST SHIZUKU PERMISSION") {
            ShizukuInput.requestPermission()
            status.text = "PROVJERAVAM SHIZUKU DOZVOLU"
        })
        setContentView(ScrollView(this).apply { addView(root) })
        registerObservationReceiver()
        status.text = if (ShizukuInput.isReady()) "SHIZUKU: CONNECTED" else "SHIZUKU: NOT READY"
    }

    private fun registerObservationReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter(ScreenCaptureService.ACTION_OBSERVATION)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(observationReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") registerReceiver(observationReceiver, filter)
        receiverRegistered = true
    }

    private fun hasStableConsensus(): Boolean {
        if (recentFrames.size < 3) return false
        val frames = recentFrames.toList()
        val first = frames.first()
        val last = frames.last()
        if (frames.any { it.capturedAtMs <= 0L || System.currentTimeMillis() - it.capturedAtMs > 3500L }) return false
        // Require three genuinely separate, ordered captures; duplicated/stale broadcasts do not count.
        if (frames[1].capturedAtMs - frames[0].capturedAtMs !in 150L..3000L) return false
        if (frames[2].capturedAtMs - frames[1].capturedAtMs !in 150L..3000L) return false
        if (frames.last().capturedAtMs - frames.first().capturedAtMs < 400L) return false
        if (frames.any { !it.handComplete || it.handConfidence < 0.70f || it.visibleCards.size != 4 }) return false
        // Arena detections fluctuate between frames; only the hand must remain stable.
        // Requiring an identical threat label blocked autoplay whenever YOLO flickered.
        if (frames.any { it.visibleCards != first.visibleCards }) return false
        val elixirs = frames.mapNotNull { it.elixir }
        // One OCR miss should not invalidate otherwise stable frames; the AI
        // controller extrapolates briefly from the last confirmed reading.
        if (elixirs.size < 2 || elixirs.any { it !in 0f..10f }) return false
        if (elixirs.maxOrNull()!! - elixirs.minOrNull()!! > 0.90f) return false
        return last.capturedAtMs >= first.capturedAtMs
    }

    private fun setAutoplay(enabled: Boolean) {
        if (enabled && !ShizukuInput.isReady()) {
            autoSwitch.isChecked = false
            status.text = "AUTOPLAY BLOKIRAN: SHIZUKU JE OBAVEZAN"
            return
        }
        autoplay = enabled
        if (!enabled) {
            status.text = "AUTOPLAY STOPPED"
            return
        }
        status.text = "AUTOPLAY ACTIVE — ČEKAM 3 NOVA STABILNA OČITAVANJA"
    }

    private fun stopAutoplay() {
        // STOP must invalidate and terminate an in-flight Shizuku input command.
        ShizukuInput.cancelAll()
        autoplay = false
        if (::autoSwitch.isInitialized) autoSwitch.isChecked = false
        status.text = "AUTOPLAY STOPPED"
    }

    private fun autoplayStep(observation: ScreenObservation) {
        if (!autoplay) return
        if (!ShizukuInput.isReady()) {
            stopAutoplay()
            status.text = "INPUT LOST — AUTOPLAY STOPPED"
            return
        }
        val action = ai.process(observation)
        val card = action.card
        if (card == null) {
            status.text = "WAIT: ${action.reason}"
            return
        }
        val now = System.currentTimeMillis()
        if (now - observation.capturedAtMs > 900L) {
            status.text = "WAIT: STALE SCREEN"
            return
        }
        val awaiting = cardAwaitingRotation
        if (awaiting != null) {
            if (awaiting in observation.visibleCards) {
                status.text = "WAIT: $awaiting JOŠ NIJE IZAŠAO IZ RUKE"
                return
            }
            cardAwaitingRotation = null
        }
        if (card == lastPlayedCard && now - lastPlayedAt < 1800L) return
        val slot = observation.visibleCards.indexOf(card)
        if (slot !in 0..3) return

        // Match the exact hand ROIs used by HandCardRecognizer. The old
        // equal-width 6%-94% formula tapped well left of the actual cards.
        val metrics = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val scale = minOf(width / 1080f, height / 2400f)
        val offsetX = (width - 1080f * scale) / 2f
        val offsetY = (height - 2400f * scale) / 2f
        val slotCenters = floatArrayOf(340f, 540f, 740f, 950f)
        val slotX = (offsetX + slotCenters[slot] * scale).toInt()
        val slotY = (offsetY + 2170f * scale).toInt()
        // AI placements are normalized across the complete captured game frame.
        // Do NOT map them through the old 1080x2400 portrait canvas: on a landscape
        // tablet that compressed every arena target into the middle third of the screen.
        val target = com.clashai.bot.input.ScreenCoordinateMapper.normalizedToPixel(
            action.placementX ?: 0.50f,
            action.placementY ?: 0.70f,
            width,
            height
        )
        val targetX = target.x
        val targetY = target.y
        val sent = ShizukuInput.swipe(slotX, slotY, targetX, targetY, 220) { success ->
            runOnUiThread {
                if (!autoplay) return@runOnUiThread
                if (success) {
                    ai.recordPlayed(card)
                    lastPlayedCard = card
                    cardAwaitingRotation = card
                    lastPlayedAt = now
                    status.text = "AI PLAY SENT: ${card.displayName}"
                } else {
                    stopAutoplay()
                    status.text = "INPUT FAILED — AUTOPLAY STOPPED"
                }
            }
        }
        if (!sent) status.text = "WAIT: INPUT BUSY OR SHIZUKU NOT READY"
    }

    private fun requestCapture() {
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(manager.createScreenCaptureIntent(), 4101)
    }

    @Deprecated("Android compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 4101) return
        if (resultCode == RESULT_OK && data != null) {
            val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
                putExtra(ScreenCaptureService.EXTRA_RESULT, resultCode)
                putExtra(ScreenCaptureService.EXTRA_DATA, data)
            }
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(serviceIntent) else startService(serviceIntent)
            status.text = "SCREEN ANALYSIS STARTED"
        } else status.text = "SCREEN PERMISSION DENIED"
    }

    override fun onDestroy() {
        stopAutoplay()
        if (receiverRegistered) {
            unregisterReceiver(observationReceiver)
            receiverRegistered = false
        }
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
