package com.example.scanpro.feature.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.graphics.createBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scanpro.data.SignatureLibrary
import com.example.scanpro.imaging.ImageProcessor
import com.example.scanpro.imaging.PdfToolsEngine
import com.example.scanpro.imaging.SignPdfEngine
import com.example.scanpro.imaging.SignPlacement
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlin.math.ceil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the Sign PDF screen shows. [visible] false means the tool is not open. */
data class SignUiState(
    val visible: Boolean = false,
    val loading: Boolean = false,
    /** Set when the file needs a password to open; [wrongPassword] when the last one given did not work. */
    val passwordFor: Uri? = null,
    val wrongPassword: Boolean = false,
    val failed: Boolean = false,
    val file: File? = null,
    val sourceName: String = "",
    val pageCount: Int = 0,
    val wasProtected: Boolean = false,
    val page: Int = 0,
    val placements: List<SignPlacement> = emptyList(),
    /** Index into [placements] of the stamp being edited, or -1. */
    val selected: Int = -1
)

/**
 * Holds the Sign PDF session (a working copy of the PDF and the stamps placed so far) outside the screen, so
 * turning the phone loses nothing. Passwords are used once to make the working copy and never stored. Everything
 * here is app-private cache and is deleted when the tool is closed.
 */
@HiltViewModel
class SignPdfViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val library: SignatureLibrary
) : ViewModel() {
    private val _state = MutableStateFlow(SignUiState())
    val state: StateFlow<SignUiState> = _state.asStateFlow()

    /** Signatures kept earlier, newest first. */
    val savedSignatures: StateFlow<List<File>> = library.items

    private var openJob: Job? = null

    private fun stampDir(): File = File(context.cacheDir, "sign_stamps").also { it.mkdirs() }

    private fun newStampFile(): File = File(stampDir(), "stamp_${System.nanoTime()}.png")

    /** Opens [uri] for signing; [password] is given on the second try for a protected file. */
    fun open(uri: Uri, password: String? = null) {
        openJob?.cancel()
        deleteWorkingFiles(_state.value.file)
        _state.value = SignUiState(visible = true, loading = true)
        openJob = viewModelScope.launch {
            try {
                val copy = SignPdfEngine.prepare(context, uri, password)
                if (copy == null) {
                    _state.value = SignUiState(visible = true, passwordFor = uri)
                    return@launch
                }
                val pages = try {
                    withContext(Dispatchers.IO) { countPages(copy.file) }
                } catch (error: Exception) {
                    copy.file.delete()
                    throw error
                }
                val name = withContext(Dispatchers.IO) { PdfToolsEngine.sourceBaseName(context, uri) }
                _state.value = SignUiState(
                    visible = true,
                    file = copy.file,
                    sourceName = name,
                    pageCount = pages,
                    wasProtected = copy.wasProtected
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: InvalidPasswordException) {
                _state.value = SignUiState(visible = true, passwordFor = uri, wrongPassword = true)
            } catch (_: Exception) {
                _state.value = SignUiState(visible = true, failed = true)
            }
        }
    }

    private fun countPages(file: File): Int =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer -> renderer.pageCount }
        }

    /** Closes the tool and removes the working copy and every stamp picture. */
    fun close() {
        openJob?.cancel()
        deleteWorkingFiles(_state.value.file)
        _state.value = SignUiState()
    }

    private fun deleteWorkingFiles(file: File?) {
        file?.delete()
        File(context.cacheDir, "sign_stamps").deleteRecursively()
    }

    fun showPage(index: Int) {
        _state.update { s ->
            if (s.pageCount == 0) s else s.copy(page = index.coerceIn(0, s.pageCount - 1), selected = -1)
        }
    }

    /** A signature drawn just now; kept in the library for next time when [keep] is true. */
    fun addDrawnSignature(signature: Bitmap, keep: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val stamp = newStampFile()
            ImageProcessor.saveSignature(signature, stamp)
            if (keep) library.save(signature)
            addPlacement(stamp, SIGNATURE_WIDTH)
        }
    }

    /** A signature from the library, copied so deleting it from the library later cannot break this session. */
    fun addSavedSignature(saved: File) {
        viewModelScope.launch(Dispatchers.IO) {
            val stamp = newStampFile()
            saved.copyTo(stamp, overwrite = true)
            addPlacement(stamp, SIGNATURE_WIDTH)
        }
    }

    /** A typed stamp, such as a date, drawn to a transparent picture so any script the phone can show is kept. */
    fun addText(text: String) {
        val clean = text.replace('\n', ' ').trim().take(MAX_TEXT_LENGTH)
        if (clean.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val stamp = newStampFile()
            val bitmap = textBitmap(clean)
            try {
                ImageProcessor.saveSignature(bitmap, stamp)
            } finally {
                bitmap.recycle()
            }
            addPlacement(stamp, TEXT_WIDTH)
        }
    }

    private fun addPlacement(file: File, widthFraction: Float) {
        _state.update { s ->
            val placed = SignPlacement(s.page, file, 0.5f, 0.8f, widthFraction, 0f)
            s.copy(placements = s.placements + placed, selected = s.placements.size)
        }
    }

    fun select(index: Int) {
        _state.update { s -> if (index in s.placements.indices) s.copy(selected = index) else s.copy(selected = -1) }
    }

    /** Moves a stamp by a fraction of the page's width and height. */
    fun move(index: Int, dx: Float, dy: Float) = edit(index) {
        it.copy(centerX = (it.centerX + dx).coerceIn(0f, 1f), centerY = (it.centerY + dy).coerceIn(0f, 1f))
    }

    fun resize(index: Int, widthFraction: Float) = edit(index) {
        it.copy(widthFraction = widthFraction.coerceIn(MIN_WIDTH, MAX_WIDTH))
    }

    fun rotate(index: Int, degrees: Float) = edit(index) { it.copy(rotationDegrees = degrees) }

    fun remove(index: Int) {
        _state.update { s ->
            if (index !in s.placements.indices) s
            else s.copy(placements = s.placements.filterIndexed { i, _ -> i != index }, selected = -1)
        }
    }

    private fun edit(index: Int, change: (SignPlacement) -> SignPlacement) {
        _state.update { s ->
            if (index !in s.placements.indices) s
            else s.copy(placements = s.placements.mapIndexed { i, p -> if (i == index) change(p) else p })
        }
    }

    /** Page [index] of the working copy at [width] pixels wide, or null when it cannot be drawn. */
    suspend fun renderPage(file: File, index: Int, width: Int): Bitmap? = withContext(Dispatchers.IO) {
        try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    renderer.openPage(index).use { page ->
                        val height = (width * page.height.toFloat() / page.width.toFloat()).toInt().coerceIn(1, MAX_PAGE_HEIGHT)
                        val bitmap = createBitmap(width, height)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmap
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    fun deleteSavedSignature(file: File) {
        viewModelScope.launch(Dispatchers.IO) { library.delete(file) }
    }

    private fun textBitmap(text: String): Bitmap {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF111111.toInt()
            textSize = TEXT_SIZE
        }
        val padding = TEXT_PADDING
        val metrics = paint.fontMetrics
        val width = ceil(paint.measureText(text)).toInt() + padding * 2
        val height = ceil(metrics.descent - metrics.ascent).toInt() + padding * 2
        val bitmap = createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1))
        Canvas(bitmap).drawText(text, padding.toFloat(), padding - metrics.ascent, paint)
        return bitmap
    }

    override fun onCleared() {
        // The session is app-private cache; drop it with the screen's owner rather than leave pages around.
        deleteWorkingFiles(_state.value.file)
        super.onCleared()
    }

    companion object {
        const val MIN_WIDTH = 0.05f
        const val MAX_WIDTH = 0.9f
        private const val SIGNATURE_WIDTH = 0.35f
        private const val TEXT_WIDTH = 0.3f
        private const val MAX_TEXT_LENGTH = 80
        private const val MAX_PAGE_HEIGHT = 4000
        private const val TEXT_SIZE = 96f
        private const val TEXT_PADDING = 16
    }
}
