package com.clashai.bot

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.Manifest
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionConfig
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.clashai.bot.capture.ScreenCaptureService
import com.clashai.bot.input.ShizukuInput

class MainActivity : Activity() {
    private lateinit var status: TextView

    private val statusHandler = Handler(Looper.getMainLooper())
    private val refreshStatus = object : Runnable {
        override fun run() {
            status.text = "Shizuku: " + (if (ShizukuInput.isReady()) "spojen" else "nije spojen") +
                "\n" + ScreenCaptureService.statusText
            statusHandler.postDelayed(this, 1000)
        }
    }

    override fun onResume() {
        super.onResume()
        statusHandler.post(refreshStatus)
    }

    override fun onPause() {
        statusHandler.removeCallbacks(refreshStatus)
        super.onPause()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 40, 32, 40)
        }
        val title = TextView(this).apply {
            text = "CLASH AI — HOG 2.6"
            textSize = 24f
            gravity = Gravity.CENTER
        }
        status = TextView(this).apply {
            text = "Provjeri Shizuku prije pokretanja."
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 28, 0, 28)
        }
        root.addView(title)
        root.addView(status)
        root.addView(makeButton("PROVJERI SHIZUKU") {
            status.text = if (ShizukuInput.isReady()) "SHIZUKU: SPOJEN" else "SHIZUKU: NIJE SPOJEN"
        })
        root.addView(makeButton("ZATRAŽI SHIZUKU DOZVOLU") {
            ShizukuInput.requestPermission()
            status.text = "Odobri zahtjev u Shizuku prozoru, pa ponovno provjeri."
        })
        root.addView(makeButton("POKRENI AUTOPLAY") {
            if (!ShizukuInput.isReady()) {
                status.text = "Prvo spoji Shizuku i odobri dozvolu."
            } else {
                requestCapture()
            }
        })
        root.addView(makeButton("ZAUSTAVI AUTOPLAY") {
            val stop = Intent(this, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_STOP
            }
            startService(stop)
            status.text = "AUTOPLAY ZAUSTAVLJEN"
        })
        setContentView(root)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 4102)
        }
    }

    private fun makeButton(label: String, action: () -> Unit) =
        Button(this).apply {
            text = label
            setOnClickListener { action() }
        }

    private fun requestCapture() {
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val captureIntent = if (Build.VERSION.SDK_INT >= 34)
            manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
        else manager.createScreenCaptureIntent()
        startActivityForResult(captureIntent, 4101)
    }

    @Deprecated("Android compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 4101) return
        if (resultCode != RESULT_OK || data == null) {
            status.text = "DOZVOLA ZA SNIMANJE NIJE ODOBRENA"
            return
        }
        val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
            putExtra(ScreenCaptureService.EXTRA_RESULT, resultCode)
            putExtra(ScreenCaptureService.EXTRA_DATA, data)
            putExtra(ScreenCaptureService.EXTRA_AUTOPLAY, true)
        }
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(serviceIntent)
        else startService(serviceIntent)
        status.text = "AUTOPLAY POKRENUT — prati logove za prepoznavanje i poteze"
    }
}
