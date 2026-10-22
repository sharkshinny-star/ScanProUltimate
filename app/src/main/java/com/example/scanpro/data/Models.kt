package com.example.scanpro.data

import java.io.File

import java.util.UUID

enum class PageFilter(val label: String) {
    ORIGINAL("Original"),
    AUTO("Auto Clean"),
    COLOR("Magic Color"),
    GRAY("Gray"),
    BW("B&W"),
    WHITEBOARD("Whiteboard")
}

enum class PdfQuality(val label: String, val maxImageDimension: Int) {
    HIGH("High", 3200),
    BALANCED("Balanced", 2200),
    SMALL("Small", 1400)
}

enum class PdfPageSize(val label: String) {
    AUTO("Auto"),
    A4("A4"),
    LETTER("Letter")
}

enum class ThemeMode(val label: String) {
    LIGHT("Light"),
    DARK("Dark"),
    SYSTEM("System default")
}

enum class ScanMode(val label: String) {
    DOCUMENT("Document"),
    RECEIPT("Receipt"),
    WHITEBOARD("Whiteboard"),
    ID_CARD("ID card"),
    BOOK("Book"),
    TEXT("Extract text")
}

enum class OcrScript(val label: String) {
    LATIN("Latin"),
    DEVANAGARI("Devanagari"),
    CHINESE("Chinese"),
    JAPANESE("Japanese"),
    KOREAN("Korean")
}

enum class AnnotationType {
    TEXT,
    HIGHLIGHT,
    REDACT,
    SIGNATURE
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
    val bottomLeft: NormalizedPoint = NormalizedPoint(0f, 1f)
) {
    fun isIdentity(epsilon: Float = 0.002f): Boolean {
        val d = DEFAULT
        return kotlin.math.abs(topLeft.x - d.topLeft.x) < epsilon &&
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
    val saturation: Float = 1f
) {
    fun normalized() = PageAdjustments(
        brightness = brightness.coerceIn(-1f, 1f),
        contrast = contrast.coerceIn(0.6f, 1.6f),
        saturation = saturation.coerceIn(0f, 2f)
    )

    fun isNeutral(epsilon: Float = 0.001f): Boolean =
        kotlin.math.abs(brightness) < epsilon &&
            kotlin.math.abs(contrast - 1f) < epsilon &&
            kotlin.math.abs(saturation - 1f) < epsilon

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
    val opacity: Float = 1f
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
