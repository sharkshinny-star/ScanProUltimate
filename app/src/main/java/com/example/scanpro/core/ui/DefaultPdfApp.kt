package com.example.scanpro.core.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.scanpro.R
import java.io.File

/**
 * Whether ScanPro is the app Android opens PDFs with, and the only way an app may ask to become it.
 *
 * Android has no "default PDF app" role an app can request. The default is whatever the person picks with "Always"
 * in the "Open with" sheet, so asking means showing that sheet: a small sample PDF of ScanPro's own is opened with
 * a plain view intent. When another app already holds the default, the sheet no longer appears, and the person has
 * to clear that app's defaults first; [otherAppSettingsIntent] opens the page where that is done.
 */
object DefaultPdfApp {
    enum class State {
        /** ScanPro opens PDFs. */
        OURS,
        /** No app is the default yet: Android will ask. */
        UNSET,
        /** Another app opens PDFs. */
        OTHER
    }

    data class Status(val state: State, val otherPackage: String? = null, val otherLabel: String? = null)

    /** The name the sample PDF travels under; [isProbe] recognises it when the "Open with" sheet hands it back. */
    private const val PROBE_NAME = "ScanPro-PDF.pdf"

    fun status(context: Context): Status {
        val pm = context.packageManager
        // The type alone decides which apps match; the content URI is only a stand-in.
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://${context.packageName}.probe/sample.pdf"), "application/pdf")
        val resolved = runCatching {
            if (Build.VERSION.SDK_INT >= 33) pm.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
            else @Suppress("DEPRECATION") pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }.getOrNull()
        val owner = resolved?.activityInfo?.packageName
        return when {
            owner == null -> Status(State.UNSET)
            owner == context.packageName -> Status(State.OURS)
            // The system's own chooser answers when nothing is the default ("android", or a vendor's resolver).
            owner == "android" || resolved.activityInfo.name.contains("Resolver", ignoreCase = true) -> Status(State.UNSET)
            else -> Status(State.OTHER, owner, runCatching { resolved.loadLabel(pm).toString() }.getOrNull())
        }
    }

    /** Opens the sample PDF so Android shows "Open with"; false when no sheet could be shown. */
    fun ask(context: Context): Boolean = runCatching {
        val file = sampleFile(context)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/pdf")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .apply { if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        )
    }.isSuccess

    /** The other app's page in Android's settings, where "Open by default" can be cleared. */
    fun otherAppSettingsIntent(packageName: String): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** True for the sample PDF coming back through the "Open with" sheet: there is nothing in it to open. */
    fun isProbe(context: Context, uri: Uri?): Boolean =
        uri != null && uri.authority == "${context.packageName}.files" && uri.lastPathSegment == PROBE_NAME

    /** One page saying the choice worked; written once into the shared exports cache. */
    private fun sampleFile(context: Context): File {
        val file = File(File(context.cacheDir, "exports").apply { mkdirs() }, PROBE_NAME)
        if (file.length() > 0) return file
        val document = PdfDocument()
        try {
            val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 22f; textAlign = Paint.Align.CENTER }
            page.canvas.drawText(context.getString(R.string.app_name), 297f, 400f, paint)
            paint.textSize = 14f
            page.canvas.drawText(context.getString(R.string.default_pdf_sample_line), 297f, 430f, paint)
            document.finishPage(page)
            file.outputStream().use(document::writeTo)
        } finally {
            document.close()
        }
        return file
    }
}
