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
    }

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var thread: HandlerThread? = null
    private var handler: Handler? = null

    private val analyzer = FrameAnalyzer()

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
            val manager =
                getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

            projection = manager.getMediaProjection(resultCode, data)

            val metrics = resources.displayMetrics
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
            stopCapture()
        }
    }

    private fun processFrame(source: ImageReader) {
        val image = source.acquireLatestImage() ?: return

        try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride

            val width = image.width
            val height = image.height
            val paddedWidth = rowStride / pixelStride

            val bitmap = Bitmap.createBitmap(
                paddedWidth,
                height,
                Bitmap.Config.ARGB_8888
            )

            bitmap.copyPixelsFromBuffer(buffer)

            val cropped = Bitmap.createBitmap(
                bitmap,
                0,
                0,
                width,
                height
            )

            val metrics = ImageMetricsCalculator.calculate(cropped)
            val observation = analyzer.analyze(cropped)

            Log.d(
                TAG,
                "Frame ${width}x${height} | " +
                "Brightness=${metrics.brightness} | " +
                "Confidence=${observation.confidence}"
            )

            cropped.recycle()
            bitmap.recycle()

        } catch (e: Exception) {
            Log.e(TAG, "Frame processing error", e)
        } finally {
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
