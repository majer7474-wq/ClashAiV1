package dev.clashvision.agent

import android.app.Activity
import android.content.*

import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.*
import android.view.ViewGroup

class MainActivity : Activity() {
    private lateinit var status: TextView
    private val requestCode = 501
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 28, 28, 28)
        }
        status = TextView(this).apply { text = "Snimanje zaslona je zaustavljeno." }
        val start = Button(this).apply {
            text = "Dopusti snimanje zaslona"
            setOnClickListener {
                val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                startActivityForResult(manager.createScreenCaptureIntent(), requestCode)
            }
        }
        val stop = Button(this).apply {
            text = "Zaustavi snimanje"
            setOnClickListener {
                startService(Intent(this@MainActivity, CaptureService::class.java).setAction("STOP"))
                status.text = "Snimanje zaustavljeno"
            }
        }
        layout.addView(status)
        val ids = listOf("general", "il", "active_il", "hog26_proactive", "hog26_measured")
        val models = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, ids)
        }
        val modelStatus = TextView(this)
        val checkModel = Button(this).apply {
            text = "Testiraj model na spremljenom stanju"
            setOnClickListener {
                val id = models.selectedItem.toString()
                isEnabled = false
                modelStatus.text = "Učitavanje: $id"
                Thread {
                    val message = try {
                        val ms = FirstLightReference.check(applicationContext, id)
                        "$id: testni potez potvrđen (${ms} ms)."
                    } catch (error: Exception) {
                        "Model: ${error.cause?.message ?: error.message}"
                    }
                    runOnUiThread {
                        if (!isDestroyed) {
                            modelStatus.text = message
                            isEnabled = true
                        }
                    }
                }.start()
            }
        }
        layout.addView(models)
        layout.addView(checkModel)
        layout.addView(modelStatus)
        layout.addView(start, ViewGroup.LayoutParams(-1, -2))
        layout.addView(stop, ViewGroup.LayoutParams(-1, -2))
        setContentView(layout)
    }
    @Deprecated("Android callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == this.requestCode && resultCode == RESULT_OK && data != null) {
            val service = Intent(this, CaptureService::class.java)
                .putExtra("resultCode", resultCode)
                .putExtra("captureData", data)
            startForegroundService(service)
            status.text = "Snimanje pokrenuto; provjeri obavijest za broj obrađenih slika."
        }
    }
}
