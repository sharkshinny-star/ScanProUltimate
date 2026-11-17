package com.example.scanpro.feature.phonepdfs

import com.example.scanpro.core.concurrent.BackgroundWork
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Small thumbnails for "Files on this phone": the picture itself for images, the first page for PDFs. Made off the main
 * thread, a few at a time, only for rows on screen (the list is lazy), and kept in a small memory cache.
 */
object PhoneThumbnails {
    /** About 8 MB of thumbnails; a 96 px square is about 36 KB. */
    private val cache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val failed = HashSet<String>()
    private val permits = Semaphore(3)

    fun cached(file: PhoneFile, px: Int): Bitmap? = cache.get(key(file, px))

    private fun key(file: PhoneFile, px: Int) = "${file.uri}|${file.modified}|$px"

    suspend fun load(context: Context, file: PhoneFile, px: Int): Bitmap? {
        val key = key(file, px)
        cache.get(key)?.let { return it }
        if (synchronized(failed) { key in failed }) return null
        return permits.withPermit {
            withContext(BackgroundWork.thumbnails) {
                cache.get(key) ?: runCatching {
                    when (file.kind) {
                        PhoneFileKind.IMAGE -> image(context, file, px)
                        PhoneFileKind.PDF -> pdfFirstPage(context, file, px)
                        else -> null
                    }
                }.getOrNull().also { bitmap ->
                    if (bitmap != null) cache.put(key, bitmap) else synchronized(failed) { failed += key }
                }
            }
        }
    }

    private fun image(context: Context, file: PhoneFile, px: Int): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && file.path == null) {
            return context.contentResolver.loadThumbnail(file.uri, Size(px, px), null)
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(file.uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= px) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(file.uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    /** The first page, [px] wide, on white. Password-protected or broken PDFs give null (the row shows its icon). */
    private fun pdfFirstPage(context: Context, file: PhoneFile, px: Int): Bitmap? {
        val descriptor = context.contentResolver.openFileDescriptor(file.uri, "r") ?: return null
        return descriptor.use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                if (renderer.pageCount == 0) return null
                renderer.openPage(0).use { page ->
                    val height = (px * page.height.toFloat() / page.width.coerceAtLeast(1)).toInt().coerceIn(1, px * 2)
                    Bitmap.createBitmap(px, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                }
            }
        }
    }
}

/** The file's thumbnail, or [fallback] while it loads and for kinds without one. */
@Composable
fun PhoneFileThumbnail(file: PhoneFile, size: Dp, modifier: Modifier = Modifier, fallback: @Composable () -> Unit) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    val hasThumbnail = file.kind == PhoneFileKind.IMAGE || file.kind == PhoneFileKind.PDF
    val bitmap by produceState(PhoneThumbnails.cached(file, px), file.uri, file.modified, px) {
        if (hasThumbnail && value == null) value = PhoneThumbnails.load(context, file, px)
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        val current = bitmap
        if (current == null) fallback()
        else Image(current.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
    }
}
