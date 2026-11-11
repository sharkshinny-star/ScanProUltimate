package com.example.scanpro.imaging

import android.content.Context
import android.net.Uri
import com.example.scanpro.R
import com.example.scanpro.core.text.userError
import com.example.scanpro.core.text.userRequire
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.util.Matrix
import java.io.File
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * One signature (or text stamp) to put on a page. Positions are fractions of the page as it is shown, as
 * PdfRenderer draws it (origin top-left, y down): [centerX] and [centerY] are the middle of the stamp, [widthFraction]
 * its width as a fraction of the shown page width (its height follows the picture's own proportions), and
 * [rotationDegrees] turns it clockwise about its middle.
 */
data class SignPlacement(
    val pageIndex: Int,
    val imageFile: File,
    val centerX: Float,
    val centerY: Float,
    val widthFraction: Float,
    val rotationDegrees: Float = 0f
)

/** A copy of the picked PDF in app cache, with any password or restriction removed so it can be rendered and stamped. */
class SignWorkingCopy(val file: File, val wasProtected: Boolean)

/** Pure geometry for stamping: from the shown page to PDF user space. */
internal object SignGeometry {
    /**
     * The six numbers of the `cm` matrix (a, b, c, d, e, f) that draws a 1 x 1 image so it lands where a [SignPlacement]
     * says on a page with the given [pageRotation] and crop box ([x0], [y0], [boxWidth], [boxHeight]) in user space.
     * [imageAspect] is the picture's height divided by its width.
     */
    fun imageMatrix(
        centerX: Float,
        centerY: Float,
        widthFraction: Float,
        rotationDegrees: Float,
        imageAspect: Float,
        pageRotation: Int,
        x0: Float,
        y0: Float,
        boxWidth: Float,
        boxHeight: Float
    ): FloatArray {
        val shown = PdfTextLayerMath.shownSize(boxWidth, boxHeight, pageRotation)
        val shownWidth = shown.first
        val shownHeight = shown.second
        val width = widthFraction * shownWidth
        val height = width * imageAspect
        val angle = Math.toRadians(rotationDegrees.toDouble())
        val cosA = cos(angle).toFloat()
        val sinA = sin(angle).toFloat()

        // A point of the picture: s across (0..1) and t up (0..1), as PDF draws an image, mapped to user space.
        fun point(s: Float, t: Float): PagePoint {
            val localX = (s - 0.5f) * width
            val localY = -(t - 0.5f) * height
            val shownX = centerX * shownWidth + localX * cosA - localY * sinA
            val shownY = centerY * shownHeight + localX * sinA + localY * cosA
            return PdfTextLayerMath.toPageSpace(shownX / shownWidth, shownY / shownHeight, pageRotation, x0, y0, boxWidth, boxHeight)
        }

        val origin = point(0f, 0f)
        val right = point(1f, 0f)
        val up = point(0f, 1f)
        return floatArrayOf(right.x - origin.x, right.y - origin.y, up.x - origin.x, up.y - origin.y, origin.x, origin.y)
    }
}

/** Sign PDF: stamps signature pictures and text pictures onto pages of a copy of a PDF. The original is never touched. */
object SignPdfEngine {
    /**
     * Copies [uri] into app cache for editing. Returns null when the file needs a password and none was given; a wrong
     * one throws InvalidPasswordException. Protected files (password or restrictions) are written out unprotected,
     * which is why the signed copy is unprotected too; the screen says so.
     */
    suspend fun prepare(context: Context, uri: Uri, password: String?): SignWorkingCopy? = withContext(Dispatchers.IO) {
        val copy = PdfToolsEngine.copyUriToTemp(context, uri, "sign_src_${UUID.randomUUID()}.pdf")
        try {
            val protection = PdfSecurity.protection(copy)
            if (protection == PdfProtection.NONE) {
                SignWorkingCopy(copy, wasProtected = false)
            } else if (protection == PdfProtection.PASSWORD_REQUIRED && password == null) {
                copy.delete()
                null
            } else {
                val unlocked = File(context.cacheDir, "sign_work_${UUID.randomUUID()}.pdf")
                try {
                    PdfSecurity.unlock(copy, unlocked, password.orEmpty())
                } catch (error: Throwable) {
                    unlocked.delete()
                    throw error
                }
                copy.delete()
                SignWorkingCopy(unlocked, wasProtected = true)
            }
        } catch (error: Throwable) {
            copy.delete()
            throw error
        }
    }

    /** Writes [source] with every placement stamped on to [output] and returns the usual tool result. */
    suspend fun stamp(
        source: File,
        placements: List<SignPlacement>,
        output: File,
        onProgress: (current: Int, total: Int, label: String) -> Unit = { _, _, _ -> }
    ): PdfToolResult = withContext(Dispatchers.IO) {
        userRequire(placements.isNotEmpty(), R.string.sign_err_nothing_placed)
        output.parentFile?.mkdirs()
        val originalSize = source.length()
        var pageCount = 0
        try {
            PdfToolsEngine.loadPdf(source).use { document ->
                pageCount = document.numberOfPages
                val images = HashMap<String, PDImageXObject>()
                val byPage = placements.groupBy { it.pageIndex }
                var done = 0
                for (pageIndex in byPage.keys.sorted()) {
                    ensureActive()
                    userRequire(pageIndex in 0 until pageCount, R.string.sign_err_page_missing)
                    done += 1
                    onProgress(done, byPage.size, "")
                    val page = document.getPage(pageIndex)
                    val box = page.cropBox
                    // Appended after the page's own content, with its graphics state reset so nothing it left behind
                    // (a scale, a clip) moves or hides the stamp.
                    PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
                        for (placement in byPage.getValue(pageIndex)) {
                            val key = placement.imageFile.absolutePath
                            val image = images.getOrPut(key) { loadImage(document, placement.imageFile) }
                            val m = SignGeometry.imageMatrix(
                                placement.centerX, placement.centerY, placement.widthFraction, placement.rotationDegrees,
                                image.height.toFloat() / image.width.toFloat(),
                                page.rotation, box.lowerLeftX, box.lowerLeftY, box.width, box.height
                            )
                            stream.drawImage(image, Matrix(m[0], m[1], m[2], m[3], m[4], m[5]))
                        }
                    }
                }
                document.save(output)
            }
        } catch (error: Throwable) {
            // A failed or cancelled run leaves no half-written file behind.
            output.delete()
            throw error
        }
        PdfToolResult(
            outputFile = output,
            pageCount = pageCount,
            originalSizeBytes = originalSize,
            outputSizeBytes = output.length(),
            message = ""
        )
    }

    /** The picture as a PDF image with its transparency kept (a signature has none of the page's white around it). */
    private fun loadImage(document: PDDocument, file: File): PDImageXObject {
        val bitmap = ImageProcessor.decodeScaled(file.absolutePath, MAX_STAMP_PIXELS) ?: userError(R.string.sign_err_image)
        try {
            return LosslessFactory.createFromImage(document, bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    private const val MAX_STAMP_PIXELS = 1600
}
