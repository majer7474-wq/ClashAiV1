package dev.clashvision.agent

import android.app.*
import android.content.*
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.view.WindowManager

class CaptureService : Service() {
    private val channelId = "capture"
    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null
    private var display: android.hardware.display.VirtualDisplay? = null
    private var handlerThread: HandlerThread? = null
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
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi
        handlerThread = HandlerThread("vision-frames").also { it.start() }
        val handler = Handler(handlerThread!!.looper)
        reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { stopSelf() }
        }, handler)
        reader?.setOnImageAvailableListener({ source ->
            source.acquireLatestImage()?.use { image ->
                // Proof of frame acquisition only. No card detection or learning yet.
                frames++
                if (frames % 30 == 0) {
                    getSystemService(NotificationManager::class.java)
                        .notify(1, notification("Obrađeno slika: $frames | ${image.width}x${image.height}"))
                }
            }
        }, handler)
        display = projection?.createVirtualDisplay(
            "VisionCapture", width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader!!.surface, null, handler
        )
        return START_NOT_STICKY
    }
    override fun onDestroy() {
        display?.release()
        reader?.close()
        projection?.stop()
        handlerThread?.quitSafely()
        super.onDestroy()
    }
}
