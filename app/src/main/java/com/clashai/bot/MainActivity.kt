package com.clashai.bot

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.*
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.content.Context
import android.os.Build

import com.clashai.bot.ai.Card
import com.clashai.bot.ai.Threat
import com.clashai.bot.capture.ScreenCaptureService
import com.clashai.bot.input.ShizukuInput

class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())

    private lateinit var status: TextView
    private lateinit var elixirText: TextView
    private lateinit var elixirBar: SeekBar
    private lateinit var autoSwitch: Switch
    private lateinit var threatSpinner: Spinner

    private val handSpinners = mutableListOf<Spinner>()

    private var autoplay = false
    private var lastPlayed = ""
    private var lastPlayTime = 0L

    private val cardNames = listOf(
        "—",
        "Hog Rider",
        "Musketeer",
        "Cannon",
        "Fireball",
        "Skeletons",
        "Ice Golem",
        "Ice Spirit",
        "The Log"
    )

    private val threats = listOf(
        "Bez prijetnje",
        "Visoka opasnost",
        "Tank",
        "Swarm",
        "Zračni napad",
        "Win condition"
    )

    private val autoTask = object : Runnable {
        override fun run() {
            if (autoplay) {
                autoplayStep()
                handler.postDelayed(this, 700)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 22, 22, 22)
            setBackgroundColor(Color.rgb(13, 18, 30))
        }

        val scroll = ScrollView(this)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8, 8, 8, 30)
        }

        fun heading(text: String): TextView {
            return TextView(this).apply {
                this.text = text
                textSize = 19f
                setTextColor(Color.WHITE)
                setPadding(0, 22, 0, 10)
            }
        }

        fun button(text: String, action: () -> Unit): Button {
            return Button(this).apply {
                this.text = text
                setOnClickListener { action() }
            }
        }

        val title = TextView(this).apply {
            text = "CLASH AI"
            textSize = 30f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(0, 15, 0, 15)
        }

        status = TextView(this).apply {
            text = "SYSTEM: READY"
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(Color.GREEN)
            setPadding(8, 14, 8, 14)
        }

        panel.addView(title)
        panel.addView(status)

        panel.addView(heading("SHIZUKU CONTROL"))

        panel.addView(button("CHECK SHIZUKU") {
            refreshShizuku()
        })

        panel.addView(button("REQUEST SHIZUKU PERMISSION") {
            ShizukuInput.requestPermission()
            status.text = "Provjeravam Shizuku dozvolu..."
        })

        panel.addView(heading("BATTLE INPUT"))

        elixirText = TextView(this).apply {
            text = "Elixir: 5"
            textSize = 17f
            setTextColor(Color.WHITE)
        }
        panel.addView(elixirText)

        elixirBar = SeekBar(this).apply {
            max = 10
            progress = 5
            setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(
                        seekBar: SeekBar?,
                        progress: Int,
                        fromUser: Boolean
                    ) {
                        elixirText.text = "Elixir: $progress"
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                }
            )
        }
        panel.addView(elixirBar)

        panel.addView(heading("YOUR FOUR CARD SLOTS"))

        repeat(4) { index ->
            val label = TextView(this).apply {
                text = "SLOT ${index + 1}"
                setTextColor(Color.LTGRAY)
                textSize = 14f
            }
            panel.addView(label)

            val spinner = Spinner(this)
            spinner.adapter = ArrayAdapter(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                cardNames
            )
            handSpinners.add(spinner)
            panel.addView(spinner)
        }

        panel.addView(heading("ENEMY THREAT"))

        threatSpinner = Spinner(this)
        threatSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            threats
        )
        panel.addView(threatSpinner)

        panel.addView(heading("AUTOPLAY"))

        autoSwitch = Switch(this).apply {
            text = "ENABLE AUTOPLAY"
            textSize = 19f
            setTextColor(Color.WHITE)
            setPadding(0, 15, 0, 15)

            setOnCheckedChangeListener { _, enabled ->
                setAutoplay(enabled)
            }
        }
        panel.addView(autoSwitch)

        panel.addView(button("TEST INPUT - CENTER") {
            val w = resources.displayMetrics.widthPixels
            val h = resources.displayMetrics.heightPixels

            val ok = ShizukuInput.swipe(
                w / 2,
                (h * 0.90f).toInt(),
                w / 2,
                (h * 0.60f).toInt(),
                onComplete = { success -> runOnUiThread { status.text = if (success) "TEST INPUT EXECUTED" else "TEST INPUT FAILED" } }
            )

            status.text =
                if (ok) "TEST INPUT RUNNING"
                else "SHIZUKU NOT READY"
        })

        panel.addView(button("PROOF: PLACE SLOT 1") {
            if (!ShizukuInput.isReady()) {
                status.text = "SHIZUKU NOT READY"
            } else {
                val w = resources.displayMetrics.widthPixels
                val h = resources.displayMetrics.heightPixels
                status.text = "TESTING SLOT 1 PLACEMENT"
                val started = ShizukuInput.swipe(
                    (w * 0.325f).toInt(),
                    (h * 0.90f).toInt(),
                    (w * 0.50f).toInt(),
                    (h * 0.68f).toInt(),
                    duration = 180,
                    onComplete = { success ->
                        runOnUiThread {
                            status.text = if (success) "GESTURE EXECUTED - CHECK GAME"
                                          else "GESTURE FAILED"
                        }
                    }
                )
                if (!started) status.text = "GESTURE NOT STARTED"
            }
        })

        panel.addView(button("STOP AUTOPLAY") {
            autoSwitch.isChecked = false
        })

        panel.addView(heading("SCREEN ANALYSIS"))

        panel.addView(button("START SCREEN ANALYSIS") {
            requestCapture()
        })

        panel.addView(button("STOP SCREEN ANALYSIS") {
            val intent = Intent(
                this,
                ScreenCaptureService::class.java
            ).apply {
                action = ScreenCaptureService.ACTION_STOP
            }
            startService(intent)
            status.text = "SCREEN ANALYSIS STOPPED"
        })

        panel.addView(heading("AUTOPLAY STATUS"))

        panel.addView(button("RESET PLAY COOLDOWN") {
            lastPlayed = ""
            lastPlayTime = 0L
            status.text = "COOLDOWN RESET"
        })

        scroll.addView(panel)
        root.addView(scroll)
        setContentView(root)

        refreshShizuku()
    }

    private fun refreshShizuku() {
        status.text = if (ShizukuInput.isReady()) {
            "SHIZUKU: CONNECTED"
        } else {
            "SHIZUKU: NOT READY"
        }
    }

    private fun setAutoplay(enabled: Boolean) {
        if (enabled && !ShizukuInput.isReady()) {
            autoSwitch.isChecked = false
            status.text = "AUTOPLAY BLOCKED: SHIZUKU REQUIRED"
            return
        }
        autoplay = enabled
        handler.removeCallbacks(autoTask)
        val intent = Intent(this, ScreenCaptureService::class.java).apply {
            action = if (enabled) ScreenCaptureService.ACTION_AUTO_START
                     else ScreenCaptureService.ACTION_AUTO_STOP
        }
        startService(intent)
        status.text = if (enabled) "AUTOPLAY ARMED: START SCREEN ANALYSIS"
                      else "AUTOPLAY STOPPED"
    }

    private fun selectedCards(): List<Pair<Int, Card>> {
        val result = mutableListOf<Pair<Int, Card>>()

        handSpinners.forEachIndexed { index, spinner ->
            val name = spinner.selectedItem?.toString() ?: return@forEachIndexed
            val card = when (name) {
                "Hog Rider" -> Card.HOG_RIDER
                "Musketeer" -> Card.MUSKETEER
                "Cannon" -> Card.CANNON
                "Fireball" -> Card.FIREBALL
                "Skeletons" -> Card.SKELETONS
                "Ice Golem" -> Card.ICE_GOLEM
                "Ice Spirit" -> Card.ICE_SPIRIT
                "The Log" -> Card.LOG
                else -> null
            }

            if (card != null) result.add(index to card)
        }

        return result
    }

    private fun autoplayStep() {
        if (!autoplay || !ShizukuInput.isReady()) {
            setAutoplay(false)
            return
        }

        val elixir = elixirBar.progress.toFloat()
        val hand = selectedCards()

        if (hand.isEmpty()) {
            status.text = "AUTOPLAY: SELECT YOUR HAND"
            return
        }

        val threat = when (threatSpinner.selectedItemPosition) {
            1 -> Threat.HIGH_DANGER
            2 -> Threat.TANK
            3 -> Threat.SWARM
            4 -> Threat.AIR
            5 -> Threat.WIN_CONDITION
            else -> Threat.NONE
        }

        val affordable = hand.filter { it.second.cost <= elixir }
        if (affordable.isEmpty()) {
            status.text = "AUTOPLAY: WAITING FOR ELIXIR"
            return
        }

        val chosen = when (threat) {
            Threat.HIGH_DANGER -> choose(
                affordable,
                listOf(
                    Card.CANNON,
                    Card.MUSKETEER,
                    Card.ICE_GOLEM,
                    Card.SKELETONS,
                    Card.LOG
                )
            )

            Threat.TANK -> choose(
                affordable,
                listOf(Card.CANNON, Card.MUSKETEER, Card.ICE_GOLEM)
            )

            Threat.SWARM -> choose(
                affordable,
                listOf(Card.LOG, Card.FIREBALL, Card.SKELETONS)
            )

            Threat.AIR -> choose(
                affordable,
                listOf(Card.MUSKETEER, Card.FIREBALL)
            )

            Threat.WIN_CONDITION -> choose(
                affordable,
                listOf(Card.CANNON, Card.MUSKETEER)
            )

            else -> {
                if (elixir >= 4f && affordable.any { it.second == Card.HOG_RIDER }) {
                    choose(affordable, listOf(Card.HOG_RIDER))
                } else if (elixir >= 8f) {
                    choose(
                        affordable,
                        listOf(
                            Card.ICE_SPIRIT,
                            Card.SKELETONS,
                            Card.ICE_GOLEM
                        )
                    )
                } else null
            }
        }

        if (chosen == null) {
            status.text = "AUTOPLAY: WAITING FOR BETTER MOVE"
            return
        }

        val now = System.currentTimeMillis()

        if (chosen.second.displayName == lastPlayed &&
            now - lastPlayTime < 8000L
        ) return

        val width = resources.displayMetrics.widthPixels
        val height = resources.displayMetrics.heightPixels

        val slotX = (width * (0.325f + chosen.first * 0.17f)).toInt()
        val slotY = (height * 0.90f).toInt()

        val placementX = (width * 0.50f).toInt()
        val placementY = when (chosen.second) {
            Card.HOG_RIDER -> (height * 0.53f).toInt()
            Card.FIREBALL, Card.LOG -> (height * 0.48f).toInt()
            else -> (height * 0.66f).toInt()
        }

        val sent = ShizukuInput.swipe(
            slotX,
            slotY,
            placementX,
            placementY,
            onComplete = { success ->
                runOnUiThread {
                    if (success) {
                        lastPlayed = chosen.second.displayName
                        lastPlayTime = System.currentTimeMillis()
                        status.text = "INPUT EXECUTED: ${chosen.second.displayName}"
                    } else {
                        status.text = "INPUT COMMAND FAILED"
                        autoSwitch.isChecked = false
                    }
                }
            }
        )

        if (sent) {
            lastPlayTime = now
            status.text = "AUTOPLAY: INPUT RUNNING"
        } else {
            status.text = "INPUT FAILED"
            setAutoplay(false)
        }
    }

    private fun choose(
        available: List<Pair<Int, Card>>,
        priority: List<Card>
    ): Pair<Int, Card>? {
        for (card in priority) {
            val found = available.firstOrNull { it.second == card }
            if (found != null) return found
        }
        return null
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

    override fun onDestroy() {
        autoplay = false
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
