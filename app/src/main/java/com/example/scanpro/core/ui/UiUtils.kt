package com.example.scanpro.core.ui

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
import com.example.scanpro.data.ScanDocument
import java.io.File
import java.util.Locale

fun sharePdf(context: Context, document: ScanDocument) {
    val file = File(document.pdfPath)
    if (!file.isFile) {
        Toast.makeText(context, "PDF is not available yet", Toast.LENGTH_SHORT).show()
        return
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TITLE, "${document.title}.pdf")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share PDF"))
}

fun sharePdfs(context: Context, documents: List<ScanDocument>) {
    val valid = documents.mapNotNull { document ->
        val file = File(document.pdfPath)
        if (!file.exists()) null else FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }
    if (valid.isEmpty()) {
        Toast.makeText(context, "No PDF files are available to share", Toast.LENGTH_SHORT).show()
        return
    }
    if (valid.size == 1) {
        val document = documents.firstOrNull { File(it.pdfPath).exists() } ?: return
        sharePdf(context, document)
        return
    }
    val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = "application/pdf"
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(valid))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share PDFs"))
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
        Toast.makeText(context, "JPG images are not available", Toast.LENGTH_SHORT).show()
        return
    }
    val single = uris.size == 1
    val intent = Intent(if (single) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
        type = "image/jpeg"
        if (single) putExtra(Intent.EXTRA_STREAM, uris.first())
        else putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, if (single) "Share JPG" else "Share JPG pages"))
}

fun shareOcrText(context: Context, document: ScanDocument) {
    val text = document.ocrText
    if (text.isBlank()) {
        Toast.makeText(context, "No recognized text to share", Toast.LENGTH_SHORT).show()
        return
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, document.title)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Share recognized text"))
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
        Toast.makeText(context, "Install a PDF viewer to open this file", Toast.LENGTH_LONG).show()
    }
}

fun copyText(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Recognized text", text).apply {
        // Scans often hold IDs and account numbers; keep them out of the clipboard preview on Android 13+.
        description.extras = PersistableBundle().apply {
            putBoolean(if (Build.VERSION.SDK_INT >= 33) ClipDescription.EXTRA_IS_SENSITIVE else "android.content.extra.IS_SENSITIVE", true)
        }
    }
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "Text copied", Toast.LENGTH_SHORT).show()
}

fun pagesLabel(count: Int) = if (count == 1) "1 page" else "$count pages"

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var group = 0
    // 1023.95 so a value that would print as "1024.0" rolls over to the next unit.
    while (value >= 1023.95 && group < units.lastIndex) { value /= 1024.0; group++ }
    return if (group == 0) "$bytes B" else String.format(Locale.getDefault(), "%.1f %s", value, units[group])
}

fun formatTrashCountdown(trashedAt: Long, nowMs: Long = System.currentTimeMillis()): Pair<String, Boolean> {
    val retentionMs = 7L * 24L * 60L * 60L * 1000L
    val expirationMs = trashedAt + retentionMs
    val remainingMs = expirationMs - nowMs

    if (remainingMs <= 0) return Pair("Auto-deletes soon", true)

    val hourMs = 60L * 60L * 1000L
    val dayMs = 24L * hourMs
    val minMs = 60L * 1000L

    val isUrgent = remainingMs < dayMs

    val text = when {
        remainingMs >= dayMs -> {
            val days = kotlin.math.ceil(remainingMs.toDouble() / dayMs.toDouble()).toInt().coerceAtLeast(1)
            "Auto-deletes in $days ${if (days == 1) "day" else "days"}"
        }
        remainingMs >= hourMs -> {
            val hours = kotlin.math.ceil(remainingMs.toDouble() / hourMs.toDouble()).toInt().coerceAtLeast(1)
            "Auto-deletes in $hours ${if (hours == 1) "hour" else "hours"}"
        }
        remainingMs >= minMs -> {
            val mins = kotlin.math.ceil(remainingMs.toDouble() / minMs.toDouble()).toInt().coerceAtLeast(1)
            "Auto-deletes in $mins ${if (mins == 1) "minute" else "minutes"}"
        }
        else -> "Auto-deletes soon"
    }

    return Pair(text, isUrgent)
}

fun shareFile(context: Context, path: String, mimeType: String, chooserTitle: String) {
    val file = File(path)
    if (!file.exists()) {
        Toast.makeText(context, "Export file is missing", Toast.LENGTH_SHORT).show()
        return
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TITLE, file.name)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, chooserTitle))
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
        Toast.makeText(context, "No app can share this file", Toast.LENGTH_SHORT).show()
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
        Toast.makeText(context, "Export file is missing", Toast.LENGTH_SHORT).show()
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

fun printPdf(context: Context, document: ScanDocument) {
    val manager = context.getSystemService(Context.PRINT_SERVICE) as android.print.PrintManager
    val file = File(document.pdfPath)
    manager.print(
        document.title,
        PdfFilePrintAdapter(file),
        android.print.PrintAttributes.Builder().build()
    )
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
            callback.onWriteFailed("Missing print destination")
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
                    .onFailure { callback.onWriteFailed(it.message ?: "Could not print PDF") }
            }
        }.start()
    }
}

/** The app's Play Store package, the release applicationId (debug builds add a ".debug" suffix). */
const val PLAY_STORE_PACKAGE = "com.scanpro.app"

/** Opens ScanPro's listing in the Play Store app, or on the Play website if the Store isn't installed. */
fun openPlayStoreListing(context: Context) {
    val storeApp = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PLAY_STORE_PACKAGE"))
        .setPackage("com.android.vending")
    val website = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$PLAY_STORE_PACKAGE"))
    try {
        context.startActivity(storeApp)
    } catch (_: ActivityNotFoundException) {
        try {
            context.startActivity(website)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "Google Play isn't available on this device", Toast.LENGTH_SHORT).show()
        }
    }
}
