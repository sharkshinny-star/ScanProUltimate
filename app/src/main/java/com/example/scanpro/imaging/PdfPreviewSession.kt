package com.example.scanpro.imaging

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import com.example.scanpro.R
import com.example.scanpro.core.text.userError

/**
 * Page previews for one PDF: the file is copied once and opened once, pages are rendered only when asked for,
 * and finished thumbnails are kept in a cache bounded by memory rather than by page count. Rendering is
 * serialised because PdfRenderer allows only one open page at a time.
 */
class PdfPreviewSession private constructor(
    private val file: File,
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer
) : Closeable {
    val pageCount: Int = renderer.pageCount
    private var closed = false

    // Two caches: a full-size page (about 11 MB at 1400 px) used to push out about thirty thumbnails, so closing the
    // preview meant rendering the whole grid again.
    private val thumbnails = object : LruCache<Int, Bitmap>(THUMBNAIL_CACHE_BYTES) {
        override fun sizeOf(key: Int, value: Bitmap) = value.byteCount
    }
    private val large = object : LruCache<Int, Bitmap>(LARGE_CACHE_BYTES) {
        override fun sizeOf(key: Int, value: Bitmap) = value.byteCount
    }

    /** The page at [width] pixels wide, from the cache when possible; null if it cannot be rendered. */
    @Synchronized
    fun render(index: Int, width: Int): Bitmap? {
        val cache = if (width > LARGE_FROM_WIDTH) large else thumbnails
        cache.get(index)?.let { if (it.width == width) return it }
        if (closed || index !in 0 until pageCount) return null
        return runCatching {
            renderer.openPage(index).use { page ->
                val height = (width * page.height.toFloat() / page.width.toFloat()).toInt().coerceAtLeast(1)
                createBitmap(width, height).also { bitmap ->
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    cache.put(index, bitmap)
                }
            }
        }.getOrNull()
    }

    /** A page's size in points (1/72 inch) as it is shown, or null if it cannot be read. */
    @Synchronized
    fun pageSizePoints(index: Int): Pair<Int, Int>? = runCatching {
        check(!closed)
        renderer.openPage(index).use { it.width to it.height }
    }.getOrNull()

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        // Evicted bitmaps are left to the garbage collector: one may still be on screen for a frame.
        thumbnails.evictAll()
        large.evictAll()
        runCatching { renderer.close() }
        runCatching { descriptor.close() }
        file.delete()
    }

    companion object {
        private const val THUMBNAIL_CACHE_BYTES = 16 * 1024 * 1024
        private const val LARGE_CACHE_BYTES = 14 * 1024 * 1024

        /** Renders wider than this are full-page previews, not grid thumbnails. */
        private const val LARGE_FROM_WIDTH = 600

        suspend fun open(context: Context, uri: Uri): PdfPreviewSession = withContext(Dispatchers.IO) {
            val copy = File(context.cacheDir, "thumb_${UUID.randomUUID()}.pdf")
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(copy).buffered(64 * 1024).use { input.copyTo(it, 64 * 1024) }
                } ?: userError(R.string.err_could_not_read_pdf_location)
                val descriptor = ParcelFileDescriptor.open(copy, ParcelFileDescriptor.MODE_READ_ONLY)
                try {
                    PdfPreviewSession(copy, descriptor, PdfRenderer(descriptor))
                } catch (error: Throwable) {
                    descriptor.close()
                    throw error
                }
            } catch (error: Throwable) {
                copy.delete()
                throw error
            }
        }
    }
}
