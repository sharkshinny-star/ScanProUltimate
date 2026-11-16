package com.example.scanpro.feature.phonepdfs

import android.net.Uri

/** The kinds of file "Files on this phone" lists. */
enum class PhoneFileKind { PDF, IMAGE, WORD, EXCEL, POWERPOINT, TEXT }

/** The filter chips above the list; [ALL] shows every kind. */
enum class PhoneFileFilter(val kind: PhoneFileKind?) {
    ALL(null), PDF(PhoneFileKind.PDF), IMAGES(PhoneFileKind.IMAGE), WORD(PhoneFileKind.WORD),
    EXCEL(PhoneFileKind.EXCEL), POWERPOINT(PhoneFileKind.POWERPOINT), TEXT(PhoneFileKind.TEXT)
}

/** A file found in the phone's shared storage (not one of ScanPro's own documents). */
data class PhoneFile(
    val uri: Uri,
    val name: String,
    val kind: PhoneFileKind,
    val mimeType: String,
    /** Size in bytes; 0 when unknown. */
    val size: Long,
    /** Last modified, milliseconds since the epoch; 0 when unknown. */
    val modified: Long,
    /** The folder it sits in, for display only ("Download", "Documents/Bills"); blank when unknown. */
    val folder: String = "",
    /** The file's path when it was found by walking the storage rather than through MediaStore. */
    val path: String? = null
)

enum class PhoneFileSort { NAME, DATE, SIZE }

/** Kinds, sorting, searching and the storage walk's rules for "Files on this phone". Pure, so it is unit tested. */
object PhoneFiles {
    /** Most files a storage walk looks at before it stops, so a huge card can't keep it busy for minutes. */
    const val WALK_FILE_CAP = 20_000

    /** Most documents (PDF, Office, text) listed; newest first, so the cap drops the oldest. */
    const val DOCUMENT_CAP = 5_000

    /** Most gallery images listed, newest first; a phone can hold tens of thousands. */
    const val IMAGE_CAP = 2_000

    /** Deepest folder level a walk goes into, so a link loop or a deep tree can't run forever. */
    const val WALK_MAX_DEPTH = 12

    /** How many of the newest files Home shows before "See all". */
    const val HOME_COUNT = 10

    const val PDF_MIME = "application/pdf"

    /** MIME types asked of MediaStore, by kind. Images are the gallery formats people turn into PDFs. */
    val MIME_TYPES: Map<PhoneFileKind, List<String>> = mapOf(
        PhoneFileKind.PDF to listOf(PDF_MIME),
        PhoneFileKind.IMAGE to listOf("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif"),
        PhoneFileKind.WORD to listOf(
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/rtf"
        ),
        PhoneFileKind.EXCEL to listOf(
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "text/csv",
            "text/comma-separated-values"
        ),
        PhoneFileKind.POWERPOINT to listOf(
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        ),
        PhoneFileKind.TEXT to listOf("text/plain")
    )

    private val EXTENSIONS: Map<String, PhoneFileKind> = mapOf(
        "pdf" to PhoneFileKind.PDF,
        "jpg" to PhoneFileKind.IMAGE, "jpeg" to PhoneFileKind.IMAGE, "png" to PhoneFileKind.IMAGE,
        "webp" to PhoneFileKind.IMAGE, "heic" to PhoneFileKind.IMAGE, "heif" to PhoneFileKind.IMAGE,
        "doc" to PhoneFileKind.WORD, "docx" to PhoneFileKind.WORD, "rtf" to PhoneFileKind.WORD,
        "xls" to PhoneFileKind.EXCEL, "xlsx" to PhoneFileKind.EXCEL, "csv" to PhoneFileKind.EXCEL,
        "ppt" to PhoneFileKind.POWERPOINT, "pptx" to PhoneFileKind.POWERPOINT,
        "txt" to PhoneFileKind.TEXT
    )

    private val EXTENSION_MIME: Map<String, String> = mapOf(
        "pdf" to PDF_MIME, "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "png" to "image/png", "webp" to "image/webp",
        "heic" to "image/heic", "heif" to "image/heif", "doc" to "application/msword",
        "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "rtf" to "application/rtf",
        "xls" to "application/vnd.ms-excel", "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "csv" to "text/csv", "ppt" to "application/vnd.ms-powerpoint",
        "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation", "txt" to "text/plain"
    )

    /** The kind of a file from its MIME type, else from its name's extension; null for anything this view doesn't list. */
    fun kindOf(mimeType: String?, name: String): PhoneFileKind? {
        val mime = mimeType?.lowercase()
        if (mime != null) MIME_TYPES.entries.firstOrNull { (_, types) -> mime in types }?.let { return it.key }
        return EXTENSIONS[name.substringAfterLast('.', "").lowercase()]
    }

    fun mimeForName(name: String): String = EXTENSION_MIME[name.substringAfterLast('.', "").lowercase()] ?: "application/octet-stream"

    /** Newest first for dates and biggest first for sizes; names A to Z. */
    fun defaultAscending(sort: PhoneFileSort) = sort == PhoneFileSort.NAME

    /** Keeps the files of [filter]'s kind whose name or folder contains every word of [query], ignoring case. */
    fun filter(files: List<PhoneFile>, filter: PhoneFileFilter, query: String): List<PhoneFile> {
        val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return files.filter { file ->
            (filter.kind == null || file.kind == filter.kind) &&
                (words.isEmpty() || (file.name + " " + file.folder).lowercase().let { haystack -> words.all { it in haystack } })
        }
    }

    fun sort(files: List<PhoneFile>, sort: PhoneFileSort, ascending: Boolean = defaultAscending(sort)): List<PhoneFile> {
        val byName = compareBy<PhoneFile, String>(String.CASE_INSENSITIVE_ORDER) { it.name }
        val comparator = when (sort) {
            PhoneFileSort.NAME -> byName
            PhoneFileSort.DATE -> compareBy<PhoneFile> { it.modified }.then(byName)
            PhoneFileSort.SIZE -> compareBy<PhoneFile> { it.size }.then(byName)
        }
        return files.sortedWith(if (ascending) comparator else comparator.reversed())
    }

    /** The [count] most recently changed files, for Home. */
    fun newest(files: List<PhoneFile>, count: Int = HOME_COUNT): List<PhoneFile> = sort(files, PhoneFileSort.DATE, ascending = false).take(count)

    /** What a multi-selection can be turned into: Merge needs two PDFs or more, Images to PDF only images. */
    fun canMerge(selection: List<PhoneFile>) = selection.size >= 2 && selection.all { it.kind == PhoneFileKind.PDF }
    fun canMakePdfFromImages(selection: List<PhoneFile>) = selection.isNotEmpty() && selection.all { it.kind == PhoneFileKind.IMAGE }

    /**
     * Whether a storage walk should go into the folder [relativePath] (relative to the storage root, '/' separated).
     * Hidden folders, other apps' private Android/data and Android/obb, and LOST.DIR are skipped.
     */
    fun shouldEnterDir(relativePath: String): Boolean {
        val parts = relativePath.trim('/').split('/').filter { it.isNotEmpty() }
        if (parts.isEmpty()) return true
        if (parts.size > WALK_MAX_DEPTH) return false
        if (parts.any { it.startsWith(".") }) return false
        if (parts.first().equals("Android", ignoreCase = true) && parts.size >= 2 &&
            (parts[1].equals("data", ignoreCase = true) || parts[1].equals("obb", ignoreCase = true))
        ) return false
        if (parts.first().equals("LOST.DIR", ignoreCase = true)) return false
        return true
    }

    /** Whether a walk lists the file [name]: a kind this view shows, and not hidden. */
    fun isListedName(name: String) = !name.startsWith(".") && kindOf(null, name) != null

    /** The parent folder of [relativePath] for display, e.g. "Download/Bills/x.pdf" gives "Download/Bills". */
    fun folderOf(relativePath: String): String = relativePath.trim('/').substringBeforeLast('/', "")
}
