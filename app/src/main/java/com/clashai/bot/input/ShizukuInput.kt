package com.clashai.bot.input

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.util.concurrent.atomic.AtomicLong

/**
 * Serializes touch commands and allows STOP to destroy an in-flight shell process.
 * A successful return means the command was accepted; completion is reported separately.
 */
object ShizukuInput {
    private val generation = AtomicLong(0)
    @Volatile private var activeProcess: android.os.Process? = null
    @Volatile private var running = false

    fun isReady(): Boolean = try {
        Shizuku.pingBinder() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Exception) { false }

    fun requestPermission() {
        try {
            if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(1001)
            }
        } catch (_: Exception) { }
    }

    @Synchronized
    fun swipe(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        duration: Int = 220,
        onComplete: (Boolean) -> Unit
    ): Boolean {
        if (!isReady() || running) return false
        val token = generation.get()
        running = true
        Thread {
            var success = false
            try {
                if (generation.get() != token || !isReady()) return@Thread
                val process = Shizuku.newProcess(
                    arrayOf("sh", "-c", "input swipe $startX $startY $endX $endY $duration"),
                    null,
                    null
                )
                activeProcess = process
                if (generation.get() == token) {
                    success = process.waitFor() == 0
                } else {
                    process.destroy()
                }
            } catch (_: Exception) {
                success = false
            } finally {
                activeProcess = null
                running = false
                if (generation.get() == token) onComplete(success)
            }
        }.start()
        return true
    }

    @Synchronized
    fun cancelAll() {
        generation.incrementAndGet()
        try { activeProcess?.destroy() } catch (_: Exception) { }
        activeProcess = null
        running = false
    }
}