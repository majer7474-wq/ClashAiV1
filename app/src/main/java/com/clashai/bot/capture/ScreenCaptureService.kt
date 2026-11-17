package com.clashai.bot.capture

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.util.Log
import com.clashai.bot.ai.AIController
import com.clashai.bot.ai.Card
import com.clashai.bot.input.ShizukuInput
import com.clashai.bot.vision.FrameAnalyzer
import com.clashai.bot.vision.ImageMetricsCalculator
import com.clashai.bot.vision.VirtualGameSpace

class ScreenCaptureService : Service() {
    companion object {
        const val EXTRA_RESULT = "capture_result"
        const val EXTRA_DATA = "capture_data"
        const val EXTRA_AUTOPLAY = "autoplay_enabled"
        const val ACTION_STOP = "clashai.STOP_CAPTURE"
        private const val CHANNEL = "clashai_capture"
        private const val NOTIFICATION_ID = 701
        private const val TAG = "ClashAI"
    }

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private lateinit var analyzer: FrameAnalyzer
    private val aiController = AIController()
    private var lastProcessedFrameMs = 0L
    private var autoplayEnabled = false
    private var lastPlayedCard: Card? = null
    private var lastPlayAtMs = 0L

    override fun onCreate() {
        super.onCreate()
        analyzer = FrameAnalyzer(this)
    }

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            autoplayEnabled = false
            stopCapture()
            return START_NOT_STICKY
        }

        createNotificationChannel()
        val notification = Notification.Builder(this, CHANNEL)
            .setContentTitle("ClashAI")
            .setContentText("Autoplay analiza igru")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else startForeground(NOTIFICATION_ID, notification)

        val resultCode = intent?.getIntExtra(EXTRA_RESULT, Activity.RESULT_CANCELED)
            ?: Activity.RESULT_CANCELED
        @Suppress("DEPRECATION")
        val data = intent?.getParcelableExtra<Intent>(EXTRA_DATA)

        autoplayEnabled = intent?.getBooleanExtra(EXTRA_AUTOPLAY, false) == true
        if (autoplayEnabled && !ShizukuInput.isReady()) {
            Log.e(TAG, "Autoplay zaustavljen: Shizuku nije spreman")
            autoplayEnabled = false
        }
        if (resultCode != Activity.RESULT_OK || data == null) {
            Log.e(TAG, "Nedostaje dozvola za snimanje")
            stopCapture()
            return START_NOT_STICKY
        }
        startCapture(resultCode, data)
        return START_NOT_STICKY
    }

    private fun startCapture(resultCode: Int, data: Intent) {
        try {
            aiController.reset()
            lastProcessedFrameMs = 0L
            lastPlayedCard = null
            lastPlayAtMs = 0L

            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = manager.getMediaProjection(resultCode, data)
            val metrics = resources.displayMetrics
            val width = metrics.widthPixels
            val height = metrics.heightPixels
            val density = metrics.densityDpi

            thread = HandlerThread("ClashAI-Capture").apply { start() }
            handler = Handler(thread!!.looper)
            reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

            projection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.i(TAG, "MediaProjection zaustavljen")
                    stopCapture()
                }
            }, handler)

            reader?.setOnImageAvailableListener({ source -> processFrame(source) }, handler)
            virtualDisplay = projection?.createVirtualDisplay(
                "ClashAI-Display", width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader?.surface, null, handler
            )
            Log.i(TAG, "Snimanje pokrenuto; virtualni koordinatni sustav 1080x2400; autoplay=$autoplayEnabled")
        } catch (e: Exception) {
            Log.e(TAG, "Capture error", e)
            stopCapture()
        }
    }

    private fun processFrame(source: ImageReader) {
        val image = source.acquireLatestImage() ?: return
        val now = SystemClock.elapsedRealtime()
        if (now - lastProcessedFrameMs < 220L) {
            image.close()
            return
        }
        lastProcessedFrameMs = now

        var padded: Bitmap? = null
        var frame: Bitmap? = null
        try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val width = image.width
            val height = image.height
            val paddedWidth = rowStride / pixelStride

            padded = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
            padded.copyPixelsFromBuffer(buffer)
            frame = Bitmap.createBitmap(padded, 0, 0, width, height)

            val metrics = ImageMetricsCalculator.calculate(frame)
            val observation = analyzer.analyze(frame)
            val decision = aiController.process(observation)

            Log.d(TAG, "Frame ${width}x${height} | Brightness=${metrics.brightness} | " +
                "Elixir=${observation.elixir} OCR=${observation.confidence} | " +
                "Hand=${observation.visibleCards.size}/4 (${observation.handConfidence}) | " +
                "Decision=${decision.card?.displayName ?: "WAIT"}: ${decision.reason}")

            if (autoplayEnabled && decision.card != null) {
                executeDecision(decision.card, observation, width, height)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Frame processing error", e)
        } finally {
            frame?.recycle()
            padded?.recycle()
            image.close()
        }
    }

    private fun executeDecision(card: Card, observation: com.clashai.bot.vision.ScreenObservation,
                                width: Int, height: Int) {
        if (!ShizukuInput.isReady()) {
            autoplayEnabled = false
            Log.e(TAG, "Autoplay stop: Shizuku permission lost")
            return
        }
        val slot = observation.visibleCards.indexOf(card)
        if (slot !in 0..3) return

        val now = SystemClock.elapsedRealtime()
        if (card == lastPlayedCard && now - lastPlayAtMs < 2800L) return

        val fromVirtual = VirtualGameSpace.handSlot(slot)
        val toVirtual = if (card == Card.HOG_RIDER) {
            VirtualGameSpace.placementForHog()
        } else {
            VirtualGameSpace.placementForCycle()
        }
        val from = VirtualGameSpace.toScreen(fromVirtual.x.toFloat(), fromVirtual.y.toFloat(), width, height)
        val to = VirtualGameSpace.toScreen(toVirtual.x.toFloat(), toVirtual.y.toFloat(), width, height)

        val sent = ShizukuInput.swipe(from.x, from.y, to.x, to.y, 180)
        if (sent) {
            lastPlayedCard = card
            lastPlayAtMs = now
            aiController.onActionSent(card)
            Log.i(TAG, "PLAY SENT: ${card.displayName} slot=${slot} virtual=(${fromVirtual.x},${fromVirtual.y})->(${toVirtual.x},${toVirtual.y})")
        } else {
            autoplayEnabled = false
            Log.e(TAG, "INPUT FAILED - autoplay stopped")
        }
    }

    private fun defensivePlacement(card: Card, observation: com.clashai.bot.vision.ScreenObservation): VirtualGameSpace.Point {
        val enemyX = observation.enemyTargetX ?: 540f
        val enemyY = observation.enemyTargetY ?: 1050f
        val laneX = enemyX.coerceIn(250f, 830f)
        return when (card) {
            Card.CANNON -> VirtualGameSpace.Point(540, 1370)
            Card.MUSKETEER -> VirtualGameSpace.Point(
                if (laneX < 540f) 360 else 720, 1660
            )
            Card.ICE_GOLEM -> VirtualGameSpace.Point(laneX.toInt(), 1420)
            Card.SKELETONS, Card.ICE_SPIRIT -> VirtualGameSpace.Point(laneX.toInt(), 1370)
            Card.LOG, Card.FIREBALL -> VirtualGameSpace.Point(
                laneX.toInt(), enemyY.coerceIn(650f, 1320f).toInt()
            )
            else -> VirtualGameSpace.placementForCycle()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL, "ClashAI screen capture", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun stopCapture() {
        autoplayEnabled = false
        try {
            virtualDisplay?.release()
            virtualDisplay = null
            reader?.close()
            reader = null
            projection?.stop()
            projection = null
            thread?.quitSafely()
            thread = null
            handler = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } catch (e: Exception) {
            Log.e(TAG, "Stop error", e)
        }
    }

    override fun onDestroy() {
        if (::analyzer.isInitialized) analyzer.close()
        stopCapture()
        super.onDestroy()
    }
}
