package com.example.scanpro.work

import android.content.Context
import com.example.scanpro.imaging.ThumbnailStore
import com.example.scanpro.scanner.SESSION_PENDING_IMPORT
import com.example.scanpro.scanner.SESSION_ROOT
import com.example.scanpro.scanner.scannerSessionRoot
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
        "thumb_", "markup_", "inspect_", "pdf2img_in", "img2pdf_", "raster_", "temp_in_",
        "ocr_in_", "ocr_page_", "form_read_", "form_fill_", "view_"
    )
    private const val SESSION_MAX_AGE_MS = 24L * 60 * 60 * 1000
    // A scan whose import never finished is kept this long, so a later retry can still save it.
    private const val PENDING_SESSION_MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000
    private const val TEMP_MAX_AGE_MS = 60L * 60 * 1000
    private const val EXPORT_MAX_AGE_MS = 24L * 60 * 60 * 1000

    fun purge(context: Context, now: Long = System.currentTimeMillis()) {
        val cache = context.cacheDir
        cache.listFiles()?.forEach { file ->
            val isTemp = TEMP_PREFIXES.any { file.name.startsWith(it) }
            if (isTemp && now - file.lastModified() > TEMP_MAX_AGE_MS) file.deleteRecursively()
        }
        // Scanner sessions nobody came back to (the scanner itself cleans them only when it is opened again).
        // Sessions moved from the cache to the app's files; the cache folder only holds ones from older versions.
        listOf(File(cache, SESSION_ROOT), scannerSessionRoot(context)).forEach { root ->
            root.listFiles()?.forEach { session ->
                val maxAge = if (File(session, SESSION_PENDING_IMPORT).exists()) PENDING_SESSION_MAX_AGE_MS else SESSION_MAX_AGE_MS
                if (now - session.lastModified() > maxAge) session.deleteRecursively()
            }
        }
        // Results of the PDF tools (including unlocked copies) are shareable for a day, then go.
        File(cache, "exports").listFiles()?.forEach { file ->
            val isTemp = file.name.startsWith("temp_in_")
            if (now - file.lastModified() > (if (isTemp) TEMP_MAX_AGE_MS else EXPORT_MAX_AGE_MS)) file.deleteRecursively()
        }
        ThumbnailStore.trim(context)
    }
}
