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
import com.clashai.bot.vision.FrameAnalyzer
import com.clashai.bot.vision.ImageMetricsCalculator

class ScreenCaptureService : Service() {

    companion object {
        const val EXTRA_RESULT = "capture_result"
        const val EXTRA_DATA = "capture_data"
        const val ACTION_STOP = "clashai.STOP_CAPTURE"
        private const val CHANNEL = "clashai_capture"
        private const val NOTIFICATION_ID = 701
        private const val TAG = "ClashAI"
        const val ACTION_OBSERVATION = "com.clashai.bot.OBSERVATION"
        const val EXTRA_ELIXIR = "observation_elixir"
        const val EXTRA_THREAT = "observation_threat"
        const val EXTRA_CONFIDENCE = "observation_confidence"
        const val EXTRA_DETECTION_COUNT = "observation_detection_count"
        const val EXTRA_CAPTURED_AT = "observation_captured_at"
        const val EXTRA_HAND_CARDS = "observation_hand_cards"
        const val EXTRA_HAND_COMPLETE = "observation_hand_complete"
        const val EXTRA_HAND_CONFIDENCE = "observation_hand_confidence"
        const val EXTRA_ELIXIR_CONFIDENCE = "observation_elixir_confidence"
        const val EXTRA_ERROR = "observation_error"
        const val EXTRA_OCR_TEXT = "observation_ocr_text"
        const val EXTRA_HAND_DIAGNOSTIC = "observation_hand_diagnostic"
        const val EXTRA_DETECTIONS = "observation_detections"
        const val EXTRA_FRAME_WIDTH = "observation_frame_width"
        const val EXTRA_FRAME_HEIGHT = "observation_frame_height"
    }

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var thread: HandlerThread? = null
    private var handler: Handler? = null

    private lateinit var analyzer: FrameAnalyzer
    private var lastInferenceAt = 0L

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        if (intent?.action == ACTION_STOP) {
            stopCapture()
            return START_NOT_STICKY
        }

        createNotificationChannel()

        val notification = Notification.Builder(this, CHANNEL)
            .setContentTitle("ClashAI")
            .setContentText("Analiza ekrana je aktivna")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        val resultCode = intent?.getIntExtra(EXTRA_RESULT, Activity.RESULT_CANCELED)
            ?: Activity.RESULT_CANCELED

        @Suppress("DEPRECATION")
        val data = intent?.getParcelableExtra<Intent>(EXTRA_DATA)

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
            analyzer = FrameAnalyzer(this)
            val manager =
                getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

            projection = manager.getMediaProjection(resultCode, data)

            // Capture the physical display, not the app's density-scaled
            // resource bounds. Shizuku taps use physical display pixels too.
            val metrics = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            (getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager)
                .defaultDisplay.getRealMetrics(metrics)
            val width = metrics.widthPixels
            val height = metrics.heightPixels
            val density = metrics.densityDpi

            thread = HandlerThread("ClashAI-Capture").apply { start() }
            handler = Handler(thread!!.looper)

            reader = ImageReader.newInstance(
                width,
                height,
                PixelFormat.RGBA_8888,
                2
            )

            projection?.registerCallback(
                object : MediaProjection.Callback() {
                    override fun onStop() {
                        Log.i(TAG, "MediaProjection zaustavljen")
                        stopCapture()
                    }
                },
                handler
            )

            reader?.setOnImageAvailableListener({ source ->
                processFrame(source)
            }, handler)

            virtualDisplay = projection?.createVirtualDisplay(
                "ClashAI-Display",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader?.surface,
                null,
                handler
            )

            Log.i(TAG, "Snimanje pokrenuto: ${width}x${height}")

        } catch (e: Exception) {
            Log.e(TAG, "Capture error", e)
            sendBroadcast(Intent(ACTION_OBSERVATION).apply {
                setPackage(packageName)
                putExtra(EXTRA_ERROR, "INIT: ${e.javaClass.simpleName}: ${e.message ?: "unknown"}")
            })
            stopCapture()
        }
    }

    private fun processFrame(source: ImageReader) {
        val image = source.acquireLatestImage() ?: return
        val now = System.currentTimeMillis()
        if (now - lastInferenceAt < 250L) {
            image.close()
            return
        }
        lastInferenceAt = now

        var paddedBitmap: Bitmap? = null
        var croppedBitmap: Bitmap? = null
        try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride

            val width = image.width
            val height = image.height
            val paddedWidth = rowStride / pixelStride

            val frameBitmap = Bitmap.createBitmap(
                paddedWidth,
                height,
                Bitmap.Config.ARGB_8888
            )
            paddedBitmap = frameBitmap
            frameBitmap.copyPixelsFromBuffer(buffer)

            val frameCrop = Bitmap.createBitmap(
                frameBitmap,
                0,
                0,
                width,
                height
            )
            croppedBitmap = frameCrop

            val metrics = ImageMetricsCalculator.calculate(frameCrop)
            val observation = analyzer.analyze(frameCrop)

            Log.d(
                TAG,
                "Frame ${width}x${height} | Brightness=${metrics.brightness} | " +
                    "Detections=${observation.detections.size} | " +
                    "Elixir=${observation.elixir} | Hand=${observation.visibleCards} " +
                    "complete=${observation.handComplete} handConf=${observation.handConfidence} | " +
                    "Threat=${observation.enemyThreat} | Confidence=${observation.confidence}"
            )
            sendBroadcast(Intent(ACTION_OBSERVATION).apply {
                setPackage(packageName)
                putExtra(EXTRA_ELIXIR, observation.elixir ?: -1f)
                putExtra(EXTRA_THREAT, observation.enemyThreat.name)
                putExtra(EXTRA_CONFIDENCE, observation.confidence)
                putExtra(EXTRA_DETECTION_COUNT, observation.detections.size)
                putExtra(EXTRA_CAPTURED_AT, observation.capturedAtMs)
                putExtra(EXTRA_HAND_CARDS, observation.visibleCards.joinToString(",") { it.name })
                putExtra(EXTRA_HAND_COMPLETE, observation.handComplete)
                putExtra(EXTRA_HAND_CONFIDENCE, observation.handConfidence)
                putExtra(EXTRA_OCR_TEXT, observation.detectedText)
                putExtra(EXTRA_HAND_DIAGNOSTIC, observation.handDiagnostic)
                putExtra(EXTRA_FRAME_WIDTH, width)
                putExtra(EXTRA_FRAME_HEIGHT, height)
                putExtra(EXTRA_DETECTIONS, observation.detections.joinToString(";") {
                    "${it.label},${it.centerX},${it.centerY},${it.confidence}"
                })
                putExtra(EXTRA_ELIXIR_CONFIDENCE, if (observation.elixir != null) 0.75f else 0f)
            })

        } catch (e: Exception) {
            Log.e(TAG, "Frame processing error", e)
        } finally {
            // Recycle even when OCR/ONNX throws; otherwise live analysis leaks bitmaps.
            croppedBitmap?.recycle()
            paddedBitmap?.recycle()
            image.close()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL,
                "ClashAI screen capture",
                NotificationManager.IMPORTANCE_LOW
            )

            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun stopCapture() {
        try {
            virtualDisplay?.release()
            virtualDisplay = null

            reader?.close()
            reader = null

            projection?.stop()
            projection = null

            if (::analyzer.isInitialized) {
                analyzer.close()
            }
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
        stopCapture()
        super.onDestroy()
    }
}
