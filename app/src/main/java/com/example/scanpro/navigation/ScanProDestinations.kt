package com.example.scanpro.navigation

/** Route strings for every screen, kept in one place so screens never build routes by hand. */
object ScanProDestinations {
    const val HOME = "home"
    const val FILES = "documents"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val CAMERA = "camera"
    const val TRASH = "trash"

    const val DOCUMENT_ID_ARG = "documentId"
    const val DOCUMENT = "detail/{$DOCUMENT_ID_ARG}"
    fun document(id: String) = "detail/$id"

    /** Tabs shown in the bottom bar. Search is opened from the top bar instead of being a tab. */
    val topLevel = listOf(HOME, FILES, SETTINGS)
}
