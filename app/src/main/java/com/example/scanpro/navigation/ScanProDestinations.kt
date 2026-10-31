package com.example.scanpro.navigation

/** Route strings for every screen, kept in one place so screens never build routes by hand. */
object ScanProDestinations {
    const val HOME = "home"
    const val FILES = "documents"
    const val TOOLS = "tools"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val CAMERA = "camera"
    /** Camera opened straight into one mode: a [com.example.scanpro.data.ScanMode] name, or [CAMERA_QR]. */
    const val CAMERA_MODE_ARG = "mode"
    /** Camera opened to shoot one page of a saved document again: "documentId/pageId/position". */
    const val CAMERA_RETAKE_ARG = "retake"
    const val CAMERA_ROUTE = "camera?$CAMERA_MODE_ARG={$CAMERA_MODE_ARG}&$CAMERA_RETAKE_ARG={$CAMERA_RETAKE_ARG}"
    const val CAMERA_QR = "QR"
    fun camera(mode: String) = "camera?$CAMERA_MODE_ARG=$mode"
    fun cameraRetake(documentId: String, pageId: String, position: Int) = "camera?$CAMERA_RETAKE_ARG=$documentId/$pageId/$position"

    /** The parts of a [CAMERA_RETAKE_ARG] value, or null when it is empty or malformed. */
    fun parseRetake(value: String?): RetakeTarget? {
        val parts = value.orEmpty().split('/')
        if (parts.size != 3 || parts[0].isBlank() || parts[1].isBlank()) return null
        return RetakeTarget(parts[0], parts[1], parts[2].toIntOrNull() ?: 0)
    }
    const val TRASH = "trash"
    /** Camera scans whose import failed, kept so they can be saved again or discarded. */
    const val KEPT_SCANS = "kept_scans"

    const val DOCUMENT_ID_ARG = "documentId"
    const val DOCUMENT = "detail/{$DOCUMENT_ID_ARG}"
    fun document(id: String) = "detail/$id"

    const val PDF_URI_ARG = "uri"
    const val PDF_TITLE_ARG = "title"
    const val PDF_VIEWER = "pdf_viewer?$PDF_URI_ARG={$PDF_URI_ARG}&$PDF_TITLE_ARG={$PDF_TITLE_ARG}"
    fun pdfViewer(uri: android.net.Uri, title: String) =
        "pdf_viewer?$PDF_URI_ARG=${android.net.Uri.encode(uri.toString())}&$PDF_TITLE_ARG=${android.net.Uri.encode(title)}"

    /** Tabs shown in the bottom bar. Search is opened from the top bar instead of being a tab. */
    val topLevel = listOf(HOME, FILES, TOOLS, SETTINGS)
}

/** A saved page the camera is about to replace. */
data class RetakeTarget(val documentId: String, val pageId: String, val position: Int)
