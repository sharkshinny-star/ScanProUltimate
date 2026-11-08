package com.example.scanpro.data

import android.graphics.Bitmap
import com.example.scanpro.R
import com.example.scanpro.core.text.userError
import com.example.scanpro.core.text.userRequire
import com.example.scanpro.imaging.AnnotationRemap
import com.example.scanpro.imaging.ImageProcessor
import com.example.scanpro.imaging.PaperColors
import java.io.File
import java.util.UUID
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Edits to one page or its look: filters, crop, markup, text, signature, watermark, OCR and Undo/Redo (R9). */
internal class PageEditor(private val store: DocumentStore) {
    private val context get() = store.context
    private val db get() = store.db
    private val ocr get() = store.ocr
    private val history get() = store.history
    private fun requireDocument(id: String) = store.requireDocument(id)
    private fun saveWithStalePdf(document: ScanDocument) = store.saveWithStalePdf(document)
    private suspend fun <T> allOrNothing(block: suspend (touched: MutableList<ScanPage>) -> T): T = store.allOrNothing(block)
    private suspend fun renderAndOcr(page: ScanPage, runOcr: Boolean = true, redactionChanged: Boolean = false, script: OcrScript? = null) =
        store.renderAndOcr(page, runOcr, redactionChanged, script)
    private fun now() = System.currentTimeMillis()

    suspend fun applyFilter(documentId: String, pageId: String, filter: PageFilter) =
        editSinglePage(documentId, pageId) { page ->
            val updated = page.copy(filter = filter)
            renderAndOcr(updated)
        }

    suspend fun rotatePage(documentId: String, pageId: String) =
        editSinglePage(documentId, pageId) { page ->
            // Markup is placed on the finished page, so it turns with it; otherwise a redaction would slide off its text.
            // The eraser brush is a fraction of the page width, which changes with the turn.
            val aspect = ImageProcessor.imageAspect(page.imagePath) ?: 1f
            val updated = page.copy(
                rotationDegrees = (page.rotationDegrees + 90) % 360,
                annotations = page.annotations.map { it.rotatedClockwise(pageAspect = aspect) }
            )
            renderAndOcr(updated)
        }

    suspend fun applyCrop(documentId: String, pageId: String, crop: CropQuad) =
        editSinglePage(documentId, pageId) { page ->
            // Mark-up is placed on the cropped page, so move it with its content; otherwise a redaction would
            // slide off the text it hides when the crop changes.
            val annotations = ImageProcessor.orientedAspect(page.sourcePath)?.let { aspect ->
                AnnotationRemap.remap(page.annotations, page.crop, crop, page.rotationDegrees, aspect)
            } ?: page.annotations
            renderAndOcr(page.copy(crop = crop, annotations = annotations))
        }

    suspend fun applyAdjustments(documentId: String, pageId: String, adjustments: PageAdjustments) =
        editSinglePage(documentId, pageId) { page ->
            renderAndOcr(page.copy(adjustments = adjustments.normalized()))
        }

    /** [adjustments] on [pageId]; every other page gets only its shadow and finger clean-up switches. */
    suspend fun applyAdjustmentsToAll(documentId: String, pageId: String, adjustments: PageAdjustments) = withContext(Dispatchers.IO) {
        val doc = requireDocument(documentId)
        val clean = adjustments.normalized()
        allOrNothing { touched ->
            val pages = doc.pages.map { page ->
                val updated = if (page.id == pageId) clean
                else page.adjustments.copy(removeShadows = clean.removeShadows, removeFingers = clean.removeFingers)
                if (updated == page.adjustments) page else {
                    touched += page
                    renderAndOcr(page.copy(adjustments = updated))
                }
            }
            saveWithStalePdf(doc.copy(pages = pages, updatedAt = now()))
        }.also { history.record(doc.id, doc.pages) }
    }

    suspend fun resetPage(documentId: String, pageId: String) {
        val removed = annotationsOf(documentId, pageId)
        editSinglePage(documentId, pageId) { page ->
            renderAndOcr(
                page.copy(
                    filter = PageFilter.ORIGINAL,
                    rotationDegrees = 0,
                    crop = CropQuad.DEFAULT,
                    adjustments = PageAdjustments.DEFAULT,
                    annotations = emptyList()
                ),
                redactionChanged = removed.any { it.hidesContent }
            )
        }
        // Signature images stay until the document is closed, so Undo can bring the signature back.
    }

    private suspend fun annotationsOf(documentId: String, pageId: String): List<PageAnnotation> =
        withContext(Dispatchers.IO) { requireDocument(documentId).pages.firstOrNull { it.id == pageId }?.annotations.orEmpty() }

    /** The page as it looks without its markup, so the markup editor can show boxes over a clean image. */
    suspend fun markupBase(documentId: String, pageId: String): File = withContext(Dispatchers.IO) {
        val page = requireDocument(documentId).pages.firstOrNull { it.id == pageId } ?: userError(R.string.err_page_not_found)
        val out = File(context.cacheDir, "markup_${page.id}.jpg")
        ImageProcessor.renderPage(File(page.sourcePath), out, page.filter, page.rotationDegrees, page.crop, page.adjustments, emptyList())
        out
    }

    /** Replaces a page's markup with an edited list (moved, resized or removed boxes). */
    suspend fun replaceAnnotations(documentId: String, pageId: String, annotations: List<PageAnnotation>) {
        val before = annotationsOf(documentId, pageId)
        editSinglePage(documentId, pageId) { page ->
            // Only a redaction that was added, moved, resized or removed changes which text is visible.
            val changed = redactions(before) != redactions(annotations)
            renderAndOcr(page.copy(annotations = annotations), runOcr = changed, redactionChanged = changed)
        }
    }

    suspend fun addTextAnnotation(documentId: String, pageId: String, text: String) =
        addAnnotation(
            documentId,
            pageId,
            PageAnnotation(
                type = AnnotationType.TEXT,
                x = 0.08f,
                y = 0.08f,
                width = 0.58f,
                height = 0.12f,
                text = text.trim().take(400)
            )
        )

    suspend fun addHighlight(documentId: String, pageId: String) =
        addAnnotation(
            documentId,
            pageId,
            PageAnnotation(
                type = AnnotationType.HIGHLIGHT,
                x = 0.12f,
                y = 0.40f,
                width = 0.70f,
                height = 0.055f,
                opacity = 0.55f
            )
        )

    suspend fun addRedaction(documentId: String, pageId: String) =
        addAnnotation(
            documentId,
            pageId,
            PageAnnotation(
                type = AnnotationType.REDACT,
                x = 0.12f,
                y = 0.48f,
                width = 0.70f,
                height = 0.07f
            )
        )

    suspend fun removeLastAnnotation(documentId: String, pageId: String) {
        val removed = annotationsOf(documentId, pageId).takeLast(1)
        val redaction = removed.any { it.hidesContent }
        editSinglePage(documentId, pageId) { page ->
            // Removing a highlight, text or signature leaves the page's words as they were; a redaction uncovers some.
            renderAndOcr(page.copy(annotations = page.annotations.dropLast(1)), runOcr = redaction, redactionChanged = redaction)
        }
    }

    /**
     * Replaces a line of printed words: the box ([left]..[bottom], fractions of the page) is painted in the paper
     * colour sampled around it and [text] is written there in the ink colour of the original words. Blank text
     * just removes the words.
     */
    suspend fun replaceText(documentId: String, pageId: String, left: Float, top: Float, right: Float, bottom: Float, text: String) {
        val page = requireDocument(documentId).pages.firstOrNull { it.id == pageId } ?: userError(R.string.err_page_not_found)
        val colours = withContext(Dispatchers.IO) { sampleColours(File(page.imagePath), left, top, right, bottom) }
        val padX = (bottom - top) * 0.15f
        addAnnotation(
            documentId,
            pageId,
            PageAnnotation(
                type = AnnotationType.TEXT,
                x = (left - padX).coerceIn(0f, 1f),
                y = top.coerceIn(0f, 1f),
                width = (right - left + 2 * padX).coerceIn(0.01f, 1f),
                height = (bottom - top).coerceIn(0.005f, 1f),
                text = text.trim().take(400),
                color = colours.ink,
                background = colours.paper
            )
        )
    }

    /** Paper and ink colours of a box on the rendered page, read from a reduced copy of it. */
    private fun sampleColours(image: File, left: Float, top: Float, right: Float, bottom: Float): PaperColors.Estimate {
        val bitmap = ImageProcessor.decodeOrientedScaled(image.absolutePath, 1600) ?: return PaperColors.estimate(IntArray(0))
        try {
            val margin = (bottom - top) * 0.2f
            val x0 = ((left - margin) * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
            val y0 = ((top - margin) * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
            val x1 = ((right + margin) * bitmap.width).toInt().coerceIn(x0 + 1, bitmap.width)
            val y1 = ((bottom + margin) * bitmap.height).toInt().coerceIn(y0 + 1, bitmap.height)
            val pixels = IntArray((x1 - x0) * (y1 - y0))
            bitmap.getPixels(pixels, 0, x1 - x0, x0, y0, x1 - x0, y1 - y0)
            return PaperColors.estimate(pixels)
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * Erases brush strokes (points as fractions of the page), grouped by brush size (a fraction of the page
     * width): the marks under them are filled in from the surrounding paper. One undo step for all of them.
     */
    suspend fun erase(documentId: String, pageId: String, strokesByBrush: Map<Float, List<List<NormalizedPoint>>>) {
        val erasers = strokesByBrush.entries.sortedBy { it.key }.mapNotNull { (size, strokes) -> eraser(strokes.filter { it.isNotEmpty() }, size) }
        userRequire(erasers.isNotEmpty(), R.string.err_erase_nothing_selected)
        editSinglePage(documentId, pageId) { page ->
            renderAndOcr(page.copy(annotations = page.annotations + erasers), runOcr = true, redactionChanged = true)
        }
    }

    private fun eraser(strokes: List<List<NormalizedPoint>>, brushSize: Float): PageAnnotation? {
        val points = strokes.flatten()
        if (points.isEmpty()) return null
        val brush = brushSize.coerceIn(0.005f, 0.2f)
        val left = (points.minOf { it.x } - brush).coerceIn(0f, 1f)
        val top = (points.minOf { it.y } - brush).coerceIn(0f, 1f)
        val width = ((points.maxOf { it.x } + brush).coerceIn(0f, 1f) - left).coerceAtLeast(0.001f)
        val height = ((points.maxOf { it.y } + brush).coerceIn(0f, 1f) - top).coerceAtLeast(0.001f)
        return PageAnnotation(
            type = AnnotationType.ERASE,
            x = left,
            y = top,
            width = width,
            height = height,
            // Stored relative to the box, so moving or resizing it in the markup editor carries the strokes.
            strokes = strokes.map { stroke -> stroke.map { NormalizedPoint((it.x - left) / width, (it.y - top) / height).clamped() } },
            brushSize = brush
        )
    }

    /** Stamps [signature] on the page, fitted into [placement] (fractions of the page) or, without one, the lower right. */
    suspend fun stampSignature(documentId: String, pageId: String, signature: Bitmap, placement: com.example.scanpro.feature.document.SignaturePlacement? = null) =
        editSinglePage(documentId, pageId) { page ->
            val folder = File(page.imagePath).parentFile?.parentFile ?: error("Invalid page folder")
            val signatureDir = File(folder, "signatures").apply { mkdirs() }
            val asset = File(signatureDir, "sig_${UUID.randomUUID()}.png")
            ImageProcessor.saveSignature(signature, asset)
            val annotation = PageAnnotation(
                type = AnnotationType.SIGNATURE,
                x = placement?.x?.coerceIn(0f, 0.99f) ?: 0.56f,
                y = placement?.y?.coerceIn(0f, 0.99f) ?: 0.78f,
                width = placement?.width?.coerceIn(0.01f, 1f) ?: 0.34f,
                height = placement?.height?.coerceIn(0.01f, 1f) ?: 0.12f,
                assetPath = asset.absolutePath
            )
            try {
                renderAndOcr(page.copy(annotations = page.annotations + annotation))
            } catch (error: Throwable) {
                asset.delete()
                throw error
            }
        }

    suspend fun addWatermark(documentId: String, style: WatermarkStyle) = withContext(Dispatchers.IO) {
        val clean = style.text.trim()
        userRequire(clean.isNotBlank(), R.string.err_watermark_blank)
        val doc = requireDocument(documentId)
        val height = style.size.height
        val (y, rotation) = when (style.position) {
            WatermarkPosition.DIAGONAL -> (0.5f - height / 2f) to -28f
            WatermarkPosition.CENTER -> (0.5f - height / 2f) to 0f
            WatermarkPosition.BOTTOM -> (0.95f - height) to 0f
        }
        allOrNothing { touched ->
            val pages = doc.pages.map { page ->
                touched += page
                val annotation = PageAnnotation(
                    type = AnnotationType.TEXT,
                    x = 0.12f,
                    y = y,
                    width = 0.76f,
                    height = height,
                    rotationDegrees = rotation,
                    text = clean.take(80),
                    opacity = style.opacity.coerceIn(0.1f, 1f),
                    color = style.color
                )
                // The watermark is drawn over the page; the text underneath stays readable, so OCR is kept.
                renderAndOcr(page.copy(annotations = page.annotations + annotation), runOcr = false)
            }
            saveWithStalePdf(doc.copy(pages = pages, updatedAt = now()))
        }.also { history.record(doc.id, doc.pages) }
    }

    suspend fun updatePageOcrText(documentId: String, pageId: String, text: String) = withContext(Dispatchers.IO) {
        val doc = requireDocument(documentId)
        // Marked as hand-corrected so filters, crops and rotations afterwards do not read the page again and replace it.
        val pages = doc.pages.map { page -> if (page.id == pageId) page.copy(ocrText = text.trim(), ocrEdited = true) else page }
        userRequire(pages.any { it.id == pageId }, R.string.err_page_not_found)
        saveWithStalePdf(doc.copy(pages = pages, updatedAt = now())).also { history.record(doc.id, doc.pages) }
    }

    /** Reads every page again. Returns how many pages could not be read (they keep their old text). */
    suspend fun reOcrDocument(id: String, script: OcrScript? = null): Int = withContext(Dispatchers.IO) {
        val doc = requireDocument(id)
        val selected = script ?: doc.ocrScript
        // Fails with a clear message when the script's model can't be fetched, instead of silently blanking every page.
        ocr.prepare(selected)
        var failed = 0
        val pages = doc.pages.map { page ->
            coroutineContext.ensureActive()
            // Re-running recognition is an explicit request to read the pages again, so it replaces corrections.
            runCatching { ocr.recognizeDetailed(File(page.imagePath), selected) }
                .map { page.copy(ocrText = it.text, ocrWords = it.words, ocrEdited = false) }
                .getOrElse { failed++; page }
        }
        saveWithStalePdf(doc.copy(pages = pages, ocrScript = selected, updatedAt = now())).also { history.record(doc.id, doc.pages) }
        failed
    }

    private suspend fun addAnnotation(documentId: String, pageId: String, annotation: PageAnnotation) =
        editSinglePage(documentId, pageId) { page ->
            // Replaced words may be blank (the words are just removed); added text may not.
            userRequire(annotation.type != AnnotationType.TEXT || annotation.text.isNotBlank() || annotation.background != null, R.string.err_text_blank)
            // A redaction re-reads the flattened page: the text it covers must not stay in search, copy or text export.
            val redacting = annotation.hidesContent
            renderAndOcr(page.copy(annotations = page.annotations + annotation), runOcr = redacting, redactionChanged = redacting)
        }

    /** Goes back one edit; false when there is nothing to undo. */
    suspend fun undo(documentId: String): Boolean = step(documentId, back = true)

    /** Re-applies an undone edit; false when there is nothing to redo. */
    suspend fun redo(documentId: String): Boolean = step(documentId, back = false)

    private suspend fun step(documentId: String, back: Boolean): Boolean = withContext(Dispatchers.IO) {
        val doc = requireDocument(documentId)
        val target = (if (back) history.undo(documentId, doc.pages) else history.redo(documentId, doc.pages))
            ?: return@withContext false
        val current = doc.pages.associateBy { it.id }
        if (target.size != doc.pages.size || target.any { it.id !in current }) {
            // Pages were added or removed outside the history; its steps no longer fit this document.
            history.clear(documentId)
            userError(R.string.err_change_cannot_be_undone)
        }
        try {
            // Only pages whose look differs are drawn again; text, corrections and order come back as they were.
            allOrNothing { touched ->
                val restored = target.map { page ->
                    val now = current.getValue(page.id)
                    if (!sameRendering(now, page)) {
                        touched += now
                        ImageProcessor.renderPage(
                            File(page.sourcePath), File(page.imagePath), page.filter, page.rotationDegrees,
                            page.crop, page.adjustments, page.annotations
                        )
                    }
                    page
                }
                saveWithStalePdf(doc.copy(pages = restored, updatedAt = now()))
            }
            true
        } catch (error: Throwable) {
            history.rollback(documentId, back, target)
            throw error
        }
    }

    private fun sameRendering(a: ScanPage, b: ScanPage) =
        a.sourcePath == b.sourcePath && a.filter == b.filter && a.rotationDegrees == b.rotationDegrees &&
            a.crop == b.crop && a.adjustments == b.adjustments && a.annotations == b.annotations

    /**
     * Deletes signature images that neither the document nor any undo step uses. Removing a signature keeps its
     * image so Undo can bring it back; this runs when the document is closed.
     */
    suspend fun cleanUnusedSignatures(documentId: String) = withContext(Dispatchers.IO) {
        val doc = db.loadDocument(documentId) ?: return@withContext
        val folder = File(doc.pdfPath).parentFile ?: return@withContext
        val inUse = (doc.pages + history.retained(documentId))
            .flatMap { it.annotations }
            .mapNotNull { it.assetPath.takeIf(String::isNotBlank)?.let { path -> File(path).canonicalPath } }
            .toSet()
        File(folder, "signatures").listFiles()?.filter { it.isFile && it.canonicalPath !in inUse }?.forEach { it.delete() }
    }

    private suspend fun editSinglePage(
        documentId: String,
        pageId: String,
        block: suspend (ScanPage) -> ScanPage
    ) = withContext(Dispatchers.IO) {
        val doc = requireDocument(documentId)
        val target = doc.pages.firstOrNull { it.id == pageId } ?: userError(R.string.err_page_not_found)
        allOrNothing { touched ->
            touched += target
            val updatedPage = block(target)
            val pages = doc.pages.map { if (it.id == pageId) updatedPage else it }
            saveWithStalePdf(doc.copy(pages = pages, updatedAt = now()))
        }.also { history.record(doc.id, doc.pages) }
    }

    private fun redactions(list: List<PageAnnotation>) =
        list.filter { it.hidesContent }.map { listOf(it.id, it.x, it.y, it.width, it.height, it.rotationDegrees) }
}
