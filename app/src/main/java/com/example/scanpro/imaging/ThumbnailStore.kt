package com.example.scanpro.imaging

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Small page previews kept on disk, so the library and document screens don't decode every full-size
 * page JPEG again after each process start. Each page gets its own folder (a hash of the page's path),
 * holding one file per decode size, named after the page file's size and timestamp; a page that is
 * re-rendered in place therefore misses the cache once and replaces its old preview.
 *
 * Only pages in the app's permanent storage are cached. Files in the cache folder (camera sessions,
 * imports in progress) are short-lived, so a copy of their preview would only be wasted work.
 */
object ThumbnailStore {
    private const val DIR_NAME = "thumbnails"
    private const val JPEG_QUALITY = 88
    private const val MAX_BYTES = 64L * 1024 * 1024
    private const val TRIM_TO_BYTES = 48L * 1024 * 1024

    /**
     * The preview of [path] at [maxDimension]: from disk when a copy matches the file's current version,
     * otherwise decoded from the page and written for next time. Null when the page can't be decoded.
     */
    fun load(context: Context, path: String, maxDimension: Int): Bitmap? {
        val source = File(path)
        val length = source.length()
        if (length <= 0L) return null
        if (!isPermanent(context, source)) return ImageProcessor.decodeScaled(path, maxDimension)

        val folder = folderFor(context, path)
        val target = File(folder, "$maxDimension-${source.lastModified()}-$length.jpg")
        if (target.isFile) {
            BitmapFactory.decodeFile(target.path)?.let { return it }
            target.delete() // A half-written or corrupt preview: make it again below.
        }
        val bitmap = ImageProcessor.decodeScaled(path, maxDimension) ?: return null
        runCatching { write(folder, target, bitmap, maxDimension) }
        return bitmap
    }

    /** Drops every cached preview of [path], for when the page's own files are deleted. */
    fun forget(context: Context, path: String) {
        folderFor(context, path).deleteRecursively()
    }

    /** Keeps the cache under its size budget by dropping the previews written longest ago. */
    fun trim(context: Context) {
        val files = File(context.cacheDir, DIR_NAME).walkBottomUp().filter { it.isFile }.toList()
        var total = files.sumOf { it.length() }
        if (total <= MAX_BYTES) return
        for (file in files.sortedBy { it.lastModified() }) {
            if (total <= TRIM_TO_BYTES) break
            val size = file.length()
            if (file.delete()) {
                total -= size
                file.parentFile?.delete() // Only succeeds once the page's folder is empty.
            }
        }
    }

    private fun write(folder: File, target: File, bitmap: Bitmap, maxDimension: Int) {
        folder.mkdirs()
        // Earlier versions of this page at the same size are stale now.
        folder.listFiles { file -> file.name.startsWith("$maxDimension-") && file != target }?.forEach { it.delete() }
        val temp = File(folder, "${target.name}.tmp")
        FileOutputStream(temp).use { out -> check(bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) }
        if (!temp.renameTo(target)) temp.delete()
    }

    private fun isPermanent(context: Context, file: File): Boolean =
        !file.absolutePath.startsWith(context.cacheDir.absolutePath + File.separator)

    private fun folderFor(context: Context, path: String) = File(File(context.cacheDir, DIR_NAME), hash(path))

    private fun hash(path: String): String =
        MessageDigest.getInstance("SHA-1").digest(path.toByteArray()).joinToString("") { "%02x".format(it) }
}
