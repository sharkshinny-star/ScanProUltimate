package com.example.scanpro.imaging.metadata

/** The broad kind of a file: decides which reader looks into it and what can be changed. */
enum class FileFamily { PDF, IMAGE, RAW_IMAGE, VECTOR_IMAGE, AUDIO, VIDEO, OFFICE, OFFICE_LEGACY, OPEN_DOCUMENT, EBOOK, ARCHIVE, TEXT, OTHER }

/** What a file is: its [family], a short format name ("JPEG", "Word (DOCX)") and its usual extension and type. */
data class DetectedFormat(val family: FileFamily, val name: String, val extension: String, val mime: String)

/**
 * Tells what a file is from its first bytes (its signature), and only then from its name and the type its app gave:
 * a renamed or mislabelled file is still recognised. Covers more than a hundred formats. Pure Kotlin, unit tested.
 */
object FileFormat {
    /** How many bytes from the start [detect] wants: enough for the ZIP "mimetype" entry and the TAR header. */
    const val HEAD_BYTES = 512

    private class Known(val family: FileFamily, val name: String, val mime: String)

    private val BY_EXTENSION: Map<String, Known> = buildMap {
        fun add(family: FileFamily, name: String, mime: String, vararg ext: String) = ext.forEach { put(it, Known(family, name, mime)) }
        add(FileFamily.PDF, "PDF", "application/pdf", "pdf")
        // Pictures
        add(FileFamily.IMAGE, "JPEG", "image/jpeg", "jpg", "jpeg", "jpe", "jfif")
        add(FileFamily.IMAGE, "PNG", "image/png", "png")
        add(FileFamily.IMAGE, "WebP", "image/webp", "webp")
        add(FileFamily.IMAGE, "HEIC", "image/heic", "heic", "heif", "hif")
        add(FileFamily.IMAGE, "AVIF", "image/avif", "avif")
        add(FileFamily.IMAGE, "GIF", "image/gif", "gif")
        add(FileFamily.IMAGE, "TIFF", "image/tiff", "tif", "tiff")
        add(FileFamily.IMAGE, "BMP", "image/bmp", "bmp", "dib")
        add(FileFamily.IMAGE, "Icon", "image/x-icon", "ico", "cur")
        add(FileFamily.IMAGE, "Photoshop (PSD)", "image/vnd.adobe.photoshop", "psd")
        add(FileFamily.IMAGE, "JPEG 2000", "image/jp2", "jp2", "j2k", "jpf", "jpx")
        add(FileFamily.IMAGE, "JPEG XL", "image/jxl", "jxl")
        add(FileFamily.IMAGE, "TGA", "image/x-tga", "tga")
        add(FileFamily.IMAGE, "PCX", "image/x-pcx", "pcx")
        add(FileFamily.VECTOR_IMAGE, "SVG", "image/svg+xml", "svg")
        add(FileFamily.VECTOR_IMAGE, "EPS", "application/postscript", "eps", "ai")
        add(FileFamily.RAW_IMAGE, "Camera RAW", "image/x-raw", "raw", "cr2", "cr3", "crw", "nef", "nrw", "arw", "srf", "sr2", "orf",
            "rw2", "raf", "pef", "srw", "x3f", "3fr", "erf", "kdc", "mrw", "mos", "iiq", "rwl")
        add(FileFamily.RAW_IMAGE, "DNG", "image/x-adobe-dng", "dng")
        // Sound
        add(FileFamily.AUDIO, "MP3", "audio/mpeg", "mp3")
        add(FileFamily.AUDIO, "WAV", "audio/wav", "wav")
        add(FileFamily.AUDIO, "FLAC", "audio/flac", "flac")
        add(FileFamily.AUDIO, "AAC", "audio/aac", "aac")
        add(FileFamily.AUDIO, "M4A", "audio/mp4", "m4a", "m4b", "m4p")
        add(FileFamily.AUDIO, "Ogg", "audio/ogg", "ogg", "oga")
        add(FileFamily.AUDIO, "Opus", "audio/opus", "opus")
        add(FileFamily.AUDIO, "WMA", "audio/x-ms-wma", "wma")
        add(FileFamily.AUDIO, "AIFF", "audio/aiff", "aif", "aiff", "aifc")
        add(FileFamily.AUDIO, "AMR", "audio/amr", "amr")
        add(FileFamily.AUDIO, "MIDI", "audio/midi", "mid", "midi")
        add(FileFamily.AUDIO, "DSD", "audio/dsf", "dsf", "dff")
        add(FileFamily.AUDIO, "APE", "audio/ape", "ape")
        // Video
        add(FileFamily.VIDEO, "MP4", "video/mp4", "mp4", "m4v")
        add(FileFamily.VIDEO, "QuickTime (MOV)", "video/quicktime", "mov", "qt")
        add(FileFamily.VIDEO, "AVI", "video/x-msvideo", "avi")
        add(FileFamily.VIDEO, "Matroska (MKV)", "video/x-matroska", "mkv", "mka", "mk3d")
        add(FileFamily.VIDEO, "WebM", "video/webm", "webm")
        add(FileFamily.VIDEO, "3GP", "video/3gpp", "3gp", "3g2")
        add(FileFamily.VIDEO, "WMV", "video/x-ms-wmv", "wmv", "asf")
        add(FileFamily.VIDEO, "FLV", "video/x-flv", "flv")
        add(FileFamily.VIDEO, "MPEG", "video/mpeg", "mpg", "mpeg", "m2v")
        add(FileFamily.VIDEO, "MPEG-TS", "video/mp2t", "ts", "mts", "m2ts")
        // Office
        add(FileFamily.OFFICE, "Word (DOCX)", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx", "docm", "dotx")
        add(FileFamily.OFFICE, "Excel (XLSX)", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx", "xlsm", "xltx")
        add(FileFamily.OFFICE, "PowerPoint (PPTX)", "application/vnd.openxmlformats-officedocument.presentationml.presentation", "pptx", "pptm", "ppsx", "potx")
        add(FileFamily.OFFICE, "Visio (VSDX)", "application/vnd.ms-visio.drawing", "vsdx")
        add(FileFamily.OFFICE_LEGACY, "Word 97-2003 (DOC)", "application/msword", "doc", "dot")
        add(FileFamily.OFFICE_LEGACY, "Excel 97-2003 (XLS)", "application/vnd.ms-excel", "xls", "xlt")
        add(FileFamily.OFFICE_LEGACY, "PowerPoint 97-2003 (PPT)", "application/vnd.ms-powerpoint", "ppt", "pps", "pot")
        add(FileFamily.OFFICE_LEGACY, "Outlook message (MSG)", "application/vnd.ms-outlook", "msg")
        add(FileFamily.OPEN_DOCUMENT, "OpenDocument text (ODT)", "application/vnd.oasis.opendocument.text", "odt")
        add(FileFamily.OPEN_DOCUMENT, "OpenDocument sheet (ODS)", "application/vnd.oasis.opendocument.spreadsheet", "ods")
        add(FileFamily.OPEN_DOCUMENT, "OpenDocument slides (ODP)", "application/vnd.oasis.opendocument.presentation", "odp")
        add(FileFamily.OPEN_DOCUMENT, "OpenDocument drawing (ODG)", "application/vnd.oasis.opendocument.graphics", "odg")
        add(FileFamily.EBOOK, "EPUB", "application/epub+zip", "epub")
        add(FileFamily.EBOOK, "Mobipocket", "application/x-mobipocket-ebook", "mobi", "azw", "azw3")
        add(FileFamily.EBOOK, "FictionBook", "application/x-fictionbook+xml", "fb2")
        add(FileFamily.EBOOK, "DjVu", "image/vnd.djvu", "djvu", "djv")
        // Archives
        add(FileFamily.ARCHIVE, "ZIP", "application/zip", "zip")
        add(FileFamily.ARCHIVE, "RAR", "application/vnd.rar", "rar")
        add(FileFamily.ARCHIVE, "7-Zip", "application/x-7z-compressed", "7z")
        add(FileFamily.ARCHIVE, "TAR", "application/x-tar", "tar")
        add(FileFamily.ARCHIVE, "GZIP", "application/gzip", "gz", "tgz")
        add(FileFamily.ARCHIVE, "BZIP2", "application/x-bzip2", "bz2", "tbz2")
        add(FileFamily.ARCHIVE, "XZ", "application/x-xz", "xz", "txz")
        add(FileFamily.ARCHIVE, "Android app (APK)", "application/vnd.android.package-archive", "apk", "aab")
        add(FileFamily.ARCHIVE, "Java archive (JAR)", "application/java-archive", "jar")
        add(FileFamily.ARCHIVE, "Disc image (ISO)", "application/x-iso9660-image", "iso")
        // Text
        add(FileFamily.TEXT, "Text", "text/plain", "txt", "text", "log")
        add(FileFamily.TEXT, "CSV", "text/csv", "csv")
        add(FileFamily.TEXT, "TSV", "text/tab-separated-values", "tsv")
        add(FileFamily.TEXT, "Rich text (RTF)", "application/rtf", "rtf")
        add(FileFamily.TEXT, "Markdown", "text/markdown", "md", "markdown")
        add(FileFamily.TEXT, "JSON", "application/json", "json")
        add(FileFamily.TEXT, "XML", "application/xml", "xml")
        add(FileFamily.TEXT, "HTML", "text/html", "html", "htm", "xhtml")
        add(FileFamily.TEXT, "YAML", "application/yaml", "yaml", "yml")
        add(FileFamily.TEXT, "Contact card (vCard)", "text/vcard", "vcf")
        add(FileFamily.TEXT, "Calendar (ICS)", "text/calendar", "ics")
        add(FileFamily.TEXT, "Subtitles (SRT)", "application/x-subrip", "srt", "vtt")
        add(FileFamily.TEXT, "Source code", "text/plain", "kt", "java", "py", "js", "ts", "c", "cpp", "h", "cs", "go", "rs",
            "swift", "php", "rb", "sh", "sql", "css", "ini", "conf", "properties", "gradle")
    }

    /** How many extensions are recognised (for the screen's "more than 100 formats"). */
    val knownExtensionCount: Int get() = BY_EXTENSION.size

    private fun known(ext: String, fallback: Known): DetectedFormat {
        val k = BY_EXTENSION[ext] ?: fallback
        return DetectedFormat(k.family, k.name, ext.ifBlank { "" }, k.mime)
    }

    private fun of(family: FileFamily, name: String, ext: String, mime: String) = DetectedFormat(family, name, ext, mime)

    /** What [head] (the first bytes of the file) says it is; [fileName] and [mimeType] settle what bytes cannot. */
    fun detect(head: ByteArray, fileName: String?, mimeType: String?): DetectedFormat {
        val ext = fileName?.substringAfterLast('.', "")?.lowercase().orEmpty()
        fun at(offset: Int, vararg bytes: Int) = head.size >= offset + bytes.size && bytes.indices.all { (head[offset + it].toInt() and 0xFF) == bytes[it] }
        fun ascii(offset: Int, text: String) = at(offset, *text.map { it.code }.toIntArray())
        fun inExt(vararg options: String) = ext in options

        return when {
            ascii(0, "%PDF-") -> known("pdf", Known(FileFamily.PDF, "PDF", "application/pdf")).copy(extension = "pdf")
            at(0, 0xFF, 0xD8, 0xFF) -> of(FileFamily.IMAGE, "JPEG", if (inExt("jpeg", "jpe", "jfif")) ext else "jpg", "image/jpeg")
            at(0, 0x89, 0x50, 0x4E, 0x47) -> of(FileFamily.IMAGE, "PNG", "png", "image/png")
            ascii(0, "GIF8") -> of(FileFamily.IMAGE, "GIF", "gif", "image/gif")
            ascii(0, "RIFF") && ascii(8, "WEBP") -> of(FileFamily.IMAGE, "WebP", "webp", "image/webp")
            ascii(0, "RIFF") && ascii(8, "WAVE") -> of(FileFamily.AUDIO, "WAV", "wav", "audio/wav")
            ascii(0, "RIFF") && ascii(8, "AVI ") -> of(FileFamily.VIDEO, "AVI", "avi", "video/x-msvideo")
            ascii(0, "FORM") && (ascii(8, "AIFF") || ascii(8, "AIFC")) -> of(FileFamily.AUDIO, "AIFF", "aiff", "audio/aiff")
            ascii(0, "8BPS") -> of(FileFamily.IMAGE, "Photoshop (PSD)", "psd", "image/vnd.adobe.photoshop")
            ascii(0, "BM") && head.size > 14 && inExt("bmp", "dib", "") -> of(FileFamily.IMAGE, "BMP", "bmp", "image/bmp")
            at(0, 0x49, 0x49, 0x2A, 0x00) || at(0, 0x4D, 0x4D, 0x00, 0x2A) -> when {
                // Most camera RAW files are TIFF inside; their name tells which.
                BY_EXTENSION[ext]?.family == FileFamily.RAW_IMAGE -> known(ext, Known(FileFamily.RAW_IMAGE, "Camera RAW", "image/x-raw"))
                else -> of(FileFamily.IMAGE, "TIFF", if (inExt("tif")) "tif" else "tiff", "image/tiff")
            }
            ascii(4, "ftyp") -> ftyp(String(head, 8, 4.coerceAtMost(head.size - 8).coerceAtLeast(0), Charsets.ISO_8859_1), ext)
            ascii(0, "ID3") -> of(FileFamily.AUDIO, "MP3", "mp3", "audio/mpeg")
            ascii(0, "fLaC") -> of(FileFamily.AUDIO, "FLAC", "flac", "audio/flac")
            ascii(0, "OggS") -> if (inExt("opus")) of(FileFamily.AUDIO, "Opus", "opus", "audio/opus") else of(FileFamily.AUDIO, "Ogg", if (inExt("oga")) "oga" else "ogg", "audio/ogg")
            ascii(0, "#!AMR") -> of(FileFamily.AUDIO, "AMR", "amr", "audio/amr")
            ascii(0, "MThd") -> of(FileFamily.AUDIO, "MIDI", "mid", "audio/midi")
            at(0, 0x1A, 0x45, 0xDF, 0xA3) -> if (inExt("webm")) of(FileFamily.VIDEO, "WebM", "webm", "video/webm") else of(FileFamily.VIDEO, "Matroska (MKV)", if (inExt("mka")) "mka" else "mkv", "video/x-matroska")
            at(0, 0x30, 0x26, 0xB2, 0x75) -> if (inExt("wma")) of(FileFamily.AUDIO, "WMA", "wma", "audio/x-ms-wma") else of(FileFamily.VIDEO, "WMV", "wmv", "video/x-ms-wmv")
            ascii(0, "FLV") -> of(FileFamily.VIDEO, "FLV", "flv", "video/x-flv")
            at(0, 0x00, 0x00, 0x01, 0xBA) -> of(FileFamily.VIDEO, "MPEG", "mpg", "video/mpeg")
            at(0, 0xFF, 0xF1) || at(0, 0xFF, 0xF9) -> of(FileFamily.AUDIO, "AAC", "aac", "audio/aac")
            // An MPEG audio frame (11 sync bits) with no ID3 tag in front.
            head.size >= 2 && (head[0].toInt() and 0xFF) == 0xFF && (head[1].toInt() and 0xE0) == 0xE0 && inExt("mp3", "") ->
                of(FileFamily.AUDIO, "MP3", "mp3", "audio/mpeg")
            ascii(0, "PK\u0003\u0004") || ascii(0, "PK\u0005\u0006") -> zip(head, ext)
            ascii(0, "Rar!\u001A\u0007") -> of(FileFamily.ARCHIVE, "RAR", "rar", "application/vnd.rar")
            at(0, 0x37, 0x7A, 0xBC, 0xAF, 0x27, 0x1C) -> of(FileFamily.ARCHIVE, "7-Zip", "7z", "application/x-7z-compressed")
            at(0, 0x1F, 0x8B) -> of(FileFamily.ARCHIVE, "GZIP", if (inExt("tgz")) "tgz" else "gz", "application/gzip")
            ascii(0, "BZh") -> of(FileFamily.ARCHIVE, "BZIP2", "bz2", "application/x-bzip2")
            at(0, 0xFD, 0x37, 0x7A, 0x58, 0x5A, 0x00) -> of(FileFamily.ARCHIVE, "XZ", "xz", "application/x-xz")
            ascii(257, "ustar") -> of(FileFamily.ARCHIVE, "TAR", "tar", "application/x-tar")
            at(0, 0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1) ->
                known(ext, Known(FileFamily.OFFICE_LEGACY, "Microsoft Office 97-2003", "application/x-ole-storage")).let {
                    if (it.family == FileFamily.OFFICE_LEGACY) it else of(FileFamily.OFFICE_LEGACY, "Microsoft Office 97-2003", ext, "application/x-ole-storage")
                }
            ascii(0, "{\\rtf") -> of(FileFamily.TEXT, "Rich text (RTF)", "rtf", "application/rtf")
            at(0, 0x00, 0x00, 0x01, 0x00) && inExt("ico", "") -> of(FileFamily.IMAGE, "Icon", "ico", "image/x-icon")
            ascii(0, "AT&TFORM") -> of(FileFamily.EBOOK, "DjVu", "djvu", "image/vnd.djvu")
            ascii(60, "BOOKMOBI") -> of(FileFamily.EBOOK, "Mobipocket", "mobi", "application/x-mobipocket-ebook")
            at(0, 0x00, 0x00, 0x00, 0x0C, 0x6A, 0x50) -> of(FileFamily.IMAGE, "JPEG 2000", "jp2", "image/jp2")
            at(0, 0xFF, 0x0A) || at(0, 0x00, 0x00, 0x00, 0x0C, 0x4A, 0x58, 0x4C, 0x20) -> of(FileFamily.IMAGE, "JPEG XL", "jxl", "image/jxl")
            looksLikeSvg(head) -> of(FileFamily.VECTOR_IMAGE, "SVG", "svg", "image/svg+xml")
            ascii(0, "%!PS") -> of(FileFamily.VECTOR_IMAGE, "EPS", "eps", "application/postscript")
            else -> {
                val byName = BY_EXTENSION[ext]
                when {
                    byName != null -> DetectedFormat(byName.family, byName.name, ext, byName.mime)
                    mimeType != null && mimeType.startsWith("text/") || looksLikeText(head) ->
                        of(FileFamily.TEXT, "Text", ext.ifBlank { "txt" }, mimeType?.takeIf { it.startsWith("text/") } ?: "text/plain")
                    mimeType?.startsWith("image/") == true -> of(FileFamily.IMAGE, mimeType.substringAfter('/').uppercase(), ext, mimeType)
                    mimeType?.startsWith("audio/") == true -> of(FileFamily.AUDIO, mimeType.substringAfter('/').uppercase(), ext, mimeType)
                    mimeType?.startsWith("video/") == true -> of(FileFamily.VIDEO, mimeType.substringAfter('/').uppercase(), ext, mimeType)
                    else -> of(FileFamily.OTHER, ext.uppercase().ifBlank { "Unknown" }, ext, mimeType ?: "application/octet-stream")
                }
            }
        }
    }

    /** ISO media (MP4 family): the brand after "ftyp" says picture, sound or film. */
    private fun ftyp(brand: String, ext: String): DetectedFormat {
        val b = brand.lowercase()
        return when {
            b in setOf("heic", "heix", "hevc", "hevx", "heim", "heis", "mif1", "msf1") -> of(FileFamily.IMAGE, "HEIC", if (ext == "heif") "heif" else "heic", "image/heic")
            b == "avif" || b == "avis" -> of(FileFamily.IMAGE, "AVIF", "avif", "image/avif")
            b.startsWith("crx") -> of(FileFamily.RAW_IMAGE, "Camera RAW", "cr3", "image/x-raw")
            b == "m4a " || b == "m4b " || b == "m4p " || ext == "m4a" || ext == "m4b" -> of(FileFamily.AUDIO, "M4A", if (ext == "m4b") "m4b" else "m4a", "audio/mp4")
            b == "qt  " -> of(FileFamily.VIDEO, "QuickTime (MOV)", "mov", "video/quicktime")
            b.startsWith("3g") -> of(FileFamily.VIDEO, "3GP", "3gp", "video/3gpp")
            else -> of(FileFamily.VIDEO, "MP4", if (ext == "m4v") "m4v" else "mp4", "video/mp4")
        }
    }

    /** A ZIP that is really an Office file, an e-book, an OpenDocument or an app, from its first entry and its name. */
    private fun zip(head: ByteArray, ext: String): DetectedFormat {
        val text = String(head, Charsets.ISO_8859_1)
        // EPUB and OpenDocument store an uncompressed "mimetype" entry first, at byte 30.
        if (text.startsWith("mimetype", 30)) {
            val declared = text.substring(38).takeWhile { it.code in 0x21..0x7E }
            when {
                declared.startsWith("application/epub+zip") -> return of(FileFamily.EBOOK, "EPUB", "epub", "application/epub+zip")
                declared.startsWith("application/vnd.oasis.opendocument") -> {
                    val byName = BY_EXTENSION[ext]
                    return if (byName?.family == FileFamily.OPEN_DOCUMENT) DetectedFormat(byName.family, byName.name, ext, byName.mime)
                    else of(FileFamily.OPEN_DOCUMENT, "OpenDocument", ext, declared)
                }
            }
        }
        val byName = BY_EXTENSION[ext]
        if (byName != null && byName.family != FileFamily.TEXT) return DetectedFormat(byName.family, byName.name, ext, byName.mime)
        return when {
            text.contains("word/") -> known("docx", Known(FileFamily.OFFICE, "Word (DOCX)", "")).copy(extension = "docx")
            text.contains("xl/") -> known("xlsx", Known(FileFamily.OFFICE, "Excel (XLSX)", "")).copy(extension = "xlsx")
            text.contains("ppt/") -> known("pptx", Known(FileFamily.OFFICE, "PowerPoint (PPTX)", "")).copy(extension = "pptx")
            text.contains("[Content_Types].xml") -> of(FileFamily.OFFICE, "Office Open XML", ext.ifBlank { "zip" }, "application/zip")
            else -> of(FileFamily.ARCHIVE, "ZIP", "zip", "application/zip")
        }
    }

    private fun looksLikeSvg(head: ByteArray): Boolean {
        val text = String(head, Charsets.UTF_8).trimStart('\uFEFF', ' ', '\n', '\r', '\t').lowercase()
        return text.startsWith("<svg") || (text.startsWith("<?xml") || text.startsWith("<!doctype svg")) && text.contains("<svg")
    }

    /** Readable text: valid UTF-8 (or plain ASCII) with no control characters other than tabs and line breaks. */
    internal fun looksLikeText(head: ByteArray): Boolean {
        if (head.isEmpty()) return false
        var i = 0
        while (i < head.size) {
            val b = head[i].toInt() and 0xFF
            when {
                b == 0x09 || b == 0x0A || b == 0x0D || b == 0x0C -> i++
                b < 0x20 || b == 0x7F -> return false
                b < 0x80 -> i++
                else -> {
                    // A multi-byte UTF-8 sequence; a sequence cut off by the end of the sample is accepted.
                    val extra = when {
                        b and 0xE0 == 0xC0 -> 1
                        b and 0xF0 == 0xE0 -> 2
                        b and 0xF8 == 0xF0 -> 3
                        else -> return false
                    }
                    for (k in 1..extra) {
                        if (i + k >= head.size) return true
                        if ((head[i + k].toInt() and 0xC0) != 0x80) return false
                    }
                    i += extra + 1
                }
            }
        }
        return true
    }
}
