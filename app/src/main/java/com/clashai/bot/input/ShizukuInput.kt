package com.clashai.bot.input

import rikka.shizuku.Shizuku
import java.util.concurrent.atomic.AtomicBoolean

object ShizukuInput {

    private val inputBusy = AtomicBoolean(false)

    fun isReady(): Boolean {
        return try {
            Shizuku.pingBinder() &&
                Shizuku.checkSelfPermission() ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) {
            false
        }
    }

    fun requestPermission() {
        try {
            if (Shizuku.pingBinder() &&
                Shizuku.checkSelfPermission() !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                Shizuku.requestPermission(1001)
            }
        } catch (_: Exception) {
        }
    }

    fun swipe(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        duration: Int = 220,
        onComplete: ((Boolean) -> Unit)? = null
    ): Boolean {
        if (!isReady() || !inputBusy.compareAndSet(false, true)) return false

        return try {
            val command =
                "input swipe $startX $startY $endX $endY $duration"

            Thread {
                try {
                    val process = Shizuku.newProcess(
                        arrayOf("sh", "-c", command),
                        null,
                        null
                    )
                    val exitCode = process.waitFor()
                    onComplete?.invoke(exitCode == 0)
                } catch (_: Exception) {
                    onComplete?.invoke(false)
                } finally {
                    inputBusy.set(false)
                }
            }.start()

            true
        } catch (_: Exception) {
            inputBusy.set(false)
            false
        }
    }
}
