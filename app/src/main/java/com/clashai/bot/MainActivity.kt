package com.clashai.bot

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.*

import com.clashai.bot.capture.ScreenCaptureService

class MainActivity : Activity() {

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(28, 30, 28, 30)
            setBackgroundColor(Color.rgb(15, 20, 32))
        }

        val title = TextView(this).apply {
            text = "CLASH AI"
            textSize = 34f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        status = TextView(this).apply {
            text = "SYSTEM READY"
            textSize = 18f
            setTextColor(Color.GREEN)
            gravity = Gravity.CENTER
            setPadding(10, 40, 10, 40)
        }

        val start = Button(this).apply {
            text = "START SCREEN ANALYSIS"
            setOnClickListener { requestCapture() }
        }

        val stop = Button(this).apply {
            text = "STOP ANALYSIS"
            setOnClickListener {
                val intent = Intent(
                    this@MainActivity,
                    ScreenCaptureService::class.java
                ).apply {
                    action = ScreenCaptureService.ACTION_STOP
                }
                startService(intent)
                status.text = "ANALYSIS STOPPED"
            }
        }

        layout.addView(title)
        layout.addView(status)
        layout.addView(start)
        layout.addView(stop)

        setContentView(layout)
    }

    private fun requestCapture() {
        val manager = getSystemService(
            Context.MEDIA_PROJECTION_SERVICE
        ) as MediaProjectionManager

        startActivityForResult(
            manager.createScreenCaptureIntent(),
            4101
        )
    }

    @Deprecated("Android compatibility")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode != 4101) return

        if (resultCode == RESULT_OK && data != null) {
            val serviceIntent = Intent(
                this,
                ScreenCaptureService::class.java
            ).apply {
                putExtra(ScreenCaptureService.EXTRA_RESULT, resultCode)
                putExtra(ScreenCaptureService.EXTRA_DATA, data)
            }

            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }

            status.text = "SCREEN ANALYSIS STARTED"
        } else {
            status.text = "SCREEN PERMISSION DENIED"
        }
    }
}
