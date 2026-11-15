package com.clashai.bot.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.Closeable

/**
 * Loads the bundled card-art reference set.
 *
 * The build workflow packages reference images from the upstream repository and
 * includes its source notice and README. This project is not affiliated with
 * Supercell. Missing/corrupt files are skipped; callers must treat an empty or
 * incomplete catalog as "unknown", never as a card match.
 */
class CardTemplateCatalog(context: Context) : Closeable {
    data class Template(val id: String, val bitmap: Bitmap)

    private val templates: List<Template> = context.assets
        .list("card_templates")
        .orEmpty()
        .asSequence()
        .filter { it.endsWith(".png", ignoreCase = true) }
        .mapNotNull { filename ->
            val id = filename.substringBeforeLast('.')
            runCatching {
                context.assets.open("card_templates/$filename").use { input ->
                    BitmapFactory.decodeStream(input)?.let { Template(id, it) }
                }
            }.getOrNull()
        }
        .toList()

    fun all(): List<Template> = templates
    fun size(): Int = templates.size
    fun contains(id: String): Boolean = templates.any { it.id == id }

    override fun close() {
        templates.forEach { template ->
            if (!template.bitmap.isRecycled) template.bitmap.recycle()
        }
    }
}
