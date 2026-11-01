package android.print

import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Runs a [PrintDocumentAdapter] (a WebView's, for Office to PDF) straight into a PDF file, without the print dialog.
 * It sits in package android.print because the result callbacks' constructors are not public in the SDK stubs; this
 * is the usual way apps save HTML as PDF. If a future Android refuses it, the caller falls back to the print dialog.
 */
object ScanProPdfPrinter {
    suspend fun write(adapter: PrintDocumentAdapter, attributes: PrintAttributes, output: File): Unit =
        suspendCancellableCoroutine { continuation ->
            val cancel = CancellationSignal()
            continuation.invokeOnCancellation { cancel.cancel() }
            adapter.onStart()
            adapter.onLayout(null, attributes, cancel, object : PrintDocumentAdapter.LayoutResultCallback() {
                override fun onLayoutFinished(info: PrintDocumentInfo?, changed: Boolean) {
                    val descriptor = try {
                        ParcelFileDescriptor.open(
                            output,
                            ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE or ParcelFileDescriptor.MODE_READ_WRITE
                        )
                    } catch (error: IOException) {
                        adapter.onFinish()
                        if (continuation.isActive) continuation.resumeWithException(error)
                        return
                    }
                    adapter.onWrite(arrayOf(PageRange.ALL_PAGES), descriptor, cancel, object : PrintDocumentAdapter.WriteResultCallback() {
                        override fun onWriteFinished(pages: Array<out PageRange>?) {
                            descriptor.close()
                            adapter.onFinish()
                            if (continuation.isActive) continuation.resume(Unit)
                        }

                        override fun onWriteFailed(error: CharSequence?) {
                            descriptor.close()
                            adapter.onFinish()
                            if (continuation.isActive) continuation.resumeWithException(IOException(error?.toString() ?: "PDF write failed"))
                        }

                        override fun onWriteCancelled() {
                            descriptor.close()
                            adapter.onFinish()
                            if (continuation.isActive) continuation.cancel()
                        }
                    })
                }

                override fun onLayoutFailed(error: CharSequence?) {
                    adapter.onFinish()
                    if (continuation.isActive) continuation.resumeWithException(IOException(error?.toString() ?: "PDF layout failed"))
                }

                override fun onLayoutCancelled() {
                    adapter.onFinish()
                    if (continuation.isActive) continuation.cancel()
                }
            }, null)
        }
}
