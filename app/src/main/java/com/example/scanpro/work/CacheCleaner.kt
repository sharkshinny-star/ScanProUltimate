package com.example.scanpro.work

import android.content.Context
import java.io.File

/**
 * Removes leftovers in the cache folder. Several features write decrypted or imported copies of the
 * user's PDFs there while they work; they delete them when done, but a crash or a killed process can
 * leave them behind, and they should not sit on the device.
 */
object CacheCleaner {
    private val TEMP_PREFIXES = listOf(
        "view_", "search_temp_", "pdf_import_", "pdf_unlocked_", "import_", "restore_", "merge_in_",
        "split_in", "extract_in", "rotate_in", "organize_in", "protect_in", "unlock_in", "count_",
        "thumb_", "inspect_", "pdf2img_in", "img2pdf_", "raster_", "temp_in_"
    )
    private const val TEMP_MAX_AGE_MS = 60L * 60 * 1000
    private const val EXPORT_MAX_AGE_MS = 24L * 60 * 60 * 1000

    fun purge(context: Context, now: Long = System.currentTimeMillis()) {
        val cache = context.cacheDir
        cache.listFiles()?.forEach { file ->
            val isTemp = TEMP_PREFIXES.any { file.name.startsWith(it) }
            if (isTemp && now - file.lastModified() > TEMP_MAX_AGE_MS) file.deleteRecursively()
        }
        // Results of the PDF tools (including unlocked copies) are shareable for a day, then go.
        File(cache, "exports").listFiles()?.forEach { file ->
            val isTemp = file.name.startsWith("temp_in_")
            if (now - file.lastModified() > (if (isTemp) TEMP_MAX_AGE_MS else EXPORT_MAX_AGE_MS)) file.deleteRecursively()
        }
    }
}
