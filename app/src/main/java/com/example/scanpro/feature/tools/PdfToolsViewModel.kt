package com.example.scanpro.feature.tools

import kotlinx.coroutines.withContext
import com.example.scanpro.core.text.userError
import com.example.scanpro.imaging.HtmlToPdf
import com.example.scanpro.imaging.OfficeConversionException
import com.example.scanpro.imaging.OfficeToHtml
import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import com.example.scanpro.core.ui.copyFileToUri
import android.provider.DocumentsContract
import com.example.scanpro.R
import com.example.scanpro.core.ui.formatBytes
import com.example.scanpro.imaging.CompressPdfEngine
import com.example.scanpro.imaging.CompressionPreset
import com.example.scanpro.imaging.FormField
import com.example.scanpro.imaging.FormFieldRules
import com.example.scanpro.imaging.ImageCompressConfig
import com.example.scanpro.imaging.ImageExportFormat
import com.example.scanpro.imaging.ImageMetadata
import com.example.scanpro.imaging.ImageResizeConfig
import com.example.scanpro.imaging.ImageToolsEngine
import com.example.scanpro.imaging.MergePasswordNeededException
import com.example.scanpro.imaging.InsertPagesEngine
import com.example.scanpro.imaging.PageInsertion
import com.example.scanpro.imaging.PdfPasswordNeededException
import com.example.scanpro.imaging.PdfToTextEngine
import com.example.scanpro.imaging.TextExportFormat
import com.example.scanpro.imaging.OcrEngine
import com.example.scanpro.imaging.PdfFormEngine
import com.example.scanpro.imaging.PdfOcrEngine
import com.example.scanpro.data.CropQuad
import com.example.scanpro.imaging.PageNumbersConfig
import com.example.scanpro.imaging.WatermarkConfig
import com.example.scanpro.data.OcrScript
import com.example.scanpro.imaging.PageSpec
import com.example.scanpro.imaging.PdfProtection
import com.example.scanpro.imaging.PdfToolResult
import com.example.scanpro.imaging.PdfToolsEngine
import com.example.scanpro.imaging.SignPdfEngine
import com.example.scanpro.imaging.SignPlacement
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
import androidx.annotation.PluralsRes
import com.example.scanpro.core.operations.OperationRunner
import com.example.scanpro.core.notifications.AppNotifications

/** A form being filled in: its fields as read from the file, and what the user has typed or picked so far. */
data class FormSession(val uri: Uri, val fields: List<FormField>, val values: Map<String, String>) {
    val changes: Map<String, String> get() = FormFieldRules.changes(fields, values)
}

/** What the Tools screen shows while and after a tool runs. */
data class ToolProgress(val title: String, val subtitle: String? = null, val current: Int = 0, val total: Int = 0)

/**
 * A PDF in a merge that needs its password: file [index] of [uris], shown as [fileName]. [wrongPassword] is true when
 * the last password typed for it did not work. [attempt] numbers the questions, so each one opens a fresh dialog.
 */
data class MergePasswordRequest(
    val uris: List<Uri>,
    val index: Int,
    val fileName: String,
    val wrongPassword: Boolean,
    val attempt: Int,
)

/** A single-file tool ready to open [uri]: the screen shows that tool's options. */
data class ToolTarget(val tool: String, val uri: Uri)

/** Where unlocked working copies of protected PDFs live while a tool uses them; swept at start-up. */
const val WORKING_COPY_DIR = "tool_input"

/**
 * Runs the PDF tools outside the screen, so rotating the device, resizing a split-screen window or unfolding a
 * foldable never kills a running job or loses its result, and Cancel really stops the work.
 */
@HiltViewModel
class PdfToolsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val recents: RecentPdfStore,
    private val results: com.example.scanpro.data.ToolResultsStore,
    private val ocr: OcrEngine,
    private val operations: OperationRunner,
    private val notifications: AppNotifications,
) : ViewModel() {
    /** PDFs picked from the device or a cloud app, newest first, to reopen with one tap. */
    val recentPdfs: StateFlow<List<RecentPdf>> = recents.items

    /** Everything the tools have made, newest first, kept until the user deletes it. */
    val toolResults: StateFlow<List<com.example.scanpro.data.ToolResultFile>> = results.items

    fun deleteResult(file: File) {
        viewModelScope.launch(Dispatchers.IO) { results.delete(file) }
    }

    init {
        // A recent file may have been deleted, or its access revoked, since it was opened.
        viewModelScope.launch(Dispatchers.IO) { recents.prune() }
        // An unlocked working copy left behind when the app was closed while a tool was open.
        viewModelScope.launch(Dispatchers.IO) { File(context.cacheDir, WORKING_COPY_DIR).deleteRecursively() }
    }

    fun rememberPdfs(uris: List<Uri>) {
        viewModelScope.launch(Dispatchers.IO) { uris.asReversed().forEach { recents.add(it) } }
    }

    /**
     * True if the Recents row for [uri] can still be read. If not, the row is dropped and the reason shown: the file was
     * moved or deleted, or the provider took its access back (a blank viewer told the user nothing).
     */
    suspend fun checkRecent(uri: Uri): Boolean {
        val readable = withContext(Dispatchers.IO) { recents.canRead(uri) }
        if (!readable) {
            forgetPdf(uri)
            _message.value = context.getString(R.string.tools_recent_unavailable)
        }
        return readable
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

    /** Why the last tool run could not finish. Held until the user answers it (try again, another file, close). */
    private val _failure = MutableStateFlow<String?>(null)
    val failure: StateFlow<String?> = _failure.asStateFlow()
    fun clearFailure() { _failure.value = null }

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

    /** Set by a job that ends by asking the user something (and says so itself): it is then not announced as "Done". */
    private var endedWithQuestion = false

    fun clearResult() { _result.value = null }
    fun clearMessage() { _message.value = null }
    fun clearUnlockNotice() { _unlockNotice.value = null }
    fun clearPasswordNeeded() { _passwordNeeded.value = null }

    fun cancel() {
        job?.cancel()
    }

    /**
     * Copies a result to the location the user picked. Runs here rather than in the screen, so turning the
     * phone mid-copy can't cancel it; the outcome arrives as a message either way.
     */
    fun saveTo(file: File, destination: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = copyFileToUri(context, file.absolutePath, destination)
            // The picker already created the destination; don't leave an empty file behind.
            if (!ok) runCatching { DocumentsContract.deleteDocument(context.contentResolver, destination) }
            _message.value = context.getString(if (ok) R.string.tools_saved_to_location else R.string.tools_could_not_save_file)
        }
    }

    private fun report(current: Int, total: Int, subtitle: String) {
        _progress.update { it?.copy(subtitle = subtitle, current = current, total = total) }
    }

    /** One job at a time; a second request while one runs is refused with a message rather than queued silently. */
    private fun launchTool(@StringRes label: Int, @StringRes failure: Int, work: suspend () -> PdfToolResult?) =
        launchTool(context.getString(label), context.getString(failure), work)

    private fun launchTool(label: String, failure: String, work: suspend () -> PdfToolResult?) {
        if (job?.isActive == true) {
            _message.value = context.getString(R.string.msg_tools_still_working)
            return
        }
        _result.value = null
        _failure.value = null
        _progress.value = ToolProgress(title = label)
        endedWithQuestion = false
        job = viewModelScope.launch {
            try {
                // Held alive by the foreground service: a long merge or compression must survive the user switching apps.
                // Off the main thread: tools resolve file names and touch disk before their engines switch dispatchers.
                // The result moves out of the one-day cache into Your results inside keepAlive: a large output is still
                // being copied after the progress reaches 100%, and that copy must survive the user switching apps too.
                val result = operations.keepAlive(label) {
                    withContext(Dispatchers.IO) { work()?.let { it.copy(outputFile = results.keep(it.outputFile)) } }
                }
                _result.value = result
                // The unlocked copy a tool read from has served its purpose once the result is kept.
                if (result != null) releaseWorkingCopy()
                if (!endedWithQuestion) notifications.taskFinished(result?.message)
            } catch (cancelled: CancellationException) {
                _message.value = context.getString(R.string.msg_tools_operation_cancelled)
                throw cancelled
            } catch (error: Exception) {
                // The engines stop with an InterruptedException when cancelled mid-way; report that as a cancel.
                val cancelledMidWay = error is InterruptedException
                val text = if (cancelledMidWay) context.getString(R.string.msg_tools_operation_cancelled) else error.localizedMessage ?: failure
                if (cancelledMidWay) _message.value = text
                else {
                    _failure.value = text
                    notifications.taskFailed(text)
                }
            } catch (_: OutOfMemoryError) {
                // A huge page image is a failed tool run, not a reason to lose the app.
                val text = context.getString(R.string.msg_tools_out_of_memory)
                _failure.value = text
                notifications.taskFailed(text)
            } finally {
                _progress.value = null
            }
        }
    }

    private fun bundled(files: List<File>, prefix: String, @PluralsRes message: Int): suspend () -> PdfToolResult? = {
        if (files.isEmpty()) null
        else PdfToolResult(
            outputFile = PdfToolsEngine.bundleOutputs(context, files, "$prefix ${nameStamp()}"),
            pageCount = files.size,
            originalSizeBytes = 0,
            outputSizeBytes = files.sumOf { it.length() },
            message = context.resources.getQuantityString(message, files.size, files.size),
        )
    }

    /** A readable, file-safe time for output names (the zip title used to show a raw millisecond count). */
    private fun nameStamp(): String = java.text.SimpleDateFormat("yyyy-MM-dd HH-mm-ss", java.util.Locale.US).format(java.util.Date())

    private fun named(uri: Uri, suffix: String, extension: String = "pdf"): File =
        PdfToolsEngine.outputLocation(context, "${PdfToolsEngine.sourceBaseName(context, uri)} $suffix.$extension")

    /** Runs [work] in a fresh folder that is removed again if the tool fails or is cancelled part-way. */
    private suspend fun <T> inScratchDir(name: String, work: suspend (File) -> T): T {
        val dir = PdfToolsEngine.outputLocation(context, name).apply { mkdirs() }
        return try {
            work(dir)
        } catch (error: Throwable) {
            dir.deleteRecursively()
            throw error
        }
    }

    fun compress(uri: Uri, preset: CompressionPreset, customTargetBytes: Long? = null) = launchTool(R.string.msg_tools_compressing, R.string.msg_tools_compression_failed) {
        val res = CompressPdfEngine.compressPdf(context, uri, preset, named(uri, "compressed"), ::report, customTargetBytes)
        PdfToolResult(
            outputFile = res.outputFile,
            pageCount = res.pageCount,
            originalSizeBytes = res.originalSizeBytes,
            outputSizeBytes = res.compressedSizeBytes,
            message = (if (res.compressedSizeBytes >= res.originalSizeBytes) context.getString(R.string.msg_tools_compress_no_gain, formatBytes(context, res.originalSizeBytes))
                else context.getString(R.string.msg_tools_compressed_by, res.savingsPercentage, formatBytes(context, res.originalSizeBytes), formatBytes(context, res.compressedSizeBytes))) +
                (if (res.isSearchablePreserved) "" else context.getString(R.string.msg_tools_compressed_not_searchable)) +
                if (res.missedTarget && customTargetBytes != null) context.getString(R.string.msg_tools_compress_missed_target, formatBytes(context, customTargetBytes)) else "",
            comparesSize = true
        )
    }

    fun pdfToImages(uri: Uri, format: ImageExportFormat, pages: List<Int>? = null, scale: Float = 2.5f) =
        launchTool(R.string.msg_tools_exporting_pages, R.string.msg_tools_export_failed) {
            val base = PdfToolsEngine.sourceBaseName(context, uri)
            inScratchDir("$base pages") { dir ->
                val files = PdfToolsEngine.pdfToImages(context, uri, format, dir, ::report, pages, scale)
                bundled(files, "$base pages", R.plurals.msg_tools_exported_page_images)()
            }
    }

    fun split(uri: Uri, mode: SplitMode, ranges: String, everyN: Int) = launchTool(R.string.msg_tools_splitting, R.string.msg_tools_split_failed) {
        val base = PdfToolsEngine.sourceBaseName(context, uri)
        inScratchDir("$base split") { dir ->
            val files = PdfToolsEngine.splitPdf(context, uri, mode, ranges, everyN, dir, ::report)
            bundled(files, "$base split", R.plurals.msg_tools_split_into_files)()
        }
    }

    fun protect(uri: Uri, password: String) = launchTool(R.string.msg_encrypting_pdf, R.string.msg_tools_protect_failed) {
        PdfToolsEngine.protectPdf(context, uri, password, named(uri, "protected"))
    }

    fun pdfToPowerPoint(uri: Uri) = launchTool(R.string.msg_tools_pdf2ppt_working, R.string.msg_tools_pdf2ppt_failed) {
        val base = PdfToolsEngine.sourceBaseName(context, uri)
        com.example.scanpro.imaging.PdfToPptEngine.convert(context, uri, PdfToolsEngine.outputLocation(context, "$base.pptx"), ::report)
    }

    fun repair(uri: Uri) = launchTool(R.string.msg_tools_repairing, R.string.msg_tools_repair_failed) {
        com.example.scanpro.imaging.PdfRepairEngine.repairPdf(context, uri, named(uri, "repaired"), ::report)
    }

    fun unlock(uri: Uri, password: String) = launchTool(R.string.msg_tools_unlocking, R.string.msg_tools_unlock_failed) {
        try {
            PdfToolsEngine.unlockPdf(context, uri, password, named(uri, "unlocked"))
        } catch (_: com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException) {
            // Ask again for the same file, with the error in the dialog (a snackbar would sit behind it).
            _wrongPassword.value = true
            _passwordNeeded.value = uri
            endedWithQuestion = true
            notifications.taskFailed(context.getString(R.string.tools_wrong_password_try_again))
            null
        }
    }

    /** Checks how a PDF is protected before asking for a password it may not have. */
    fun inspectForUnlock(uri: Uri) = launchTool(R.string.msg_tools_checking_protection, R.string.msg_tools_read_failed) {
        when (val protection = PdfToolsEngine.inspectProtection(context, uri)) {
            PdfProtection.PASSWORD_REQUIRED -> {
                _passwordNeeded.value = uri
                endedWithQuestion = true
                notifications.taskFailed(context.getString(R.string.tools_password_unlock_prompt))
            }
            else -> {
                _unlockNotice.value = uri to protection
                endedWithQuestion = true
            }
        }
        null
    }

    /** Sign PDF: stamps the placed signatures onto a copy of [source] (the screen's working copy of the picked PDF). */
    fun signPdf(source: File, placements: List<SignPlacement>, name: String) =
        launchTool(R.string.msg_sign_saving, R.string.msg_sign_failed) {
            val out = PdfToolsEngine.outputLocation(context, "$name signed.pdf")
            val result = SignPdfEngine.stamp(source, placements, out) { current, total, _ ->
                report(current, total, context.getString(R.string.msg_sign_progress, current, total))
            }
            result.copy(message = context.getString(R.string.msg_sign_done, placements.size))
        }

    fun organize(tool: String, uri: Uri, specs: List<PageSpec>, extracted: List<Int>, rotations: Map<Int, Int>) =
        launchTool(R.string.msg_tools_saving_pages, R.string.msg_tools_page_operation_failed) {
            val out = named(uri, when (tool) { "extract" -> "extracted"; "rotate" -> "rotated"; else -> "organised" })
            when (tool) {
                "extract" -> PdfToolsEngine.extractPages(context, uri, extracted, out, ::report)
                "rotate" -> PdfToolsEngine.rotatePages(context, uri, rotations, out, ::report)
                else -> PdfToolsEngine.organizePages(context, uri, specs, out, ::report)
            }
        }

    private val _mergePassword = MutableStateFlow<MergePasswordRequest?>(null)
    val mergePassword: StateFlow<MergePasswordRequest?> = _mergePassword.asStateFlow()

    /**
     * Passwords typed for the files of the merge being set up. Held in memory only (never in saved state, so never
     * written to disk) and forgotten as soon as that merge ends, fails, is cancelled or another one starts.
     */
    private var mergePasswords: Map<Uri, String> = emptyMap()
    private var mergeQuestions = 0

    fun merge(uris: List<Uri>) {
        if (job?.isActive == true) {
            _message.value = context.getString(R.string.msg_tools_still_working)
            return
        }
        mergePasswords = emptyMap()
        _mergePassword.value = null
        runMerge(uris)
    }

    private fun runMerge(uris: List<Uri>) {
        if (job?.isActive == true) {
            mergePasswords = emptyMap()
            _message.value = context.getString(R.string.msg_tools_still_working)
            return
        }
        val passwords = mergePasswords
        launchTool(context.resources.getQuantityString(R.plurals.msg_tools_merging, uris.size, uris.size), context.getString(R.string.msg_tools_merge_failed)) {
            var asking = false
            try {
                PdfToolsEngine.mergePdfs(context, uris, named(uris.first(), "merged"), ::report, passwords)
            } catch (locked: MergePasswordNeededException) {
                val name = withContext(Dispatchers.IO) { displayName(locked.uri) }
                _mergePassword.value = MergePasswordRequest(uris, locked.index, name, locked.wrongPassword, ++mergeQuestions)
                asking = true
                endedWithQuestion = true
                // Instead of "Done", for a user who switched to another app while the files were read.
                notifications.taskFailed(context.getString(R.string.tools_merge_password_prompt, name))
                null
            } finally {
                if (!asking) mergePasswords = emptyMap()
            }
        }
    }

    /** The password for the file [mergePassword] asked about: the merge starts again, with it. */
    fun submitMergePassword(password: String) {
        val request = _mergePassword.value ?: return
        _mergePassword.value = null
        mergePasswords += (request.uris[request.index] to password)
        // The run that asked ends right after asking; let it finish before the next one starts.
        val previous = job
        viewModelScope.launch {
            previous?.join()
            runMerge(request.uris)
        }
    }

    /** The password question was dismissed: the merge is dropped, and so are the passwords typed for it. */
    fun cancelMergePassword() {
        _mergePassword.value = null
        mergePasswords = emptyMap()
    }

    private val _form = MutableStateFlow<FormSession?>(null)
    val form: StateFlow<FormSession?> = _form.asStateFlow()

    fun openForm(uri: Uri) = launchTool(R.string.msg_tools_reading_form_fields, R.string.msg_tools_read_form_failed) {
        val fields = PdfFormEngine.readFields(context, uri)
        if (fields.isEmpty()) {
            // Not a dead end: the screen offers to type on the same PDF with Sign (F146).
            _formWithoutFields.value = uri
        } else {
            _form.value = FormSession(uri, fields, fields.associateBy({ it.name }) { it.value })
        }
        null
    }

    fun updateFormValue(name: String, value: String) {
        _form.update { session -> session?.copy(values = session.values + (name to value)) }
    }

    fun closeForm() {
        _form.value = null
        releaseWorkingCopy()
    }

    private val _formWithoutFields = MutableStateFlow<Uri?>(null)
    /** A PDF picked for Fill that has no form fields; the screen offers to type on it instead. */
    val formWithoutFields: StateFlow<Uri?> = _formWithoutFields.asStateFlow()

    fun dismissFormWithoutFields() { _formWithoutFields.value = null }

    fun saveForm(flatten: Boolean) {
        val session = _form.value ?: return
        if (job?.isActive == true) {
            _message.value = context.getString(R.string.msg_tools_still_working)
            return
        }
        if (session.changes.isEmpty() && !flatten) {
            _message.value = context.getString(R.string.msg_tools_form_nothing_changed)
            return
        }
        // The form window would hide the progress, Cancel and any error, so it closes first; a failure
        // brings it back with the entries intact.
        _form.value = null
        launchTool(R.string.msg_tools_filling_form, R.string.msg_tools_save_form_failed) {
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

    fun makeSearchable(uri: Uri, script: OcrScript) = launchTool(R.string.msg_tools_making_searchable, R.string.msg_tools_make_searchable_failed) {
        val outcome = PdfOcrEngine.makeSearchable(
            context, uri, script, ocr,
            named(uri, "searchable"), ::report
        )
        val output = outcome.outputFile
        if (output == null) {
            val text = if (outcome.alreadyHadText == outcome.pageCount) {
                context.getString(R.string.msg_tools_already_searchable)
            } else {
                context.getString(R.string.msg_tools_no_readable_text)
            }
            _message.value = text
            // Say what happened, not a bare "Done", to someone who switched apps during a long OCR run.
            endedWithQuestion = true
            notifications.taskFinished(text)
            null
        } else {
            val made = context.resources.getQuantityString(R.plurals.msg_tools_made_pages_searchable, outcome.madeSearchable, outcome.madeSearchable)
            val extras = listOfNotNull(
                outcome.alreadyHadText.takeIf { it > 0 }?.let { context.resources.getQuantityString(R.plurals.msg_tools_already_had_text, it, it) },
                outcome.noTextFound.takeIf { it > 0 }?.let { context.resources.getQuantityString(R.plurals.msg_tools_had_no_readable_text, it, it) }
            )
            PdfToolResult(
                outputFile = output,
                pageCount = outcome.pageCount,
                originalSizeBytes = outcome.originalSizeBytes,
                outputSizeBytes = output.length(),
                message = if (extras.isEmpty()) made else context.getString(R.string.msg_tools_with_extras, made, extras.joinToString(", "))
            )
        }
    }

    /** A protected PDF that PDF to text or Insert pages reached; the screen asks for its password and carries on. */
    private val _sourcePassword = MutableStateFlow<SourcePasswordRequest?>(null)
    val sourcePassword: StateFlow<SourcePasswordRequest?> = _sourcePassword.asStateFlow()
    private var sourcePasswordRetry: ((String) -> Unit)? = null
    private var sourcePasswordQuestions = 0

    private suspend fun askSourcePassword(locked: PdfPasswordNeededException, purpose: PasswordPurpose, retry: (String) -> Unit) {
        val name = withContext(Dispatchers.IO) { displayName(locked.uri) }
        sourcePasswordRetry = retry
        _sourcePassword.value = SourcePasswordRequest(name, locked.wrongPassword, ++sourcePasswordQuestions, purpose)
        endedWithQuestion = true
        // Instead of "Done", for a user who switched to another app while the file was read.
        notifications.taskFailed(context.getString(purpose.prompt, name))
    }

    /** The password for the file [sourcePassword] asked about: the tool runs again with it. It is kept in memory only. */
    fun submitSourcePassword(password: String) {
        val retry = sourcePasswordRetry
        _sourcePassword.value = null
        sourcePasswordRetry = null
        if (retry == null) return
        // The run that asked ends right after asking; let it finish before the next one starts.
        val previous = job
        viewModelScope.launch {
            previous?.join()
            retry(password)
        }
    }

    fun cancelSourcePassword() {
        _sourcePassword.value = null
        sourcePasswordRetry = null
    }

    private val _toolReady = MutableStateFlow<ToolTarget?>(null)
    /** Set when a single-file tool may open its options: on the picked file, or on an unlocked copy of it. */
    val toolReady: StateFlow<ToolTarget?> = _toolReady.asStateFlow()
    fun clearToolReady() { _toolReady.value = null }

    /**
     * The decrypted copy of a protected PDF that the current tool works on, in the app's private cache. It is deleted
     * when the tool's result is kept, when another file is picked, when the form is closed, when the Tools screen goes
     * away, and at the next start if the app was closed in between. The password itself is never kept.
     */
    private var workingCopy: File? = null

    private fun workingCopyDir() = File(context.cacheDir, WORKING_COPY_DIR)

    private fun releaseWorkingCopy() {
        workingCopy?.delete()
        workingCopy = null
    }

    override fun onCleared() {
        releaseWorkingCopy()
        super.onCleared()
    }

    /**
     * First step of every tool that reads one PDF's pages. A file that opens freely goes straight to its options; one
     * that needs a password is asked about once, here, so Compress, Split, Organise, Fill form, Watermark and the rest
     * work on protected files instead of each failing with "unlock it first".
     */
    fun prepareTool(tool: String, uri: Uri) {
        if (job?.isActive == true) {
            _message.value = context.getString(R.string.msg_tools_still_working)
            return
        }
        // "Try again" after a failure runs the tool on the working copy itself: keep it then.
        if (workingCopy?.let(Uri::fromFile) != uri) releaseWorkingCopy()
        cancelSourcePassword()
        _failure.value = null
        val label = context.getString(R.string.msg_tools_checking_protection)
        job = viewModelScope.launch {
            // Most files are answered in milliseconds: the progress card only appears for a slow one.
            val reveal = launch { kotlinx.coroutines.delay(400); _progress.value = ToolProgress(title = label) }
            try {
                if (PdfToolsEngine.needsOpenPassword(context, uri)) askToolPassword(tool, uri, wrongPassword = false)
                else _toolReady.value = ToolTarget(tool, uri)
            } finally {
                reveal.cancel()
                _progress.value = null
            }
        }
    }

    private suspend fun askToolPassword(tool: String, uri: Uri, wrongPassword: Boolean) =
        askSourcePassword(PdfPasswordNeededException(uri, wrongPassword), PasswordPurpose.OPEN) { typed -> unlockForTool(tool, uri, typed) }

    /** Writes an unlocked working copy of [uri] with [password] and opens [tool] on it; a wrong password asks again. */
    private fun unlockForTool(tool: String, uri: Uri, password: String) {
        if (job?.isActive == true) {
            _message.value = context.getString(R.string.msg_tools_still_working)
            return
        }
        _progress.value = ToolProgress(title = context.getString(R.string.msg_tools_unlocking))
        job = viewModelScope.launch {
            try {
                val copy = withContext(Dispatchers.IO) {
                    val dir = workingCopyDir().apply { mkdirs() }
                    // Named after the original, so the tool's results are too ("Invoice compressed.pdf").
                    PdfToolsEngine.uniqueFile(dir, PdfToolsEngine.sourceBaseName(context, uri) + ".pdf")
                }
                if (PdfToolsEngine.decryptCopy(context, uri, password, copy)) {
                    workingCopy = copy
                    _toolReady.value = ToolTarget(tool, Uri.fromFile(copy))
                } else {
                    askToolPassword(tool, uri, wrongPassword = true)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _failure.value = error.localizedMessage ?: context.getString(R.string.msg_tools_unlock_failed)
            } finally {
                _progress.value = null
            }
        }
    }

    fun pdfToText(uri: Uri, format: TextExportFormat, script: OcrScript, password: String? = null) {
        launchTool(R.string.msg_pdf_text_reading, R.string.msg_pdf_text_failed) {
            try {
                val base = PdfToolsEngine.sourceBaseName(context, uri)
                val outcome = PdfToTextEngine.convert(
                    context, uri, format, script, ocr, base,
                    PdfToolsEngine.outputLocation(context, "$base.${format.extension}"), password, ::report
                )
                val parts = listOfNotNull(
                    context.getString(R.string.msg_pdf_text_done, outcome.pageCount - outcome.emptyPages.size, outcome.pageCount),
                    outcome.ocrPages.takeIf { it.isNotEmpty() }
                        ?.let { context.getString(R.string.msg_pdf_text_ocr_pages, PdfToTextEngine.pageList(it)) },
                    outcome.emptyPages.takeIf { it.isNotEmpty() }
                        ?.let { context.getString(R.string.msg_pdf_text_empty_pages, PdfToTextEngine.pageList(it)) },
                    // A protected PDF gives up its protection here: say so, the way Merge does.
                    context.getString(R.string.msg_pdf_text_unprotected).takeIf { password != null }
                )
                PdfToolResult(
                    outputFile = outcome.outputFile,
                    pageCount = outcome.pageCount,
                    // Zero: the result is not a smaller copy of the PDF, so no "saved" badge.
                    originalSizeBytes = 0,
                    outputSizeBytes = outcome.outputFile.length(),
                    message = parts.joinToString(" ")
                )
            } catch (locked: PdfPasswordNeededException) {
                askSourcePassword(locked, PasswordPurpose.TEXT) { typed -> pdfToText(uri, format, script, typed) }
                null
            }
        }
    }

    /** Organise's "insert pages from another PDF": [specs] are the pages as arranged in the sheet. */
    fun insertPages(baseUri: Uri, specs: List<PageSpec>, insertion: PageInsertion, password: String? = null) {
        launchTool(R.string.msg_tools_inserting_pages, R.string.msg_tools_insert_failed) {
            try {
                InsertPagesEngine.insertPages(context, baseUri, specs, insertion, named(baseUri, "inserted"), password, ::report)
            } catch (locked: PdfPasswordNeededException) {
                askSourcePassword(locked, PasswordPurpose.INSERT) { typed -> insertPages(baseUri, specs, insertion, typed) }
                null
            }
        }
    }

    fun imagesToPdf(uris: List<Uri>, pageSize: PdfPageSize = PdfPageSize.AUTO, quality: PdfQuality = PdfQuality.BALANCED) =
        launchTool(context.resources.getQuantityString(R.plurals.msg_tools_converting_images, uris.size, uris.size), context.getString(R.string.msg_tools_convert_images_failed)) {
            val stamp = java.text.SimpleDateFormat("yyyy-MM-dd HH-mm", java.util.Locale.getDefault()).format(java.util.Date())
            try {
                PdfToolsEngine.imagesToPdf(
                    context, uris, pageSize, quality,
                    PdfToolsEngine.outputLocation(context, context.getString(R.string.tools_images_pdf_name, stamp) + ".pdf"), ::report
                )
            } finally {
                withContext(Dispatchers.IO) {
                    uris.asSequence()
                        .mapNotNull { it.path?.let(::File)?.parentFile }
                        .filter { it.name.startsWith("img2pdf_order_") }
                        .distinct()
                        .forEach { it.deleteRecursively() }
                }
            }
        }

    private val _printFallback = MutableStateFlow<Pair<String, String>?>(null)
    /** HTML and a name to hand to the system print dialogue, when the PDF could not be written directly. */
    val printFallback: StateFlow<Pair<String, String>?> = _printFallback.asStateFlow()
    fun clearPrintFallback() { _printFallback.value = null }

    /**
     * Office to PDF: a Word, Excel, text, CSV or saved web page file becomes a PDF, entirely on the phone
     * ([OfficeToHtml], then the print engine via [HtmlToPdf]).
     */
    fun officeToPdf(uri: Uri) = launchTool(R.string.msg_tools_converting_office, R.string.msg_tools_office_failed) {
        val resolver = context.contentResolver
        val fileName = runCatching {
            resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull()
        val kind = OfficeToHtml.kindOf(resolver.getType(uri), fileName) ?: userError(R.string.tools_err_office_unsupported)
        val name = PdfToolsEngine.sourceBaseName(context, uri, context.getString(R.string.tools_file_fallback_name))
        // The sentences the converter writes into the page are in the user's language.
        val notes = OfficeToHtml.Notes(
            imageNotShown = context.getString(R.string.tools_office_note_image),
            workbookEmpty = context.getString(R.string.tools_office_note_workbook_empty),
            fileEmpty = context.getString(R.string.tools_office_note_file_empty),
            fileTooLong = context.getString(R.string.tools_office_note_too_long),
            workbookTruncated = { cells -> context.getString(R.string.tools_office_note_workbook_truncated, cells) },
            fileTruncated = { cells -> context.getString(R.string.tools_office_note_file_truncated, cells) },
        )
        val html = withContext(Dispatchers.IO) {
            try {
                resolver.openInputStream(uri)?.use { OfficeToHtml.convert(kind, it, name, notes) }
            } catch (error: OfficeConversionException) {
                userError(R.string.tools_err_office_unreadable, cause = error)
            }
        } ?: userError(R.string.tools_err_office_unreadable)
        val output = PdfToolsEngine.outputLocation(context, "$name.pdf")
        try {
            HtmlToPdf.write(context, html, output)
        } catch (_: HtmlToPdf.DirectPrintUnavailable) {
            // This Android won't let the app drive the print engine itself: the user saves from the print dialogue.
            _printFallback.value = html to name
            return@launchTool null
        }
        val pages = withContext(Dispatchers.IO) {
            runCatching {
                android.os.ParcelFileDescriptor.open(output, android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    android.graphics.pdf.PdfRenderer(pfd).use { it.pageCount }
                }
            }.getOrDefault(0)
        }
        val sourceSize = runCatching {
            resolver.query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)?.use { c -> if (c.moveToFirst()) c.getLong(0) else 0L }
        }.getOrNull() ?: 0L
        PdfToolResult(output, pages, sourceSize, output.length(), context.resources.getQuantityString(R.plurals.msg_tools_office_done, pages, pages))
    }

    /** A picked file's name, for the ordering step before Merge and Images to PDF. */
    fun displayName(uri: Uri): String = PdfToolsEngine.sourceBaseName(context, uri, context.getString(R.string.tools_file_fallback_name))

    fun compressImage(uri: Uri, config: ImageCompressConfig) =
        launchTool(R.string.tools_progress_compressing_img, R.string.tools_err_compress_img) {
            val out = named(uri, "compressed", config.outputFormat.extension)
            ImageToolsEngine.compressImage(context, uri, config, out, ::report)
        }

    fun convertImage(uris: List<Uri>, targetFormat: ImageExportFormat, quality: Int = 100) =
        launchTool(R.string.tools_progress_converting_img, R.string.tools_err_convert_img) {
            val base = if (uris.size == 1) named(uris.first(), "converted", targetFormat.extension)
            else PdfToolsEngine.outputLocation(context, "converted ${nameStamp()}.${targetFormat.extension}")
            ImageToolsEngine.convertImage(context, uris, targetFormat, quality, base, ::report)
        }

    fun resizeImage(uri: Uri, config: ImageResizeConfig) =
        launchTool(R.string.tools_progress_resizing_img, R.string.tools_err_resize_img) {
            val out = named(uri, "resized_${config.targetWidth}x${config.targetHeight}", config.outputFormat.extension)
            ImageToolsEngine.resizeImage(context, uri, config, out, ::report)
        }

    fun cropImage(uri: Uri, quad: CropQuad, format: ImageExportFormat = ImageExportFormat.JPEG) =
        launchTool(R.string.tools_progress_cropping_img, R.string.tools_err_crop_img) {
            val out = named(uri, "cropped", format.extension)
            ImageToolsEngine.cropImage(context, uri, quad, out, format, ::report)
        }

    fun watermarkPdf(uri: Uri, config: WatermarkConfig) =
        launchTool(R.string.tools_progress_watermarking, R.string.tools_err_watermark) {
            val out = named(uri, "watermarked", "pdf")
            PdfToolsEngine.watermarkPdf(context, uri, config, out, ::report)
        }

    fun addPageNumbers(uri: Uri, config: PageNumbersConfig) =
        launchTool(R.string.tools_progress_page_numbers, R.string.tools_err_page_numbers) {
            val out = named(uri, "numbered", "pdf")
            PdfToolsEngine.addPageNumbers(context, uri, config, out, ::report)
        }

    fun extractText(uri: Uri) =
        launchTool(R.string.tools_progress_extracting_text, R.string.tools_err_pdf2text) {
            val out = named(uri, "extracted_text", "txt")
            PdfToolsEngine.extractText(context, uri, out, ::report)
        }

    suspend fun inspectImage(uri: Uri): ImageMetadata? = withContext(Dispatchers.IO) {
        ImageToolsEngine.inspectImage(context, uri)
    }
}
