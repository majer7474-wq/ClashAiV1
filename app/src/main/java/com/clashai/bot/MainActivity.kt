package com.clashai.bot

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.clashai.bot.capture.ScreenCaptureService
import com.clashai.bot.input.ShizukuInput

class MainActivity : Activity() {
    private lateinit var status: TextView

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
    }

    private fun makeButton(label: String, action: () -> Unit) =
        Button(this).apply {
            text = label
            setOnClickListener { action() }
        }

    private fun requestCapture() {
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(manager.createScreenCaptureIntent(), 4101)
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
