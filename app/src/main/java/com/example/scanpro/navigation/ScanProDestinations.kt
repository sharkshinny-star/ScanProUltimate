package com.example.scanpro.navigation

/** Route strings for every screen, kept in one place so screens never build routes by hand. */
object ScanProDestinations {
    const val HOME = "home"
    const val FILES = "documents"
    const val TOOLS = "tools"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val CAMERA = "camera"
    const val TRASH = "trash"

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
