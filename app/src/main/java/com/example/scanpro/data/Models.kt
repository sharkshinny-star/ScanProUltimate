package com.example.scanpro.data

import androidx.annotation.StringRes
import com.example.scanpro.R
import java.io.File

import java.util.UUID

enum class PageFilter(@StringRes val labelRes: Int) {
    ORIGINAL(R.string.data_filter_original),
    AUTO(R.string.data_filter_auto),
    COLOR(R.string.data_filter_color),
    GRAY(R.string.data_filter_gray),
    BW(R.string.data_filter_bw),
    WHITEBOARD(R.string.data_filter_whiteboard)
}

enum class PdfQuality(@StringRes val labelRes: Int, val maxImageDimension: Int) {
    HIGH(R.string.data_pdf_quality_high, 3200),
    BALANCED(R.string.data_pdf_quality_balanced, 2200),
    SMALL(R.string.data_pdf_quality_small, 1400)
}

enum class PdfPageSize(@StringRes val labelRes: Int) {
    AUTO(R.string.data_page_size_auto),
    A4(R.string.data_page_size_a4),
    LETTER(R.string.data_page_size_letter),
    LEGAL(R.string.data_page_size_legal)
}

enum class ThemeMode(@StringRes val labelRes: Int) {
    LIGHT(R.string.data_theme_light),
    DARK(R.string.data_theme_dark),
    SYSTEM(R.string.data_theme_system)
}

enum class ScanMode(@StringRes val labelRes: Int) {
    DOCUMENT(R.string.data_scan_mode_document),
    RECEIPT(R.string.data_scan_mode_receipt),
    WHITEBOARD(R.string.data_scan_mode_whiteboard),
    ID_CARD(R.string.data_scan_mode_id_card),
    BOOK(R.string.data_scan_mode_book),
    TEXT(R.string.data_scan_mode_text)
}

/** The look a new scan starts with in each mode; the review screen's filter strip can change it per page. */
val ScanMode.defaultFilter: PageFilter
    get() = when (this) {
        ScanMode.WHITEBOARD -> PageFilter.WHITEBOARD
        // ID cards keep their real colours: they are often checked against the original.
        ScanMode.ID_CARD -> PageFilter.ORIGINAL
        else -> PageFilter.AUTO
    }

enum class OcrScript(@StringRes val labelRes: Int) {
    LATIN(R.string.data_ocr_script_latin),
    DEVANAGARI(R.string.data_ocr_script_devanagari),
    CHINESE(R.string.data_ocr_script_chinese),
    JAPANESE(R.string.data_ocr_script_japanese),
    KOREAN(R.string.data_ocr_script_korean)
}

enum class AnnotationType {
    TEXT,
    HIGHLIGHT,
    REDACT,
    SIGNATURE,
    /** Brush strokes painted out and filled in from the paper around them (stains, pen marks, handwriting). */
    ERASE
}

data class NormalizedPoint(
    val x: Float,
    val y: Float
) {
    fun clamped() = copy(x = x.coerceIn(0f, 1f), y = y.coerceIn(0f, 1f))
}

data class CropQuad(
    val topLeft: NormalizedPoint = NormalizedPoint(0f, 0f),
    val topRight: NormalizedPoint = NormalizedPoint(1f, 0f),
    val bottomRight: NormalizedPoint = NormalizedPoint(1f, 1f),
    val bottomLeft: NormalizedPoint = NormalizedPoint(0f, 1f),
    /**
     * For a curved page: how far the middle of the top edge bends away from the straight line between the top
     * corners, as a fraction of the image width (x) and height (y). Null for a straight edge.
     */
    val topBow: NormalizedPoint? = null,
    /** The same for the bottom edge. */
    val bottomBow: NormalizedPoint? = null,
    /** The same for the left and right edges, for a page photographed sideways. */
    val leftBow: NormalizedPoint? = null,
    val rightBow: NormalizedPoint? = null
) {
    /** True when any edge is bent enough to be flattened rather than just straightened. */
    val isCurved: Boolean
        get() = listOfNotNull(topBow, bottomBow, leftBow, rightBow).any { kotlin.math.abs(it.x) > 0.002f || kotlin.math.abs(it.y) > 0.002f }

    /** The same outline with every edge straight. */
    fun straightened() = copy(topBow = null, bottomBow = null, leftBow = null, rightBow = null)

    fun isIdentity(epsilon: Float = 0.002f): Boolean {
        val d = DEFAULT
        return !isCurved && kotlin.math.abs(topLeft.x - d.topLeft.x) < epsilon &&
            kotlin.math.abs(topLeft.y - d.topLeft.y) < epsilon &&
            kotlin.math.abs(topRight.x - d.topRight.x) < epsilon &&
            kotlin.math.abs(topRight.y - d.topRight.y) < epsilon &&
            kotlin.math.abs(bottomRight.x - d.bottomRight.x) < epsilon &&
            kotlin.math.abs(bottomRight.y - d.bottomRight.y) < epsilon &&
            kotlin.math.abs(bottomLeft.x - d.bottomLeft.x) < epsilon &&
            kotlin.math.abs(bottomLeft.y - d.bottomLeft.y) < epsilon
    }

    companion object {
        val DEFAULT = CropQuad()
    }
}

data class PageAdjustments(
    /** -1.0 (darker) .. +1.0 (brighter). */
    val brightness: Float = 0f,
    /** 0.6 .. 1.6, with 1.0 neutral. */
    val contrast: Float = 1f,
    /** 0.0 .. 2.0, with 1.0 neutral. */
    val saturation: Float = 1f,
    /** Evens out shadows and uneven lighting on the paper. */
    val removeShadows: Boolean = false,
    /** Paints over fingers holding the page at its edges. */
    val removeFingers: Boolean = false
) {
    fun normalized() = copy(
        brightness = brightness.coerceIn(-1f, 1f),
        contrast = contrast.coerceIn(0.6f, 1.6f),
        saturation = saturation.coerceIn(0f, 2f)
    )

    /** True when brightness, contrast and saturation are untouched (the clean-up switches are separate). */
    fun isTonallyNeutral(epsilon: Float = 0.001f): Boolean =
        kotlin.math.abs(brightness) < epsilon &&
            kotlin.math.abs(contrast - 1f) < epsilon &&
            kotlin.math.abs(saturation - 1f) < epsilon

    fun isNeutral(epsilon: Float = 0.001f): Boolean = isTonallyNeutral(epsilon) && !removeShadows && !removeFingers

    companion object {
        val DEFAULT = PageAdjustments()
    }
}

data class PageAnnotation(
    val id: String = UUID.randomUUID().toString(),
    val type: AnnotationType,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val rotationDegrees: Float = 0f,
    val text: String = "",
    val assetPath: String = "",
    val opacity: Float = 1f,
    /** ARGB colour of a TEXT annotation; the alpha comes from [opacity]. */
    val color: Int = DEFAULT_TEXT_COLOR,
    /**
     * For a TEXT annotation that replaces words on the page: the paper colour painted over the box before the new
     * text is drawn. Null draws the text straight onto the page.
     */
    val background: Int? = null,
    /**
     * For ERASE: the brush strokes, each a list of points given as fractions of this annotation's box, so moving
     * or resizing the box carries them along.
     */
    val strokes: List<List<NormalizedPoint>> = emptyList(),
    /** For ERASE: the brush diameter as a fraction of the page width. */
    val brushSize: Float = 0f
) {
    /** True when this hides what was printed underneath, so the page's text has to be read again. */
    val hidesContent: Boolean
        get() = type == AnnotationType.REDACT || type == AnnotationType.ERASE || (type == AnnotationType.TEXT && background != null)

    companion object {
        const val DEFAULT_TEXT_COLOR: Int = 0xFF141414.toInt()
    }
}

/**
 * The same mark-up after the page turns a quarter clockwise (coordinates are fractions of the page). The box
 * moves with the page, and the content inside it turns too, so text, watermarks and signatures stay aligned
 * with what is printed instead of staying upright inside a box that is now on its side.
 */
fun PageAnnotation.rotatedClockwise(
    /** Width divided by height of the page before the turn, so an eraser brush (a fraction of the width) keeps its size. */
    pageAspect: Float = 1f
) = copy(
    x = 1f - (y + height), y = x, width = height, height = width,
    rotationDegrees = ((rotationDegrees + 90f) % 360f + 360f) % 360f,
    brushSize = brushSize * pageAspect,
    // Eraser strokes are fractions of the box, so they turn with it.
    strokes = strokes.map { stroke -> stroke.map { NormalizedPoint(1f - it.y, it.x) } }
)

enum class WatermarkSize(@StringRes val labelRes: Int, val height: Float) {
    SMALL(R.string.data_watermark_size_small, 0.07f),
    MEDIUM(R.string.data_watermark_size_medium, 0.12f),
    LARGE(R.string.data_watermark_size_large, 0.18f)
}

enum class WatermarkPosition(@StringRes val labelRes: Int) {
    DIAGONAL(R.string.data_watermark_position_diagonal),
    CENTER(R.string.data_watermark_position_center),
    BOTTOM(R.string.data_watermark_position_bottom)
}

/** How "Watermark all pages" stamps its text. */
data class WatermarkStyle(
    val text: String,
    val size: WatermarkSize = WatermarkSize.MEDIUM,
    val position: WatermarkPosition = WatermarkPosition.DIAGONAL,
    val color: Int = PageAnnotation.DEFAULT_TEXT_COLOR,
    val opacity: Float = 0.35f
)

/** A recognised word and where it sits on the rendered page, as fractions of the page width and height. */
data class OcrWord(val text: String, val left: Float, val top: Float, val right: Float, val bottom: Float)

data class ScanPage(
    val id: String,
    val documentId: String,
    val position: Int,
    val sourcePath: String,
    val imagePath: String,
    val ocrText: String = "",
    /** True once the user has corrected [ocrText] by hand; later edits to the page must keep it. */
    val ocrEdited: Boolean = false,
    /** Word positions for [ocrText] on the rendered page; empty when unknown, and stale once [ocrEdited]. */
    val ocrWords: List<OcrWord> = emptyList(),
    val filter: PageFilter = PageFilter.ORIGINAL,
    val rotationDegrees: Int = 0,
    val crop: CropQuad = CropQuad.DEFAULT,
    val adjustments: PageAdjustments = PageAdjustments.DEFAULT,
    val annotations: List<PageAnnotation> = emptyList()
)

data class ScanDocument(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val pdfPath: String,
    val pages: List<ScanPage>,
    val sizeBytes: Long,
    val isFavorite: Boolean = false,
    val tags: List<String> = emptyList(),
    val folderName: String = "",
    val pdfQuality: PdfQuality = PdfQuality.BALANCED,
    val pdfPageSize: PdfPageSize = PdfPageSize.A4,
    val searchablePdf: Boolean = true,
    val pageNumbers: Boolean = false,
    val scanMode: ScanMode = ScanMode.DOCUMENT,
    val ocrScript: OcrScript = OcrScript.LATIN,
    val isTrashed: Boolean = false,
    val trashedAt: Long = 0L
) {
    val pageCount: Int get() = pages.size
    val ocrText: String get() = pages.joinToString("\n\n") { it.ocrText }.trim()
}

/** Marker left beside a document's PDF when page edits have made it out of date. */
val ScanDocument.stalePdfMarker: File get() = File("$pdfPath.stale")

/** True when the PDF exists and reflects the latest page edits. */
fun ScanDocument.hasCurrentPdf(): Boolean = File(pdfPath).isFile && !stalePdfMarker.exists()
