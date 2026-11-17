package com.clashai.bot.input

import rikka.shizuku.Shizuku
import java.util.concurrent.TimeUnit

object ShizukuInput {

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

    /**
     * Sends one swipe and reports success only after the shell process exits
     * successfully. A started background thread is not proof that Android
     * accepted the input command.
     */
    @Synchronized
    fun placeCard(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        duration: Int = 220
    ): Boolean {
        if (!isReady()) return false
        if (startX < 0 || startY < 0 || endX < 0 || endY < 0) return false
        if (duration !in 1..5000) return false

        var process: Process? = null
        return try {
            val command = "input tap $startX $startY && input tap $endX $endY"
            process = Shizuku.newProcess(
                arrayOf("sh", "-c", command),
                null,
                null
            )

            val finished = process.waitFor(1500, TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                false
            } else {
                process.exitValue() == 0
            }
        } catch (_: Exception) {
            try {
                process?.destroyForcibly()
            } catch (_: Exception) {
            }
            false
        }
    }
}
