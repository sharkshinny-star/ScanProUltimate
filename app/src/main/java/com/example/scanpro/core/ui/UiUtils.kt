package com.example.scanpro.core.ui

import android.app.Activity
import com.example.scanpro.core.text.AppStrings
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.example.scanpro.R
import com.example.scanpro.data.DEFAULT_TRASH_RETENTION_DAYS
import com.example.scanpro.data.ScanDocument
import java.io.File
import kotlinx.coroutines.launch

/**
 * Every document's PDF is stored as "document.pdf", and the receiving app names the attachment after the
 * file. So a copy named after the title is made in the shareable export cache first, off the main thread.
 */
private fun namedPdfCopies(context: Context, documents: List<ScanDocument>): List<File> {
    val exports = File(context.cacheDir, "exports").apply { mkdirs() }
    val used = HashSet<String>()
    return documents.mapNotNull { document ->
        val source = File(document.pdfPath)
        if (!source.isFile) return@mapNotNull null
        var name = "${com.example.scanpro.data.fileSafeName(document.title)}.pdf"
        var n = 2
        while (!used.add(name.lowercase())) { name = "${com.example.scanpro.data.fileSafeName(document.title)} ($n).pdf"; n++ }
        val copy = File(exports, name)
        if (!copy.isFile || copy.length() != source.length() || copy.lastModified() < source.lastModified()) {
            source.copyTo(copy, overwrite = true)
        }
        copy
    }
}

fun sharePdf(context: Context, document: ScanDocument) = sharePdfs(context, listOf(document))

fun sharePdfs(context: Context, documents: List<ScanDocument>) {
    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main.immediate).launch {
        val files = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { namedPdfCopies(context, documents) }.getOrDefault(emptyList()) }
        if (files.isEmpty()) {
            Toast.makeText(context, context.getString(if (documents.size == 1) R.string.core_pdf_not_available_yet else R.string.core_no_pdfs_to_share), Toast.LENGTH_SHORT).show()
            return@launch
        }
        val uris = files.map { FileProvider.getUriForFile(context, "${context.packageName}.files", it) }
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uris.single())
                putExtra(Intent.EXTRA_TITLE, files.single().name)
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "application/pdf"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            }
        }
        intent.grantReadTo(uris)
        context.startActivity(Intent.createChooser(intent, context.getString(if (uris.size == 1) R.string.core_share_pdf else R.string.core_share_pdfs)))
    }
}

fun shareImages(context: Context, document: ScanDocument) {
    val uris = ArrayList<Uri>()
    document.pages.sortedBy { it.position }.forEach { page ->
        val file = File(page.imagePath)
        if (file.isFile) {
            uris += FileProvider.getUriForFile(
                context,
                "${context.packageName}.files",
                file
            )
        }
    }
    if (uris.isEmpty()) {
        Toast.makeText(context, context.getString(R.string.core_jpg_not_available), Toast.LENGTH_SHORT).show()
        return
    }
    val single = uris.size == 1
    val intent = Intent(if (single) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
        type = "image/jpeg"
        if (single) putExtra(Intent.EXTRA_STREAM, uris.first())
        else putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
    }
    intent.grantReadTo(uris)
    context.startActivity(Intent.createChooser(intent, context.getString(if (single) R.string.core_share_jpg else R.string.core_share_jpg_pages)))
}

fun shareOcrText(context: Context, document: ScanDocument) {
    val text = document.ocrText
    if (text.isBlank()) {
        Toast.makeText(context, context.getString(R.string.core_no_text_to_share), Toast.LENGTH_SHORT).show()
        return
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, document.title)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.core_share_recognized_text)))
}

fun openPdf(context: Context, document: ScanDocument) {
    val file = File(document.pdfPath)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/pdf")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, context.getString(R.string.core_install_pdf_viewer), Toast.LENGTH_LONG).show()
    }
}

fun copyText(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(context.getString(R.string.core_clip_recognized_text), text).apply {
        // Scans often hold IDs and account numbers; keep them out of the clipboard preview on Android 13+.
        description.extras = PersistableBundle().apply {
            putBoolean(if (Build.VERSION.SDK_INT >= 33) ClipDescription.EXTRA_IS_SENSITIVE else "android.content.extra.IS_SENSITIVE", true)
        }
    }
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, context.getString(R.string.core_text_copied), Toast.LENGTH_SHORT).show()
}

fun pagesLabel(context: Context, count: Int): String = context.resources.getQuantityString(R.plurals.core_pages, count, count)

/**
 * A file size in the phone's own units and language, from the system formatter. The short form ("96 kB",
 * "1.9 MB"): the long one prints two decimals below 100 ("96.00 kB"), which is noise on a card.
 */
fun formatBytes(context: Context, bytes: Long): String =
    android.text.format.Formatter.formatShortFileSize(context, bytes.coerceAtLeast(0L))

/**
 * Bytes in the "kB" that [formatBytes] shows: the system formatter is decimal from Android 8 (API 26) and binary
 * before. A typed target must use the same unit, or "170 KB" is rejected for a file the dialog calls 171 kB.
 */
fun displayedKiloByte(): Double = if (android.os.Build.VERSION.SDK_INT >= 26) 1000.0 else 1024.0

/** How long a trashed document has left before it is deleted for good, in the largest whole unit. */
sealed class TrashCountdown(val isUrgent: Boolean) {
    object Soon : TrashCountdown(isUrgent = true)
    data class Days(val days: Int) : TrashCountdown(isUrgent = false)
    data class Hours(val hours: Int) : TrashCountdown(isUrgent = true)
    data class Minutes(val minutes: Int) : TrashCountdown(isUrgent = true)
}

/** Pure part of [formatTrashCountdown], without resources, so it stays testable in a plain JUnit test. */
fun trashCountdown(
    trashedAt: Long,
    nowMs: Long = System.currentTimeMillis(),
    retentionDays: Int = DEFAULT_TRASH_RETENTION_DAYS
): TrashCountdown {
    val retentionMs = retentionDays * 24L * 60L * 60L * 1000L
    val expirationMs = trashedAt + retentionMs
    val remainingMs = expirationMs - nowMs

    if (remainingMs <= 0) return TrashCountdown.Soon

    val hourMs = 60L * 60L * 1000L
    val dayMs = 24L * hourMs
    val minMs = 60L * 1000L

    return when {
        remainingMs >= dayMs -> TrashCountdown.Days(kotlin.math.ceil(remainingMs.toDouble() / dayMs.toDouble()).toInt().coerceAtLeast(1))
        remainingMs >= hourMs -> TrashCountdown.Hours(kotlin.math.ceil(remainingMs.toDouble() / hourMs.toDouble()).toInt().coerceAtLeast(1))
        remainingMs >= minMs -> TrashCountdown.Minutes(kotlin.math.ceil(remainingMs.toDouble() / minMs.toDouble()).toInt().coerceAtLeast(1))
        else -> TrashCountdown.Soon
    }
}

fun formatTrashCountdown(
    context: Context,
    trashedAt: Long,
    nowMs: Long = System.currentTimeMillis(),
    retentionDays: Int = DEFAULT_TRASH_RETENTION_DAYS
): Pair<String, Boolean> {
    val countdown = trashCountdown(trashedAt, nowMs, retentionDays)
    val resources = context.resources
    val text = when (countdown) {
        TrashCountdown.Soon -> context.getString(R.string.core_trash_auto_deletes_soon)
        is TrashCountdown.Days -> resources.getQuantityString(R.plurals.core_trash_auto_deletes_days, countdown.days, countdown.days)
        is TrashCountdown.Hours -> resources.getQuantityString(R.plurals.core_trash_auto_deletes_hours, countdown.hours, countdown.hours)
        is TrashCountdown.Minutes -> resources.getQuantityString(R.plurals.core_trash_auto_deletes_minutes, countdown.minutes, countdown.minutes)
    }
    return Pair(text, countdown.isUrgent)
}

fun shareFile(context: Context, path: String, mimeType: String, chooserTitle: String, extraPaths: List<String> = emptyList()) {
    val files = (listOf(path) + extraPaths).map(::File).filter { it.isFile }
    if (files.isEmpty()) {
        Toast.makeText(context, context.getString(R.string.core_export_file_missing), Toast.LENGTH_SHORT).show()
        return
    }
    val uris = files.map { FileProvider.getUriForFile(context, "${context.packageName}.files", it) }
    val intent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uris.single())
            putExtra(Intent.EXTRA_TITLE, files.single().name)
        }
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = mimeType
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
    }
    intent.grantReadTo(uris)
    context.startActivity(Intent.createChooser(intent, chooserTitle))
}

/**
 * Lets the share sheet itself, and the app picked in it, read [uris]. The links also go in the clip data: some
 * system share sheets read the files for their previews from there and are refused ("requires the provider be
 * exported, or grantUriPermission()") when only the extras carry them.
 */
private fun Intent.grantReadTo(uris: List<Uri>) {
    if (uris.isNotEmpty()) {
        clipData = ClipData.newRawUri(null, uris.first()).also { clip -> uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) } }
    }
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}

/** Shares a content link ScanPro can read, such as a PDF picked from another app, passing on read access. */
fun shareUri(context: Context, uri: Uri, mimeType: String, chooserTitle: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri(chooserTitle, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, context.getString(R.string.core_no_app_can_share), Toast.LENGTH_SHORT).show()
    }
}

/**
 * Hands the file straight to the Google Drive app's "Save to Drive" screen. Returns false when Drive isn't
 * installed, so the caller can fall back to the system file picker (which also lists Drive, Dropbox, OneDrive…).
 * Nothing leaves the device unless the user confirms the upload in Drive.
 */
fun saveToGoogleDrive(context: Context, path: String, mimeType: String, title: String = File(path).name): Boolean {
    val file = File(path)
    if (!file.isFile) {
        Toast.makeText(context, context.getString(R.string.core_export_file_missing), Toast.LENGTH_SHORT).show()
        return true
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        setPackage(GOOGLE_DRIVE_PACKAGE)
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TITLE, title)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

private const val GOOGLE_DRIVE_PACKAGE = "com.google.android.apps.docs"

fun printPdf(context: Context, document: ScanDocument) = printPdfFile(context, File(document.pdfPath), document.title)

/** Hands a PDF on disk to the system print dialog, which also offers "Save as PDF". */
fun printPdfFile(context: Context, file: File, title: String) {
    if (!file.exists()) {
        Toast.makeText(context, context.getString(R.string.core_pdf_not_found), Toast.LENGTH_SHORT).show()
        return
    }
    val activity = context.findActivity() ?: (context as? Activity)
    if (activity == null) {
        Toast.makeText(context, context.getString(R.string.print_err_failed), Toast.LENGTH_SHORT).show()
        return
    }
    val manager = activity.getSystemService(Context.PRINT_SERVICE) as? android.print.PrintManager
    if (manager == null) {
        Toast.makeText(context, context.getString(R.string.print_err_failed), Toast.LENGTH_SHORT).show()
        return
    }
    runCatching {
        manager.print(
            title,
            PdfFilePrintAdapter(file),
            android.print.PrintAttributes.Builder().build()
        )
    }.onFailure {
        Toast.makeText(context, context.getString(R.string.print_err_failed), Toast.LENGTH_SHORT).show()
    }
}

fun copyFileToUri(context: Context, sourcePath: String, destination: Uri): Boolean = runCatching {
    val source = File(sourcePath)
    // "wt" truncates: plain "w" can leave the tail of a larger old file behind and corrupt the PDF.
    context.contentResolver.openOutputStream(destination, "wt").use { output ->
        requireNotNull(output) { "Could not open export destination" }
        source.inputStream().use { input -> input.copyTo(output) }
    }
    true
}.getOrDefault(false)

private class PdfFilePrintAdapter(
    private val file: File
) : android.print.PrintDocumentAdapter() {
    override fun onLayout(
        oldAttributes: android.print.PrintAttributes?,
        newAttributes: android.print.PrintAttributes?,
        cancellationSignal: android.os.CancellationSignal?,
        callback: LayoutResultCallback,
        extras: android.os.Bundle?
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback.onLayoutCancelled()
            return
        }
        callback.onLayoutFinished(
            android.print.PrintDocumentInfo.Builder(file.name)
                .setContentType(android.print.PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                // The print framework reads the real count from the PDF; the page list can be empty
                // for a document built from the scanner's fallback PDF.
                .setPageCount(android.print.PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                .build(),
            true
        )
    }

    override fun onWrite(
        pages: Array<out android.print.PageRange>?,
        destination: android.os.ParcelFileDescriptor?,
        cancellationSignal: android.os.CancellationSignal?,
        callback: WriteResultCallback
    ) {
        if (destination == null) {
            callback.onWriteFailed(AppStrings.get(R.string.print_err_failed))
            return
        }
        if (cancellationSignal?.isCanceled == true) {
            callback.onWriteCancelled()
            return
        }
        // onWrite is called on the main thread; copy a large PDF in the background to avoid an ANR.
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        Thread {
            val result = runCatching {
                java.io.FileOutputStream(destination.fileDescriptor).use { output ->
                    file.inputStream().use { input -> input.copyTo(output) }
                }
            }
            mainHandler.post {
                result.onSuccess { callback.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES)) }
                    .onFailure { callback.onWriteFailed(it.message ?: AppStrings.get(R.string.print_err_failed)) }
            }
        }.start()
    }
}

/** The app's Play Store package, the release applicationId (debug builds add a ".debug" suffix). */
const val PLAY_STORE_PACKAGE = "com.scanpro.app"

/** Hands the Play listing link to the share sheet, for recommending the app. */
fun sharePlayStoreListing(context: Context) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.core_recommend_app_subject))
        putExtra(Intent.EXTRA_TEXT, context.getString(R.string.core_recommend_app_text, "https://play.google.com/store/apps/details?id=$PLAY_STORE_PACKAGE"))
    }
    try {
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.core_share_app)))
    } catch (_: ActivityNotFoundException) {
        openPlayStoreListing(context)
    }
}

/** Opens ScanPro's listing in the Play Store app, or on the Play website if the Store isn't installed. */
fun openPlayStoreListing(context: Context) {
    val storeApp = Intent(Intent.ACTION_VIEW, "market://details?id=$PLAY_STORE_PACKAGE".toUri())
        .setPackage("com.android.vending")
    val website = Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=$PLAY_STORE_PACKAGE".toUri())
    try {
        context.startActivity(storeApp)
    } catch (_: ActivityNotFoundException) {
        try {
            context.startActivity(website)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, context.getString(R.string.core_google_play_unavailable), Toast.LENGTH_SHORT).show()
        }
    }
}
