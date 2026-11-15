package com.clashai.bot

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.clashai.bot.ai.BattleState
import com.clashai.bot.ai.Card
import com.clashai.bot.ai.Hog26Engine
import com.clashai.bot.ai.Threat
import kotlin.math.min
import kotlin.random.Random

/**
 * Local training sandbox. It deliberately does not pretend to be the Clash Royale
 * game engine: it runs a lightweight deterministic arena model using the same
 * Hog26Engine decision policy as the live pipeline.
 */
class SimulatorActivity : Activity() {
    private lateinit var arena: SimulatorArenaView
    private lateinit var info: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val engine = SandboxBattle()
    private var running = true
    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            engine.update(0.10f)
            arena.invalidate()
            info.text = engine.status()
            handler.postDelayed(this, 100L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(12, 22, 32)
        window.navigationBarColor = Color.rgb(12, 22, 32)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 8, 12, 8)
            setBackgroundColor(Color.rgb(12, 22, 32))
        }
        val title = TextView(this).apply {
            text = "CLASH AI — LIVE MIRROR SANDBOX"
            textSize = 18f
            setTextColor(Color.WHITE)
            setPadding(4, 8, 4, 8)
        }
        info = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.rgb(190, 220, 230))
            setPadding(4, 4, 4, 8)
        }
        arena = SimulatorArenaView(this, engine)
        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val auto = Button(this).apply {
            text = "AI: ON"
            setOnClickListener {
                engine.autoPlay = !engine.autoPlay
                text = if (engine.autoPlay) "AI: ON" else "AI: OFF"
            }
        }
        val reset = Button(this).apply {
            text = "RESET"
            setOnClickListener { engine.reset() }
        }
        val pause = Button(this).apply {
            text = "PAUSE"
            setOnClickListener {
                running = !running
                text = if (running) "PAUSE" else "RESUME"
                if (running) handler.post(tick) else handler.removeCallbacks(tick)
            }
        }
        controls.addView(auto, LinearLayout.LayoutParams(0, -2, 1f))
        controls.addView(reset, LinearLayout.LayoutParams(0, -2, 1f))
        controls.addView(pause, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(title)
        root.addView(info)
        root.addView(arena, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(controls)
        val hint = TextView(this).apply {
            text = "Tap one of the four cards to play manually. AI uses the project's Hog 2.6 decision engine. This is a training model, not a 1:1 game clone."
            textSize = 11f
            setTextColor(Color.LTGRAY)
            setPadding(4, 6, 4, 2)
        }
        root.addView(hint)
        setContentView(root)
        handler.post(tick)
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}

private data class SandboxUnit(
    val card: Card,
    var x: Float,
    var y: Float,
    var hp: Float,
    val friendly: Boolean,
    val speed: Float
)

private class SandboxBattle {
    private val policy = Hog26Engine()
    private val deck = listOf(
        Card.HOG_RIDER, Card.MUSKETEER, Card.CANNON, Card.FIREBALL,
        Card.SKELETONS, Card.ICE_GOLEM, Card.ICE_SPIRIT, Card.LOG
    )
    val hand = deck.take(4).toMutableList()
    private var nextCard = 4
    val units = mutableListOf<SandboxUnit>()
    var elixir = 5f
        private set
    var enemyElixir = 5f
        private set
    var friendlyTowerHp = 100f
        private set
    var enemyTowerHp = 100f
        private set
    var autoPlay = true
    var elapsed = 0f
        private set
    var currentThreat = Threat.NONE
        private set
    private var threatTime = 0f
    private var nextEnemySpawn = 8f
    private var decisionCooldown = 0f
    var lastAction = "Waiting for first decision"
        private set

    fun reset() {
        policy.reset()
        hand.clear()
        hand.addAll(deck.take(4))
        nextCard = 4
        units.clear()
        elixir = 5f
        enemyElixir = 5f
        friendlyTowerHp = 100f
        enemyTowerHp = 100f
        elapsed = 0f
        currentThreat = Threat.NONE
        threatTime = 0f
        nextEnemySpawn = 8f
        decisionCooldown = 0f
        lastAction = "Sandbox reset"
    }

    fun update(dt: Float) {
        elapsed += dt
        val multiplier = if (elapsed >= 120f) 2f else 1f
        elixir = min(10f, elixir + dt * multiplier / 2.8f)
        enemyElixir = min(10f, enemyElixir + dt * multiplier / 2.8f)
        threatTime = (threatTime - dt).coerceAtLeast(0f)
        if (threatTime == 0f) currentThreat = Threat.NONE

        if (elapsed >= nextEnemySpawn && enemyElixir >= 3f) {
            val threat = listOf(Threat.TANK, Threat.SWARM, Threat.AIR, Threat.WIN_CONDITION)[Random.nextInt(4)]
            currentThreat = threat
            threatTime = 4.5f
            enemyElixir -= 3f
            val enemyCard = when (threat) {
                Threat.TANK -> Card.ICE_GOLEM
                Threat.SWARM -> Card.SKELETONS
                Threat.AIR -> Card.MUSKETEER
                else -> Card.HOG_RIDER
            }
            units.add(SandboxUnit(enemyCard, 0.5f, 0.24f, 22f, false, 0.035f))
            nextEnemySpawn += Random.nextInt(10, 16)
        }

        decisionCooldown = (decisionCooldown - dt).coerceAtLeast(0f)
        if (autoPlay && decisionCooldown <= 0f) {
            val state = BattleState(
                elixir = elixir,
                hand = hand.toList(),
                enemyThreat = currentThreat,
                doubleElixir = elapsed >= 120f,
                timeRemaining = (180f - elapsed).toInt().coerceAtLeast(0),
                handConfidence = 1f,
                observationAgeMs = 0L
            )
            val action = policy.chooseAction(state)
            if (action.card != null && action.card in hand && action.card.cost <= elixir) {
                play(action.card, action.recommendedPlacement, "AI")
                decisionCooldown = 1.3f
            } else {
                lastAction = "AI: " + action.reason
                decisionCooldown = 0.45f
            }
        }
        val iterator = units.iterator()
        while (iterator.hasNext()) {
            val unit = iterator.next()
            unit.y += if (unit.friendly) -unit.speed * dt else unit.speed * dt
            if (unit.y <= 0.12f && unit.friendly) {
                enemyTowerHp = (enemyTowerHp - if (unit.card == Card.HOG_RIDER) 4.5f else 1.2f).coerceAtLeast(0f)
                iterator.remove()
            } else if (unit.y >= 0.88f && !unit.friendly) {
                friendlyTowerHp = (friendlyTowerHp - 3.5f).coerceAtLeast(0f)
                iterator.remove()
            } else if (unit.y < 0.05f || unit.y > 0.95f) iterator.remove()
        }
    }

    fun playManual(slot: Int) {
        if (slot !in hand.indices) return
        val card = hand[slot]
        if (card.cost > elixir) {
            lastAction = "Not enough elixir for ${card.displayName}"
            return
        }
        play(card, "MANUAL", "MANUAL")
    }

    private fun play(card: Card, placement: String, source: String) {
        if (card !in hand || card.cost > elixir) return
        elixir -= card.cost
        val index = hand.indexOf(card)
        hand.removeAt(index)
        hand.add(deck[nextCard % deck.size])
        nextCard++
        policy.recordPlay(card)
        when (card) {
            Card.FIREBALL, Card.LOG -> {
                if (currentThreat != Threat.NONE) {
                    currentThreat = Threat.NONE
                    threatTime = 0f
                }
                if (placement == "ENEMY_GROUP") enemyTowerHp = (enemyTowerHp - 2f).coerceAtLeast(0f)
            }
            Card.CANNON -> units.add(SandboxUnit(card, 0.5f, 0.66f, 38f, true, 0.006f))
            Card.MUSKETEER -> units.add(SandboxUnit(card, 0.5f, 0.76f, 32f, true, 0.012f))
            Card.HOG_RIDER -> units.add(SandboxUnit(card, 0.5f, 0.82f, 36f, true, 0.045f))
            else -> units.add(SandboxUnit(card, 0.5f, 0.78f, 18f, true, 0.022f))
        }
        lastAction = "$source played ${card.displayName} (${card.cost}) — $placement"
    }

    fun status(): String =
        "ELIXIR ${"%.1f".format(elixir)}/10  |  ENEMY TOWER ${enemyTowerHp.toInt()}%  |  YOUR TOWER ${friendlyTowerHp.toInt()}%\n" +
        "THREAT: $currentThreat  |  TIME: ${(180f - elapsed).toInt().coerceAtLeast(0)}s\n" +
        "HAND: ${hand.joinToString("  •  ") { it.displayName }}\n$lastAction"
}

private class SimulatorArenaView(
    context: android.content.Context,
    private val battle: SandboxBattle
) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cards = Card.values()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val sx = width / 1000f
        val sy = height / 1200f
        canvas.save()
        canvas.scale(sx, sy)
        p.style = Paint.Style.FILL
        p.color = Color.rgb(44, 112, 78)
        canvas.drawRect(0f, 0f, 1000f, 1200f, p)
        p.color = Color.rgb(48, 91, 115)
        canvas.drawRect(0f, 555f, 1000f, 645f, p)
        p.color = Color.rgb(174, 145, 91)
        canvas.drawRect(0f, 570f, 1000f, 630f, p)
        p.color = Color.rgb(220, 205, 160)
        canvas.drawRect(245f, 535f, 335f, 665f, p)
        canvas.drawRect(665f, 535f, 755f, 665f, p)
        p.color = Color.WHITE
        p.textSize = 30f
        p.textAlign = Paint.Align.CENTER
        canvas.drawText("OPPONENT", 500f, 38f, p)
        canvas.drawText("YOU", 500f, 1170f, p)
        drawTower(canvas, 350f, 100f, battle.enemyTowerHp, false)
        drawTower(canvas, 650f, 100f, battle.enemyTowerHp, false)
        drawTower(canvas, 350f, 1080f, battle.friendlyTowerHp, true)
        drawTower(canvas, 650f, 1080f, battle.friendlyTowerHp, true)
        for (unit in battle.units) {
            p.color = if (unit.friendly) Color.rgb(80, 190, 255) else Color.rgb(245, 95, 85)
            canvas.drawCircle(unit.x * 1000f, unit.y * 1200f, 25f, p)
            p.color = Color.WHITE
            p.textSize = 17f
            p.textAlign = Paint.Align.CENTER
            canvas.drawText(unit.card.displayName.take(8), unit.x * 1000f, unit.y * 1200f + 6f, p)
        }
        p.color = Color.rgb(16, 23, 34)
        canvas.drawRect(0f, 1000f, 1000f, 1200f, p)
        val hand = battle.hand
        for (i in hand.indices) {
            val left = 22f + i * 245f
            p.color = Color.rgb(58, 74, 103)
            canvas.drawRoundRect(RectF(left, 1020f, left + 220f, 1175f), 18f, 18f, p)
            p.color = Color.WHITE
            p.textSize = 23f
            p.textAlign = Paint.Align.CENTER
            canvas.drawText(hand[i].displayName, left + 110f, 1075f, p)
            p.textSize = 20f
            canvas.drawText("COST ${hand[i].cost}", left + 110f, 1125f, p)
        }
        canvas.restore()
    }

    private fun drawTower(canvas: Canvas, x: Float, y: Float, hp: Float, friendly: Boolean) {
        p.color = if (friendly) Color.rgb(65, 155, 225) else Color.rgb(220, 80, 75)
        canvas.drawRoundRect(RectF(x - 55f, y - 48f, x + 55f, y + 48f), 12f, 12f, p)
        p.color = Color.WHITE
        p.textAlign = Paint.Align.CENTER
        p.textSize = 20f
        canvas.drawText("${hp.toInt()}%", x, y + 7f, p)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val logicalX = event.x / width * 1000f
            val logicalY = event.y / height * 1200f
            if (logicalY >= 1000f) {
                val slot = ((logicalX - 22f) / 245f).toInt()
                battle.playManual(slot)
                invalidate()
                return true
            }
        }
        return true
    }
}
