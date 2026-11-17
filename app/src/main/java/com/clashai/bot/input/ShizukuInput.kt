package com.clashai.bot.input

import rikka.shizuku.Shizuku

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

    fun swipe(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        duration: Int = 220,
        onComplete: ((Boolean) -> Unit)? = null
    ): Boolean {
        if (!isReady()) return false

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
                }
            }.start()

            true
        } catch (_: Exception) {
            false
        }
    }
}
