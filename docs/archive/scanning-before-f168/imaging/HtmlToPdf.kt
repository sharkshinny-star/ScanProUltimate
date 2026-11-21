package com.example.scanpro.imaging

import android.app.Activity
import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.print.ScanProPdfPrinter
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.scanpro.core.ui.findActivity
import java.io.ByteArrayInputStream
import java.io.File
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Lays out an HTML document (from [OfficeToHtml]) into A4 pages with the system's print engine, the same one behind
 * "Save as PDF", so headings and table rows are not cut in half. The page is shown in an offscreen WebView with
 * scripts, file and content access off, and every network request refused: nothing leaves the phone.
 */
object HtmlToPdf {
    /** Thrown when the print engine can't be driven directly; the caller then offers the system print dialogue. */
    class DirectPrintUnavailable(cause: Throwable) : Exception(cause)

    val A4: PrintAttributes = PrintAttributes.Builder()
        .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
        .setResolution(PrintAttributes.Resolution("pdf", "pdf", 600, 600))
        .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
        .build()

    /** Writes [html] to [output] as a PDF. Must be able to reach the main thread (the WebView lives there). */
    suspend fun write(context: Context, html: String, output: File) {
        val webView = withContext(Dispatchers.Main) { load(context.applicationContext, html) }
        try {
            withContext(Dispatchers.Main) {
                val adapter = webView.createPrintDocumentAdapter(output.nameWithoutExtension)
                try {
                    ScanProPdfPrinter.write(adapter, A4, output)
                } catch (error: LinkageError) {
                    throw DirectPrintUnavailable(error)
                } catch (error: SecurityException) {
                    throw DirectPrintUnavailable(error)
                }
            }
        } finally {
            withContext(Dispatchers.Main) { webView.destroy() }
        }
    }

    /**
     * Fallback: opens the system print dialogue for [html] (the user picks "Save as PDF"). [context] is resolved
     * to an Activity, as the dialogue needs one.
     */
    suspend fun printWithDialog(context: Context, html: String, jobName: String) {
        val activity = context.findActivity() ?: (context as? Activity) ?: return
        val webView = withContext(Dispatchers.Main) { load(activity, html) }
        withContext(Dispatchers.Main) {
            val manager = activity.getSystemService(PrintManager::class.java) ?: return@withContext
            runCatching {
                manager.print(jobName, webView.createPrintDocumentAdapter(jobName), A4)
            }
        }
    }

    private suspend fun load(context: Context, html: String): WebView = suspendCancellableCoroutine { continuation ->
        val view = WebView(context)
        view.settings.apply {
            javaScriptEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            blockNetworkLoads = true
            loadsImagesAutomatically = true
        }
        view.webViewClient = object : WebViewClient() {
            // Images arrive inline as data: URIs; anything else the document points at is refused.
            override fun shouldInterceptRequest(target: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val url = request?.url?.toString().orEmpty()
                return if (url.startsWith("data:")) null
                else WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
            }

            override fun shouldOverrideUrlLoading(target: WebView?, request: WebResourceRequest?) = true

            override fun onPageFinished(loaded: WebView?, url: String?) {
                if (continuation.isActive) continuation.resume(view)
            }
        }
        continuation.invokeOnCancellation { view.destroy() }
        view.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
    }
}
