package com.example.scanpro.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.scanpro.data.ScanDocument
import java.io.File
import kotlin.math.ln
import kotlin.math.pow

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
    clipboard.setPrimaryClip(ClipData.newPlainText("Recognized text", text))
    Toast.makeText(context, "Text copied", Toast.LENGTH_SHORT).show()
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceIn(0, units.lastIndex)
    val value = bytes / 1024.0.pow(digitGroups.toDouble())
    return if (digitGroups == 0) "${value.toInt()} ${units[digitGroups]}"
    else String.format("%.1f %s", value, units[digitGroups])
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

fun printPdf(context: Context, document: ScanDocument) {
    val manager = context.getSystemService(Context.PRINT_SERVICE) as android.print.PrintManager
    val file = File(document.pdfPath)
    manager.print(
        document.title,
        PdfFilePrintAdapter(file, document.pageCount),
        android.print.PrintAttributes.Builder().build()
    )
}

fun copyFileToUri(context: Context, sourcePath: String, destination: Uri): Boolean = runCatching {
    val source = File(sourcePath)
    context.contentResolver.openOutputStream(destination, "w").use { output ->
        requireNotNull(output) { "Could not open export destination" }
        source.inputStream().use { input -> input.copyTo(output) }
    }
    true
}.getOrDefault(false)

private class PdfFilePrintAdapter(
    private val file: File,
    private val pageCount: Int
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
                .setPageCount(pageCount.coerceAtLeast(1))
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
        runCatching {
            java.io.FileOutputStream(destination.fileDescriptor).use { output ->
                file.inputStream().use { input -> input.copyTo(output) }
            }
        }.onSuccess {
            callback.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
        }.onFailure {
            callback.onWriteFailed(it.message ?: "Could not print PDF")
        }
    }
}
