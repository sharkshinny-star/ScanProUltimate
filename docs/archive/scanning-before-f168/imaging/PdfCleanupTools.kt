package com.example.scanpro.imaging

import com.example.scanpro.core.concurrent.BackgroundWork
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.graphics.createBitmap
import com.example.scanpro.R
import com.example.scanpro.core.text.AppStrings
import com.example.scanpro.core.text.userError
import com.example.scanpro.core.text.userRequire
import com.example.scanpro.scanner.BlankPage
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotation
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationPopup
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.util.Matrix
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.IdentityHashMap
import java.util.UUID

/**
 * Four offline clean-up tools on one PDF, each writing a new copy (the original is never changed):
 * remove blank pages, greyscale, flatten, and extract images. The page drawing goes through Android's PdfRenderer; the
 * decisions and the PDF writing are separate functions that the JVM tests drive with drawn bitmaps.
 */
object PdfCleanupTools {
    /** Long side, in pixels, that a page is drawn at to judge whether it is blank: enough for [BlankPage], and fast. */
    const val BLANK_RENDER_SIDE = 240

    /** Greyscale pages are redrawn at this resolution: sharp for reading and printing, without bloating the file. */
    const val GREYSCALE_DPI = 150

    /** JPEG quality of a greyscale page: text stays crisp at [GREYSCALE_DPI]. */
    const val GREYSCALE_JPEG_QUALITY = 82

    /** The longest side a greyscale page is drawn at, so a poster-sized page cannot run out of memory. */
    private const val GREYSCALE_MAX_SIDE = 3500

    /** Images smaller than this on either side are icons, bullets or one-pixel spacers, not pictures worth saving. */
    const val MIN_IMAGE_SIDE = 16

    /** 0-based indices of the pages that look blank, and how many pages the PDF has. */
    data class BlankScan(val pageCount: Int, val blankPages: List<Int>)

    /** What [flattenDocument] did: form fields drawn into the pages, annotations drawn in, notes it could not draw. */
    data class FlattenCount(val fields: Int, val annotations: Int)

    // ---- Remove blank pages -------------------------------------------------------------------------------------

    /**
     * The pages that are blank: no text, no annotation (other than a link), and a drawn page [BlankPage] calls blank.
     * Deliberately cautious, like [BlankPage]: a page with any real content must never be removed.
     */
    internal suspend fun blankPages(
        pageCount: Int,
        hasContent: (Int) -> Boolean,
        luma: (Int) -> ByteArray?,
        onPage: (Int) -> Unit = {}
    ): List<Int> {
        val blank = mutableListOf<Int>()
        for (i in 0 until pageCount) {
            if (!currentCoroutineContext().isActive) throw InterruptedException("Blank page check cancelled")
            onPage(i)
            if (hasContent(i)) continue
            val pixels = luma(i) ?: continue
            if (BlankPage.isBlank(pixels)) blank += i
        }
        return blank
    }

    /** True when the page has text or an annotation a reader would see (a link alone is not content). */
    internal fun pageHasContent(doc: PDDocument, index: Int, stripper: PDFTextStripper): Boolean {
        val page = doc.getPage(index)
        val annotations = runCatching { page.annotations }.getOrDefault(emptyList())
        if (annotations.any { it !is PDAnnotationLink && it !is PDAnnotationPopup && !it.isHidden && !it.isNoView }) return true
        stripper.startPage = index + 1
        stripper.endPage = index + 1
        return runCatching { stripper.getText(doc).isNotBlank() }.getOrDefault(true)
    }

    /** 8-bit brightness of every pixel of [bitmap], as [BlankPage] reads it. */
    internal fun luma(bitmap: Bitmap): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        val row = IntArray(width)
        val out = ByteArray(width * height)
        for (y in 0 until height) {
            bitmap.getPixels(row, 0, width, 0, y, width, 1)
            for (x in 0 until width) out[y * width + x] = greyLevel(row[x]).toByte()
        }
        return out
    }

    /** Rec. 601 brightness of an ARGB colour, 0 to 255 (fixed point, no floating maths per pixel). */
    internal fun greyLevel(argb: Int): Int {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return (r * 299 + g * 587 + b * 114 + 500) / 1000
    }

    suspend fun findBlankPages(
        context: Context,
        inputUri: Uri,
        onProgress: (current: Int, total: Int, label: String) -> Unit = { _, _, _ -> }
    ): BlankScan = withContext(BackgroundWork.tools) {
        val temp = PdfToolsEngine.copyUriToTemp(context, inputUri, "blank_in_${UUID.randomUUID()}.pdf")
        var bitmap: Bitmap? = null
        try {
            PdfToolsEngine.loadPdf(temp).use { doc ->
                val total = doc.numberOfPages
                userRequire(total > 0, R.string.tools_err_no_pages)
                val stripper = PDFTextStripper()
                ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfToolsEngine.openRenderer(pfd, temp).use { renderer ->
                        val blank = blankPages(
                            pageCount = minOf(total, renderer.pageCount),
                            hasContent = { pageHasContent(doc, it, stripper) },
                            luma = { index ->
                                renderer.openPage(index).use { page ->
                                    val scale = BLANK_RENDER_SIDE.toFloat() / maxOf(page.width, page.height).coerceAtLeast(1)
                                    val w = (page.width * scale).toInt().coerceAtLeast(1)
                                    val h = (page.height * scale).toInt().coerceAtLeast(1)
                                    val canvas = bitmap?.takeIf { it.width == w && it.height == h }
                                        ?: createBitmap(w, h).also { bitmap?.recycle(); bitmap = it }
                                    canvas.eraseColor(Color.WHITE)
                                    page.render(canvas, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    luma(canvas)
                                }
                            },
                            onPage = { onProgress(it + 1, total, AppStrings.get(R.string.tools_new_progress_page, it + 1, total)) }
                        )
                        BlankScan(total, blank)
                    }
                }
            }
        } finally {
            bitmap?.recycle()
            temp.delete()
        }
    }

    /** Writes [inputUri] without the 0-based [pages] to [outputFile]. At least one page must stay. */
    suspend fun removePages(
        context: Context,
        inputUri: Uri,
        pages: Set<Int>,
        outputFile: File,
        onProgress: (current: Int, total: Int, label: String) -> Unit = { _, _, _ -> }
    ): PdfToolResult = withContext(BackgroundWork.tools) {
        val temp = PdfToolsEngine.copyUriToTemp(context, inputUri, "blank_rm_${UUID.randomUUID()}.pdf")
        try {
            PdfToolsEngine.loadPdf(temp).use { doc ->
                val total = doc.numberOfPages
                val removing = pages.filter { it in 0 until total }.sortedDescending()
                userRequire(removing.size < total, R.string.tools_blank_err_all)
                removing.forEachIndexed { step, index ->
                    if (!currentCoroutineContext().isActive) throw InterruptedException("Remove pages cancelled")
                    onProgress(step + 1, removing.size, AppStrings.get(R.string.tools_blank_progress_removing))
                    doc.removePage(index)
                }
                outputFile.parentFile?.mkdirs()
                doc.save(outputFile)
                PdfToolResult(
                    outputFile = outputFile,
                    pageCount = doc.numberOfPages,
                    originalSizeBytes = temp.length(),
                    outputSizeBytes = outputFile.length(),
                    message = AppStrings.plural(R.plurals.tools_blank_done, removing.size, removing.size)
                )
            }
        } catch (error: Throwable) {
            outputFile.delete()
            throw error
        } finally {
            temp.delete()
        }
    }

    // ---- Greyscale ------------------------------------------------------------------------------------------------

    /** Turns [bitmap] grey in place, row by row so a large page needs no second full-size buffer. */
    internal fun toGreyscale(bitmap: Bitmap) {
        val width = bitmap.width
        val row = IntArray(width)
        for (y in 0 until bitmap.height) {
            bitmap.getPixels(row, 0, width, 0, y, width, 1)
            for (x in 0 until width) {
                val v = greyLevel(row[x])
                row[x] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
            }
            bitmap.setPixels(row, 0, width, 0, y, width, 1)
        }
    }

    /**
     * Builds a PDF of [pageCount] greyscale page images into [outputFile]. [pageSize] gives each page's size in points
     * and [draw] draws a page onto a white bitmap of the size asked; both come from the renderer, or from a test.
     */
    internal suspend fun writeGreyscale(
        pageCount: Int,
        pageSize: (Int) -> Pair<Float, Float>,
        draw: (index: Int, target: Bitmap) -> Unit,
        outputFile: File,
        onPage: (Int) -> Unit = {}
    ) {
        var bitmap: Bitmap? = null
        try {
            PDDocument(MemoryUsageSetting.setupTempFileOnly()).use { out ->
                val buffer = ByteArrayOutputStream()
                for (i in 0 until pageCount) {
                    if (!currentCoroutineContext().isActive) throw InterruptedException("Greyscale cancelled")
                    onPage(i)
                    val (widthPt, heightPt) = pageSize(i)
                    val scale = minOf(GREYSCALE_DPI / 72f, GREYSCALE_MAX_SIDE / maxOf(widthPt, heightPt).coerceAtLeast(1f))
                    val w = (widthPt * scale).toInt().coerceAtLeast(1)
                    val h = (heightPt * scale).toInt().coerceAtLeast(1)
                    val canvas = bitmap?.takeIf { it.width == w && it.height == h }
                        ?: createBitmap(w, h).also { bitmap?.recycle(); bitmap = it }
                    canvas.eraseColor(Color.WHITE)
                    draw(i, canvas)
                    toGreyscale(canvas)
                    buffer.reset()
                    canvas.compress(Bitmap.CompressFormat.JPEG, GREYSCALE_JPEG_QUALITY, buffer)
                    val image = JPEGFactory.createFromByteArray(out, buffer.toByteArray())
                    val page = PDPage(PDRectangle(widthPt, heightPt))
                    out.addPage(page)
                    PDPageContentStream(out, page).use { it.drawImage(image, 0f, 0f, widthPt, heightPt) }
                }
                outputFile.parentFile?.mkdirs()
                out.save(outputFile)
            }
        } catch (error: Throwable) {
            outputFile.delete()
            throw error
        } finally {
            bitmap?.recycle()
        }
    }

    suspend fun greyscale(
        context: Context,
        inputUri: Uri,
        outputFile: File,
        onProgress: (current: Int, total: Int, label: String) -> Unit = { _, _, _ -> }
    ): PdfToolResult = withContext(BackgroundWork.tools) {
        val temp = PdfToolsEngine.copyUriToTemp(context, inputUri, "grey_in_${UUID.randomUUID()}.pdf")
        try {
            val total = ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfToolsEngine.openRenderer(pfd, temp).use { renderer ->
                    val total = renderer.pageCount
                    userRequire(total > 0, R.string.tools_err_no_pages)
                    // The renderer opens one page at a time: the open page is drawn and closed before the next.
                    var open: PdfRenderer.Page? = null
                    fun page(i: Int): PdfRenderer.Page = open?.takeIf { it.index == i } ?: renderer.openPage(i).also { open?.close(); open = it }
                    try {
                        writeGreyscale(
                            pageCount = total,
                            pageSize = { i -> page(i).let { it.width.toFloat() to it.height.toFloat() } },
                            draw = { i, target -> page(i).render(target, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT) },
                            outputFile = outputFile,
                            onPage = { onProgress(it + 1, total, AppStrings.get(R.string.tools_new_progress_page, it + 1, total)) }
                        )
                    } finally {
                        open?.close()
                    }
                    total
                }
            }
            PdfToolResult(
                outputFile = outputFile,
                pageCount = total,
                originalSizeBytes = temp.length(),
                outputSizeBytes = outputFile.length(),
                message = AppStrings.plural(R.plurals.tools_grey_done, total, total)
            )
        } finally {
            temp.delete()
        }
    }

    // ---- Flatten --------------------------------------------------------------------------------------------------

    /**
     * Draws the form fields and the visible annotations into the page content and removes them, so nothing can be
     * edited or deleted any more. Links stay (they are not content), popups go with their notes, and an annotation
     * without an appearance of its own is left as it is rather than lost.
     */
    internal fun flattenDocument(doc: PDDocument): FlattenCount {
        var fields = 0
        val form = doc.documentCatalog.acroForm
        if (form != null) {
            fields = runCatching { form.fieldTree.count { it.widgets.isNotEmpty() } }.getOrDefault(0)
            // Flattening draws each field's appearance, so every appearance must be current first.
            runCatching { form.refreshAppearances() }
            form.flatten()
            // No fields are left: drop the form (and any XFA with it), so readers do not offer to fill it.
            if (form.fields.isEmpty()) doc.documentCatalog.acroForm = null
        }
        var annotations = 0
        for (page in doc.pages) {
            val all = runCatching { page.annotations }.getOrNull() ?: continue
            if (all.isEmpty()) continue
            val keep = mutableListOf<PDAnnotation>()
            val draw = mutableListOf<PDAnnotation>()
            for (annotation in all) {
                when {
                    annotation is PDAnnotationLink -> keep += annotation
                    annotation is PDAnnotationPopup -> Unit
                    annotation.isHidden || annotation.isNoView -> Unit
                    annotation.normalAppearanceStream != null && annotation.rectangle != null -> draw += annotation
                    // A widget whose field is gone is an orphan; anything else without a look is kept, not lost.
                    annotation is PDAnnotationWidget && form == null -> keep += annotation
                    annotation is PDAnnotationWidget -> Unit
                    else -> keep += annotation
                }
            }
            if (draw.isNotEmpty()) {
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    for (annotation in draw) {
                        val transform = appearanceTransform(annotation) ?: continue
                        cs.saveGraphicsState()
                        cs.transform(transform)
                        cs.drawForm(annotation.normalAppearanceStream)
                        cs.restoreGraphicsState()
                        annotations++
                    }
                }
            }
            page.annotations = keep
        }
        return FlattenCount(fields, annotations)
    }

    /**
     * The matrix that places an annotation's appearance on its rectangle (PDF 32000-1, 12.5.5): the appearance box,
     * after the appearance's own matrix, is scaled and moved onto the annotation's rectangle. Null for an empty box.
     */
    internal fun appearanceTransform(annotation: PDAnnotation): Matrix? {
        val appearance = annotation.normalAppearanceStream ?: return null
        val rect = annotation.rectangle ?: return null
        val box = appearance.bBox ?: return null
        val m = appearance.matrix
        val corners = listOf(
            box.lowerLeftX to box.lowerLeftY, box.upperRightX to box.lowerLeftY,
            box.lowerLeftX to box.upperRightY, box.upperRightX to box.upperRightY
        ).map { (x, y) -> m.transformPoint(x, y) }
        val minX = corners.minOf { it.x }
        val minY = corners.minOf { it.y }
        val width = corners.maxOf { it.x } - minX
        val height = corners.maxOf { it.y } - minY
        if (width <= 0f || height <= 0f || rect.width <= 0f || rect.height <= 0f) return null
        val sx = rect.width / width
        val sy = rect.height / height
        return Matrix(sx, 0f, 0f, sy, rect.lowerLeftX - minX * sx, rect.lowerLeftY - minY * sy)
    }

    suspend fun flatten(
        context: Context,
        inputUri: Uri,
        outputFile: File,
        onProgress: (current: Int, total: Int, label: String) -> Unit = { _, _, _ -> }
    ): PdfToolResult = withContext(BackgroundWork.tools) {
        val temp = PdfToolsEngine.copyUriToTemp(context, inputUri, "flatten_in_${UUID.randomUUID()}.pdf")
        try {
            PdfToolsEngine.loadPdf(temp).use { doc ->
                onProgress(0, 0, AppStrings.get(R.string.tools_flatten_progress))
                val count = try {
                    flattenDocument(doc)
                } catch (error: Exception) {
                    userError(R.string.tools_flatten_err_failed, cause = error)
                }
                userRequire(count.fields + count.annotations > 0, R.string.tools_flatten_nothing)
                outputFile.parentFile?.mkdirs()
                doc.save(outputFile)
                PdfToolResult(
                    outputFile = outputFile,
                    pageCount = doc.numberOfPages,
                    originalSizeBytes = temp.length(),
                    outputSizeBytes = outputFile.length(),
                    message = AppStrings.get(R.string.tools_flatten_done, count.fields, count.annotations)
                )
            }
        } catch (error: Throwable) {
            outputFile.delete()
            throw error
        } finally {
            temp.delete()
        }
    }

    // ---- Extract images -------------------------------------------------------------------------------------------

    /** Every image drawn on [page], inside forms too, each once (an image reused on many pages is one object). */
    internal fun pageImages(page: PDPage, seen: MutableSet<COSBase>, out: MutableList<PDImageXObject>) {
        collect(page.resources ?: return, seen, IdentityHashMap(), out)
    }

    private fun collect(resources: PDResources, seen: MutableSet<COSBase>, forms: IdentityHashMap<COSBase, Unit>, out: MutableList<PDImageXObject>) {
        for (name in resources.xObjectNames) {
            val xObject = runCatching { resources.getXObject(name) }.getOrNull() ?: continue
            when (xObject) {
                is PDImageXObject -> if (seen.add(xObject.cosObject)) out += xObject
                is PDFormXObject -> {
                    // A form that draws itself would loop forever; each form is looked into once.
                    if (forms.put(xObject.cosObject, Unit) == null) xObject.resources?.let { collect(it, seen, forms, out) }
                }
            }
        }
    }

    /**
     * Saves [image] into [dir] as "<base> image NNN": a JPEG exactly as stored (no quality lost), anything else as
     * PNG. Null when the image is too small to be a picture or is in a format this phone cannot decode.
     */
    internal fun saveImage(image: PDImageXObject, dir: File, base: String, number: Int): File? {
        if (image.width < MIN_IMAGE_SIDE || image.height < MIN_IMAGE_SIDE) return null
        val stem = "$base image ${number.toString().padStart(3, '0')}"
        val filters = image.cosObject.filters
        val onlyJpeg = filters == COSName.DCT_DECODE ||
            (filters is com.tom_roush.pdfbox.cos.COSArray && filters.size() == 1 && filters.getObject(0) == COSName.DCT_DECODE)
        val cmyk = runCatching { image.colorSpace.numberOfComponents == 4 }.getOrDefault(false)
        if (onlyJpeg && !cmyk) {
            val file = File(dir, "$stem.jpg")
            image.cosObject.createRawInputStream().use { input -> FileOutputStream(file).buffered().use { input.copyTo(it) } }
            return file
        }
        val bitmap = runCatching { image.image }.getOrNull() ?: return null
        val file = File(dir, "$stem.png")
        try {
            FileOutputStream(file).buffered().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            bitmap.recycle()
        }
        return file
    }

    suspend fun extractImages(
        context: Context,
        inputUri: Uri,
        outputDir: File,
        onProgress: (current: Int, total: Int, label: String) -> Unit = { _, _, _ -> }
    ): List<File> = withContext(BackgroundWork.tools) {
        outputDir.mkdirs()
        val temp = PdfToolsEngine.copyUriToTemp(context, inputUri, "ximg_in_${UUID.randomUUID()}.pdf")
        val base = PdfToolsEngine.sourceBaseName(context, inputUri)
        try {
            PdfToolsEngine.loadPdf(temp).use { doc ->
                val total = doc.numberOfPages
                val seen = HashSet<COSBase>()
                val files = mutableListOf<File>()
                for (i in 0 until total) {
                    if (!currentCoroutineContext().isActive) throw InterruptedException("Extract images cancelled")
                    onProgress(i + 1, total, AppStrings.get(R.string.tools_new_progress_page, i + 1, total))
                    val images = mutableListOf<PDImageXObject>()
                    runCatching { pageImages(doc.getPage(i), seen, images) }
                    for (image in images) {
                        // One unreadable image must not cost the others.
                        runCatching { saveImage(image, outputDir, base, files.size + 1) }.getOrNull()?.let { files += it }
                    }
                }
                userRequire(files.isNotEmpty(), R.string.tools_ximg_err_none)
                files
            }
        } finally {
            temp.delete()
        }
    }
}
