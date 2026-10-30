package com.example.scanpro.feature.tools

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scanpro.core.ui.formatBytes
import com.example.scanpro.imaging.CompressPdfEngine
import com.example.scanpro.imaging.CompressionPreset
import com.example.scanpro.imaging.FormField
import com.example.scanpro.imaging.FormFieldRules
import com.example.scanpro.imaging.ImageExportFormat
import com.example.scanpro.imaging.OcrEngine
import com.example.scanpro.imaging.PdfFormEngine
import com.example.scanpro.imaging.PdfOcrEngine
import com.example.scanpro.data.OcrScript
import com.example.scanpro.imaging.PageSpec
import com.example.scanpro.imaging.PdfProtection
import com.example.scanpro.imaging.PdfToolResult
import com.example.scanpro.imaging.PdfToolsEngine
import com.example.scanpro.imaging.SplitMode
import com.example.scanpro.data.PdfPageSize
import com.example.scanpro.data.PdfQuality
import com.example.scanpro.data.RecentPdf
import com.example.scanpro.data.RecentPdfStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A form being filled in: its fields as read from the file, and what the user has typed or picked so far. */
data class FormSession(val uri: Uri, val fields: List<FormField>, val values: Map<String, String>) {
    val changes: Map<String, String> get() = FormFieldRules.changes(fields, values)
}

/** What the Tools screen shows while and after a tool runs. */
data class ToolProgress(val label: String, val current: Int = 0, val total: Int = 0)

/**
 * Runs the PDF tools outside the screen, so rotating the device, resizing a split-screen window or unfolding a
 * foldable never kills a running job or loses its result, and Cancel really stops the work.
 */
@HiltViewModel
class PdfToolsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val recents: RecentPdfStore,
    private val ocr: OcrEngine
) : ViewModel() {
    /** PDFs picked from the device or a cloud app, newest first, to reopen with one tap. */
    val recentPdfs: StateFlow<List<RecentPdf>> = recents.items

    init {
        // A recent file may have been deleted, or its access revoked, since it was opened.
        viewModelScope.launch(Dispatchers.IO) { recents.prune() }
    }

    fun rememberPdfs(uris: List<Uri>) {
        viewModelScope.launch(Dispatchers.IO) { uris.asReversed().forEach { recents.add(it) } }
    }

    fun forgetPdf(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) { recents.remove(uri) }
    }

    fun clearRecentPdfs() {
        viewModelScope.launch(Dispatchers.IO) { recents.clear() }
    }

    private val _progress = MutableStateFlow<ToolProgress?>(null)
    val progress: StateFlow<ToolProgress?> = _progress.asStateFlow()

    private val _result = MutableStateFlow<PdfToolResult?>(null)
    val result: StateFlow<PdfToolResult?> = _result.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** Set when Unlock PDF is given a file with no open password: there is nothing to unlock. */
    private val _unlockNotice = MutableStateFlow<Pair<Uri, PdfProtection>?>(null)
    val unlockNotice: StateFlow<Pair<Uri, PdfProtection>?> = _unlockNotice.asStateFlow()

    /** Set when Unlock PDF is given a file that needs its password; the screen asks for it. */
    private val _passwordNeeded = MutableStateFlow<Uri?>(null)
    val passwordNeeded: StateFlow<Uri?> = _passwordNeeded.asStateFlow()

    /** True when the last unlock attempt used the wrong password; the reopened dialog says so. */
    private val _wrongPassword = MutableStateFlow(false)
    val wrongPassword: StateFlow<Boolean> = _wrongPassword.asStateFlow()
    fun clearWrongPassword() { _wrongPassword.value = false }

    private var job: Job? = null

    fun clearResult() { _result.value = null }
    fun clearMessage() { _message.value = null }
    fun clearUnlockNotice() { _unlockNotice.value = null }
    fun clearPasswordNeeded() { _passwordNeeded.value = null }

    fun cancel() {
        job?.cancel()
    }

    private fun report(current: Int, total: Int, label: String) {
        _progress.update { ToolProgress(label, current, total) }
    }

    /** One job at a time; a second request while one runs is refused with a message rather than queued silently. */
    private fun launchTool(label: String, failure: String, work: suspend () -> PdfToolResult?) {
        if (job?.isActive == true) {
            _message.value = "Still working on the previous task"
            return
        }
        _result.value = null
        _progress.value = ToolProgress(label)
        job = viewModelScope.launch {
            try {
                _result.value = work()
            } catch (cancelled: CancellationException) {
                _message.value = "Operation cancelled"
                throw cancelled
            } catch (error: Exception) {
                // The engines stop with an InterruptedException when cancelled mid-way; report that as a cancel.
                _message.value = if (error is InterruptedException) "Operation cancelled" else error.localizedMessage ?: failure
            } catch (memory: OutOfMemoryError) {
                // A huge page image is a failed tool run, not a reason to lose the app.
                _message.value = "Not enough memory for this PDF. Try a smaller file or fewer pages."
            } finally {
                _progress.value = null
            }
        }
    }

    private fun bundled(files: List<java.io.File>, prefix: String, message: String): suspend () -> PdfToolResult? = {
        if (files.isEmpty()) null
        else PdfToolResult(
            outputFile = PdfToolsEngine.bundleOutputs(context, files, "${prefix}_${System.currentTimeMillis()}"),
            pageCount = files.size,
            originalSizeBytes = 0,
            outputSizeBytes = files.sumOf { it.length() },
            message = message.format(files.size)
        )
    }

    private fun named(uri: Uri, suffix: String, extension: String = "pdf"): java.io.File =
        PdfToolsEngine.outputLocation(context, "${PdfToolsEngine.sourceBaseName(context, uri)} $suffix.$extension")

    /** Runs [work] in a fresh folder that is removed again if the tool fails or is cancelled part-way. */
    private suspend fun <T> inScratchDir(name: String, work: suspend (java.io.File) -> T): T {
        val dir = PdfToolsEngine.outputLocation(context, name).apply { mkdirs() }
        try {
            return work(dir)
        } catch (error: Throwable) {
            dir.deleteRecursively()
            throw error
        }
    }

    fun compress(uri: Uri, preset: CompressionPreset) = launchTool("Compressing PDF…", "Compression failed") {
        val res = CompressPdfEngine.compressPdf(context, uri, preset, named(uri, "compressed"))
        PdfToolResult(
            outputFile = res.outputFile,
            pageCount = res.pageCount,
            originalSizeBytes = res.originalSizeBytes,
            outputSizeBytes = res.compressedSizeBytes,
            message = "Compressed by ${res.savingsPercentage}% (${formatBytes(res.originalSizeBytes)} → ${formatBytes(res.compressedSizeBytes)})" +
                if (res.isSearchablePreserved) "" else ". Pages were converted to images, so the text is no longer selectable or searchable."
        )
    }

    fun pdfToImages(uri: Uri, format: ImageExportFormat, pages: List<Int>? = null, scale: Float = 2.5f) = launchTool("Exporting pages…", "Export failed") {
        val base = PdfToolsEngine.sourceBaseName(context, uri)
        inScratchDir("$base pages") { dir ->
            val files = PdfToolsEngine.pdfToImages(context, uri, format, dir, ::report, pages, scale)
            bundled(files, "$base pages", "Exported %d page images")()
        }
    }

    fun split(uri: Uri, mode: SplitMode, ranges: String, everyN: Int) = launchTool("Splitting PDF…", "Split failed") {
        val base = PdfToolsEngine.sourceBaseName(context, uri)
        inScratchDir("$base split") { dir ->
            val files = PdfToolsEngine.splitPdf(context, uri, mode, ranges, everyN, dir, ::report)
            bundled(files, "$base split", "Split PDF into %d files")()
        }
    }

    fun protect(uri: Uri, password: String) = launchTool("Encrypting PDF…", "Could not protect the PDF") {
        PdfToolsEngine.protectPdf(context, uri, password, named(uri, "protected"))
    }

    fun unlock(uri: Uri, password: String) = launchTool("Unlocking PDF…", "Could not unlock the PDF") {
        try {
            PdfToolsEngine.unlockPdf(context, uri, password, named(uri, "unlocked"))
        } catch (wrong: com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException) {
            // Ask again for the same file, with the error in the dialog (a snackbar would sit behind it).
            _wrongPassword.value = true
            _passwordNeeded.value = uri
            null
        }
    }

    /** Checks how a PDF is protected before asking for a password it may not have. */
    fun inspectForUnlock(uri: Uri) = launchTool("Checking PDF protection…", "Could not read this PDF") {
        when (val protection = PdfToolsEngine.inspectProtection(context, uri)) {
            PdfProtection.PASSWORD_REQUIRED -> _passwordNeeded.value = uri
            else -> _unlockNotice.value = uri to protection
        }
        null
    }

    fun organize(tool: String, uri: Uri, specs: List<PageSpec>, extracted: List<Int>, rotations: Map<Int, Int>) =
        launchTool("Saving PDF pages…", "Page operation failed") {
            val out = named(uri, when (tool) { "extract" -> "extracted"; "rotate" -> "rotated"; else -> "organised" })
            when (tool) {
                "extract" -> PdfToolsEngine.extractPages(context, uri, extracted, out, ::report)
                "rotate" -> PdfToolsEngine.rotatePages(context, uri, rotations, out, ::report)
                else -> PdfToolsEngine.organizePages(context, uri, specs, out, ::report)
            }
        }

    fun merge(uris: List<Uri>) = launchTool("Merging ${uris.size} PDFs…", "Failed to merge PDFs") {
        PdfToolsEngine.mergePdfs(context, uris, named(uris.first(), "merged"), ::report)
    }

    private val _form = MutableStateFlow<FormSession?>(null)
    val form: StateFlow<FormSession?> = _form.asStateFlow()

    fun openForm(uri: Uri) = launchTool("Reading form fields…", "Could not read this PDF's form") {
        val fields = PdfFormEngine.readFields(context, uri)
        if (fields.isEmpty()) {
            _message.value = "This PDF has no fillable fields. Import it into your documents and use Add text to type on it."
        } else {
            _form.value = FormSession(uri, fields, fields.associate { it.name to it.value })
        }
        null
    }

    fun updateFormValue(name: String, value: String) {
        _form.update { session -> session?.copy(values = session.values + (name to value)) }
    }

    fun closeForm() { _form.value = null }

    fun saveForm(flatten: Boolean) {
        val session = _form.value ?: return
        if (job?.isActive == true) {
            _message.value = "Still working on the previous task"
            return
        }
        // The form window would hide the progress, Cancel and any error, so it closes first; a failure
        // brings it back with the entries intact.
        _form.value = null
        launchTool("Filling form…", "Could not save the filled form") {
            try {
                PdfFormEngine.fill(context, session.uri, session.changes, flatten, named(session.uri, "filled"))
            } catch (cancelled: CancellationException) {
                _form.value = session
                throw cancelled
            } catch (error: Exception) {
                _form.value = session
                throw error
            }
        }
    }

    fun makeSearchable(uri: Uri, script: OcrScript) = launchTool("Making PDF searchable…", "Could not make the PDF searchable") {
        val outcome = PdfOcrEngine.makeSearchable(
            context, uri, script, ocr,
            named(uri, "searchable"), ::report
        )
        val output = outcome.outputFile
        if (output == null) {
            _message.value = if (outcome.alreadyHadText == outcome.pageCount) {
                "This PDF is already searchable: every page has text"
            } else {
                "No readable text was found on the pages without text"
            }
            null
        } else {
            val noun = if (outcome.madeSearchable == 1) "page" else "pages"
            val extras = listOfNotNull(
                outcome.alreadyHadText.takeIf { it > 0 }?.let { "$it already had text" },
                outcome.noTextFound.takeIf { it > 0 }?.let { "$it had no readable text" }
            )
            PdfToolResult(
                outputFile = output,
                pageCount = outcome.pageCount,
                originalSizeBytes = outcome.originalSizeBytes,
                outputSizeBytes = output.length(),
                message = "Made ${outcome.madeSearchable} $noun searchable" + if (extras.isEmpty()) "" else " (${extras.joinToString(", ")})"
            )
        }
    }

    fun imagesToPdf(uris: List<Uri>, pageSize: PdfPageSize = PdfPageSize.AUTO, quality: PdfQuality = PdfQuality.BALANCED) =
        launchTool("Converting ${uris.size} images…", "Failed to convert images") {
            val stamp = java.text.SimpleDateFormat("yyyy-MM-dd HH-mm", java.util.Locale.getDefault()).format(java.util.Date())
            PdfToolsEngine.imagesToPdf(context, uris, pageSize, quality, PdfToolsEngine.outputLocation(context, "Images $stamp.pdf"), ::report)
        }

    /** A picked file's name, for the ordering step before Merge and Images to PDF. */
    fun displayName(uri: Uri): String = PdfToolsEngine.sourceBaseName(context, uri, "File")
}
