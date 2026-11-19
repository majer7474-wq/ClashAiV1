package dev.clashvision.agent

import android.app.*
import android.content.*
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*

class CaptureService : Service() {
    private val channelId = "capture"
    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null
    private var display: android.hardware.display.VirtualDisplay? = null
    private var handlerThread: HandlerThread? = null
    private var handReader: HandReader? = null
    private var frames = 0
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(channelId, "Snimanje zaslona", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
    private fun notification(message: String): Notification =
        Notification.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("Clash Vision Agent")
            .setContentText(message)
            .build()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") { stopSelf(); return START_NOT_STICKY }
        startForeground(1, notification("Pokretanje snimanja"), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        val resultCode = intent?.getIntExtra("resultCode", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        @Suppress("DEPRECATION")
        val data = if (Build.VERSION.SDK_INT >= 33)
            intent?.getParcelableExtra("captureData", Intent::class.java)
        else intent?.getParcelableExtra("captureData")
        if (resultCode != Activity.RESULT_OK || data == null) { stopSelf(); return START_NOT_STICKY }
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = manager.getMediaProjection(resultCode, data)
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi
        handlerThread = HandlerThread("vision-frames").also { it.start() }
        val handler = Handler(handlerThread!!.looper)
        reader = makeReader(width, height, handler)
        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { stopSelf() }
            override fun onCapturedContentResize(width: Int, height: Int) {
                val current = reader ?: return
                val activeDisplay = display ?: return
                if (width <= 0 || height <= 0 || (width == current.width && height == current.height)) return
                val replacement = makeReader(width, height, handler)
                activeDisplay.resize(width, height, density)
                activeDisplay.surface = replacement.surface
                reader = replacement
                current.setOnImageAvailableListener(null, null)
                current.close()
            }
        }, handler)
        display = projection?.createVirtualDisplay(
            "VisionCapture", width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader!!.surface, null, handler
        )
        return START_NOT_STICKY
    }
    private fun makeReader(width: Int, height: Int, handler: Handler): ImageReader {
        val newReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        newReader.setOnImageAvailableListener({ source ->
            if (source !== reader) return@setOnImageAvailableListener
            source.acquireLatestImage()?.use { image ->

                frames++
                if (frames % 30 == 0) {
                    val plane = image.planes[0]
                    val amount = ElixirGauge.estimate(plane.buffer, image.width, image.height,
                        plane.rowStride, plane.pixelStride)
                    val gauge = if (amount >= 0) "$amount/10" else "?"
                    val hand = if (amount < 0) emptyList() else runCatching {
                        val detector = handReader ?: HandReader(this).also { handReader = it }
                        detector.identify(image)
                    }.getOrElse {
                        android.util.Log.e("ClashVision", "Hand recognition failed", it)
                        emptyList()
                    }
                    val summary = if (hand.size == 5) {
                        " | " + hand.take(4).joinToString(", ") { it ?: "?" } + " | sljedeća: " + (hand.last() ?: "?")
                    } else ""
                    getSystemService(NotificationManager::class.java)
                        .notify(1, notification("Slika $frames | procjena eliksira: $gauge$summary"))
                }
            }
        }, handler)
        return newReader
    }

    override fun onDestroy() {
        display?.release()
        reader?.close()
        projection?.stop()
        handlerThread?.let { thread ->
            Handler(thread.looper).post { handReader?.close(); handReader = null }
            thread.quitSafely()
        }
        super.onDestroy()
    }
}
