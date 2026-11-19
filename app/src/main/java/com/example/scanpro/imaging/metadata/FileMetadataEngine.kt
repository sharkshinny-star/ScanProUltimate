package com.example.scanpro.imaging.metadata

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import com.example.scanpro.R
import com.example.scanpro.core.concurrent.BackgroundWork
import com.example.scanpro.core.text.AppStrings
import com.example.scanpro.core.text.userRequire
import com.example.scanpro.imaging.PdfToolResult
import com.example.scanpro.imaging.PdfToolsEngine
import com.tom_roush.pdfbox.pdmodel.PDDocumentInformation
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import java.io.File
import java.security.MessageDigest
import java.text.DateFormat
import java.util.Date
import java.util.UUID
import kotlinx.coroutines.withContext

/**
 * File info and metadata (F243): reads the details of any file and saves a copy with them changed or removed. Every
 * read and write works on a private copy, so the original is never touched. What can be written:
 * - PDF: title, author, subject, keywords, creator, producer (PDFBox);
 * - JPEG, PNG, WebP: EXIF description, artist, copyright, comment, camera, software, date taken (Android's EXIF editor);
 *   location and personal details removed, including their XMP and IPTC copies; Remove all strips every block losslessly;
 * - Word, Excel, PowerPoint (DOCX, XLSX, PPTX): the document properties;
 * - MP3, M4A, FLAC, Ogg, WAV, AIFF, WMA, DSF: the common tags (jaudiotagger, Android 8 and later).
 * Everything else is read only, and says so.
 */
object FileMetadataEngine {
    /** Tag values longer than this are cut when shown (an embedded thumbnail, a long XMP packet). */
    private const val MAX_VALUE_CHARS = 600
    /** Text files are counted up to this size. */
    private const val MAX_TEXT_BYTES = 20L * 1024 * 1024
    /** Files up to this size get a SHA-256 checksum. */
    private const val MAX_HASH_BYTES = 512L * 1024 * 1024
    /** Audio formats whose tags jaudiotagger reads and writes. */
    private val AUDIO_TAG_EXTENSIONS = setOf("mp3", "m4a", "m4b", "m4p", "ogg", "oga", "flac", "wav", "aif", "aiff", "aifc", "wma", "dsf")
    private val EXIF_WRITABLE = setOf("JPEG", "PNG", "WebP")

    /** The EXIF fields offered for editing: id, EXIF tag, name, and what it says of a person. */
    private class ExifField(val id: String, val tag: String, val name: String, val sensitivity: Sensitivity = Sensitivity.NONE, val group: MetaGroup = MetaGroup.CAMERA)
    private val EXIF_FIELDS = listOf(
        ExifField("exif.description", ExifInterface.TAG_IMAGE_DESCRIPTION, "Description", group = MetaGroup.IMAGE),
        ExifField("exif.artist", ExifInterface.TAG_ARTIST, "Artist", Sensitivity.PERSONAL, MetaGroup.IMAGE),
        ExifField("exif.copyright", ExifInterface.TAG_COPYRIGHT, "Copyright", Sensitivity.PERSONAL, MetaGroup.IMAGE),
        ExifField("exif.comment", ExifInterface.TAG_USER_COMMENT, "Comment", Sensitivity.PERSONAL, MetaGroup.IMAGE),
        ExifField("exif.make", ExifInterface.TAG_MAKE, "Camera make"),
        ExifField("exif.model", ExifInterface.TAG_MODEL, "Camera model"),
        ExifField("exif.software", ExifInterface.TAG_SOFTWARE, "Software"),
        ExifField("exif.dateTaken", ExifInterface.TAG_DATETIME_ORIGINAL, "Date taken"),
    )
    /** Taken out by Remove personal besides the editable ones above: who owns the camera and its serial numbers. */
    private val EXIF_PERSONAL_EXTRA = listOf(
        ExifInterface.TAG_CAMERA_OWNER_NAME, ExifInterface.TAG_BODY_SERIAL_NUMBER, ExifInterface.TAG_LENS_SERIAL_NUMBER,
        ExifInterface.TAG_IMAGE_UNIQUE_ID
    )
    private val EXIF_GPS_TAGS = listOf(
        ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF, ExifInterface.TAG_GPS_LONGITUDE, ExifInterface.TAG_GPS_LONGITUDE_REF,
        ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_ALTITUDE_REF, ExifInterface.TAG_GPS_TIMESTAMP, ExifInterface.TAG_GPS_DATESTAMP,
        ExifInterface.TAG_GPS_PROCESSING_METHOD, ExifInterface.TAG_GPS_SPEED, ExifInterface.TAG_GPS_SPEED_REF, ExifInterface.TAG_GPS_IMG_DIRECTION,
        ExifInterface.TAG_GPS_IMG_DIRECTION_REF, ExifInterface.TAG_GPS_DEST_LATITUDE, ExifInterface.TAG_GPS_DEST_LONGITUDE,
        ExifInterface.TAG_GPS_AREA_INFORMATION, ExifInterface.TAG_GPS_MAP_DATUM, ExifInterface.TAG_GPS_TRACK, ExifInterface.TAG_GPS_SATELLITES,
        ExifInterface.TAG_GPS_H_POSITIONING_ERROR, ExifInterface.TAG_GPS_VERSION_ID
    )

    private class PdfField(val id: String, val name: String, val sensitivity: Sensitivity = Sensitivity.NONE)
    private val PDF_FIELDS = listOf(
        PdfField("pdf.title", "Title"), PdfField("pdf.author", "Author", Sensitivity.PERSONAL), PdfField("pdf.subject", "Subject"),
        PdfField("pdf.keywords", "Keywords"), PdfField("pdf.creator", "Created with"), PdfField("pdf.producer", "PDF producer"),
    )

    /** Audio tags offered for editing (jaudiotagger's names), and the two Remove personal takes out. */
    private val AUDIO_FIELDS = listOf(
        "TITLE" to "Title", "ARTIST" to "Artist", "ALBUM" to "Album", "ALBUM_ARTIST" to "Album artist", "YEAR" to "Year",
        "GENRE" to "Genre", "TRACK" to "Track", "DISC_NO" to "Disc", "COMPOSER" to "Composer", "COMMENT" to "Comment",
        "LYRICIST" to "Lyricist", "ENCODER" to "Encoded by"
    )
    private val AUDIO_PERSONAL = setOf("COMMENT", "ENCODER")

    // ---------------------------------------------------------------------------------------------------------------
    // Reading
    // ---------------------------------------------------------------------------------------------------------------

    /** A picked file copied privately, with what its app says of it. */
    private class Picked(val file: File, val name: String, val mime: String?, val modified: Long?)

    private fun pick(context: Context, uri: Uri): Picked {
        val resolver = context.contentResolver
        var name: String? = null
        var modified: Long? = null
        runCatching {
            resolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { name = c.getString(it) }
                    c.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED).takeIf { it >= 0 && !c.isNull(it) }?.let { modified = c.getLong(it) }
                }
            }
        }
        if (name == null && uri.scheme == "file") name = uri.lastPathSegment
        val shown = name?.takeIf { it.isNotBlank() } ?: "file"
        // Kept with its extension: the audio tagger and some readers go by it.
        val ext = shown.substringAfterLast('.', "").lowercase().filter { it.isLetterOrDigit() }.take(8)
        val temp = PdfToolsEngine.copyUriToTemp(context, uri, "meta_${UUID.randomUUID()}${if (ext.isEmpty()) "" else ".$ext"}")
        return Picked(temp, shown, runCatching { resolver.getType(uri) }.getOrNull(), modified)
    }

    suspend fun read(context: Context, uri: Uri): FileMetadata = withContext(BackgroundWork.tools) {
        val picked = pick(context, uri)
        try {
            read(context, picked.file, picked.name, picked.mime, picked.modified)
        } finally {
            picked.file.delete()
        }
    }

    /** The details of [file] (a private copy) whose name was [name]. */
    internal fun read(context: Context, file: File, name: String, mime: String?, modified: Long?): FileMetadata {
        val head = file.inputStream().use { input -> ByteArray(FileFormat.HEAD_BYTES).let { b -> b.copyOf(input.read(b).coerceAtLeast(0)) } }
        val format = FileFormat.detect(head, name, mime)
        val fields = mutableListOf<MetaField>()
        fun add(id: String, group: MetaGroup, label: String, value: Any?, editable: Boolean = false, sensitivity: Sensitivity = Sensitivity.NONE, source: String = "") {
            val text = value?.toString()?.trim().orEmpty()
            if (text.isNotEmpty() || editable) fields += MetaField(id, group, label, text.take(MAX_VALUE_CHARS), editable = editable, sensitivity = if (text.isEmpty()) Sensitivity.NONE else sensitivity, source = source)
        }

        add("file.name", MetaGroup.FILE, "Name", name)
        add("file.format", MetaGroup.FILE, "Format", format.name)
        add("file.mime", MetaGroup.FILE, "Type", format.mime)
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext.isNotEmpty() && format.extension.isNotEmpty() && ext != format.extension && FileFormat.detect(ByteArray(0), name, null).name != format.name) {
            add("file.mismatch", MetaGroup.FILE, "Name says", ".$ext, but the contents are ${format.name}")
        }
        add("file.size", MetaGroup.FILE, "Size", "${file.length()} bytes")
        modified?.let { add("file.modified", MetaGroup.FILE, "Last changed", DateFormat.getDateTimeInstance().format(Date(it))) }
        if (file.length() <= MAX_HASH_BYTES) add("file.sha256", MetaGroup.FILE, "SHA-256", sha256(file))

        var abilities = MetadataAbilities(readOnlyReason = R.string.meta_readonly_format)
        when (format.family) {
            FileFamily.PDF -> abilities = readPdf(file, fields)
            FileFamily.IMAGE, FileFamily.RAW_IMAGE -> abilities = readImage(file, format, fields)
            FileFamily.AUDIO -> abilities = readAudio(file, format, fields)
            FileFamily.VIDEO -> { readMedia(file, fields, video = true); readTagDirectories(file, fields); abilities = MetadataAbilities(readOnlyReason = R.string.meta_readonly_video) }
            FileFamily.OFFICE -> runCatching { OfficeMetadata.readOoxml(file) }.getOrNull()?.let { (found, writable) ->
                fields += found
                abilities = if (writable) MetadataAbilities(canEdit = true, canRemovePersonal = true, canRemoveAll = true) else MetadataAbilities(readOnlyReason = R.string.meta_readonly_no_properties)
            }
            FileFamily.OFFICE_LEGACY -> abilities = MetadataAbilities(readOnlyReason = R.string.meta_readonly_legacy_office)
            FileFamily.OPEN_DOCUMENT -> runCatching { fields += OfficeMetadata.readOpenDocument(file) }
            FileFamily.EBOOK -> if (format.name == "EPUB") runCatching { fields += OfficeMetadata.readEpub(file) }
            FileFamily.ARCHIVE -> if (head.size >= 2 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()) {
                runCatching { fields += OfficeMetadata.readZip(file) }
            } else abilities = MetadataAbilities(readOnlyReason = R.string.meta_readonly_archive)
            FileFamily.TEXT -> readText(file, format, fields)
            FileFamily.VECTOR_IMAGE -> readSvg(file, format, fields)
            FileFamily.OTHER -> Unit
        }
        return FileMetadata(name, format, file.length(), fields, abilities)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered(64 * 1024).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun readPdf(file: File, fields: MutableList<MetaField>): MetadataAbilities = try {
        PdfToolsEngine.openPdf(file).use { doc ->
            val info = doc.documentInformation
            val values = mapOf("pdf.title" to info.title, "pdf.author" to info.author, "pdf.subject" to info.subject,
                "pdf.keywords" to info.keywords, "pdf.creator" to info.creator, "pdf.producer" to info.producer)
            PDF_FIELDS.forEach { f ->
                val v = values[f.id].orEmpty().trim()
                fields += MetaField(f.id, MetaGroup.DOCUMENT, f.name, v.take(MAX_VALUE_CHARS), editable = true, sensitivity = if (v.isEmpty()) Sensitivity.NONE else f.sensitivity, source = "PDF")
            }
            fun date(c: java.util.Calendar?) = c?.let { DateFormat.getDateTimeInstance().format(it.time) }
            date(info.creationDate)?.let { fields += MetaField("pdf.created", MetaGroup.DOCUMENT, "Created", it, source = "PDF") }
            date(info.modificationDate)?.let { fields += MetaField("pdf.modified", MetaGroup.DOCUMENT, "Modified", it, source = "PDF") }
            fields += MetaField("pdf.pages", MetaGroup.CONTENTS, "Pages", doc.numberOfPages.toString(), source = "PDF")
            fields += MetaField("pdf.version", MetaGroup.CONTENTS, "PDF version", doc.version.toString(), source = "PDF")
            if (doc.isEncrypted) fields += MetaField("pdf.encrypted", MetaGroup.CONTENTS, "Restrictions", "Yes (owner password)", source = "PDF")
            doc.documentCatalog.metadata?.let {
                fields += MetaField("pdf.xmp", MetaGroup.TAGS, "XMP metadata", "Present (repeats the details above)", sensitivity = Sensitivity.PERSONAL, source = "XMP")
            }
            // Other keys apps put in the information dictionary (company, source file paths...).
            info.metadataKeys.filter { it !in setOf("Title", "Author", "Subject", "Keywords", "Creator", "Producer", "CreationDate", "ModDate", "Trapped") }.forEach { key ->
                info.getCustomMetadataValue(key)?.takeIf { it.isNotBlank() }?.let {
                    fields += MetaField("pdf.custom.$key", MetaGroup.DOCUMENT, key, it.take(MAX_VALUE_CHARS), sensitivity = Sensitivity.PERSONAL, source = "PDF custom")
                }
            }
            MetadataAbilities(canEdit = true, canRemovePersonal = true, canRemoveAll = true)
        }
    } catch (_: InvalidPasswordException) {
        MetadataAbilities(readOnlyReason = R.string.meta_readonly_pdf_locked)
    } catch (_: Exception) {
        MetadataAbilities(readOnlyReason = R.string.meta_readonly_unreadable)
    }

    private fun readImage(file: File, format: DetectedFormat, fields: MutableList<MetaField>): MetadataAbilities {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { BitmapFactory.decodeFile(file.absolutePath, bounds) }
        val exif = runCatching { ExifInterface(file) }.getOrNull()
        val width = bounds.outWidth.takeIf { it > 0 } ?: exif?.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0)?.takeIf { it > 0 }
        val height = bounds.outHeight.takeIf { it > 0 } ?: exif?.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0)?.takeIf { it > 0 }
        if (width != null && height != null) {
            fields += MetaField("image.size", MetaGroup.IMAGE, "Dimensions", "$width × $height px", source = "")
            fields += MetaField("image.megapixels", MetaGroup.IMAGE, "Megapixels", "%.1f MP".format(width.toLong() * height / 1_000_000.0))
        }
        exif?.let { e ->
            e.getAttribute(ExifInterface.TAG_X_RESOLUTION)?.let { fields += MetaField("image.dpi", MetaGroup.IMAGE, "Resolution", "$it dpi") }
            val orientation = e.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)
            if (orientation > ExifInterface.ORIENTATION_NORMAL) fields += MetaField("image.orientation", MetaGroup.IMAGE, "Turned", "${e.rotationDegrees}°${if (e.isFlipped) ", mirrored" else ""}")
            e.latLong?.let { (lat, lon) ->
                fields += MetaField("location.coordinates", MetaGroup.LOCATION, "Coordinates", "%.6f, %.6f".format(lat, lon), sensitivity = Sensitivity.LOCATION, source = "EXIF GPS")
                val alt = e.getAltitude(Double.NaN)
                if (!alt.isNaN()) fields += MetaField("location.altitude", MetaGroup.LOCATION, "Altitude", "%.0f m".format(alt), sensitivity = Sensitivity.LOCATION, source = "EXIF GPS")
            }
        }
        val writable = format.name in EXIF_WRITABLE && exif != null
        EXIF_FIELDS.forEach { f ->
            val v = exif?.getAttribute(f.tag)?.trim().orEmpty()
            if (v.isNotEmpty() || writable) fields += MetaField(f.id, f.group, f.name, v.take(MAX_VALUE_CHARS), editable = writable, sensitivity = if (v.isEmpty()) Sensitivity.NONE else f.sensitivity, source = "EXIF")
        }
        EXIF_PERSONAL_EXTRA.forEach { tag ->
            exif?.getAttribute(tag)?.takeIf { it.isNotBlank() }?.let { fields += MetaField("exif.$tag", MetaGroup.CAMERA, tag.replace(Regex("([a-z])([A-Z])"), "$1 $2"), it, sensitivity = Sensitivity.PERSONAL, source = "EXIF") }
        }
        readTagDirectories(file, fields)
        return if (writable) MetadataAbilities(canEdit = true, canRemoveLocation = true, canRemovePersonal = true, canRemoveAll = true)
        else MetadataAbilities(readOnlyReason = if (format.family == FileFamily.RAW_IMAGE) R.string.meta_readonly_raw else R.string.meta_readonly_image_format)
    }

    /**
     * Every tag metadata-extractor finds (EXIF, IPTC, XMP, ICC, maker notes, QuickTime...), grouped under its block's
     * name. Its "File" blocks are left out: they would describe the private copy, not the picked file.
     */
    private fun readTagDirectories(file: File, fields: MutableList<MetaField>) {
        val metadata = runCatching { com.drew.imaging.ImageMetadataReader.readMetadata(file) }.getOrNull() ?: return
        for (directory in metadata.directories) {
            val dirName = directory.name
            if (dirName == "File" || dirName == "File Type") continue
            val group = when {
                dirName.startsWith("GPS") -> MetaGroup.LOCATION
                dirName.startsWith("Exif") || dirName.contains("Makernote") -> MetaGroup.CAMERA
                dirName.startsWith("QuickTime") || dirName.startsWith("MP4") || dirName == "AVI" -> MetaGroup.VIDEO
                else -> MetaGroup.TAGS
            }
            for (tag in directory.tags) {
                val value = runCatching { tag.description }.getOrNull()?.trim().orEmpty()
                if (value.isEmpty()) continue
                val personal = PERSONAL_TAG_WORDS.any { tag.tagName.contains(it, ignoreCase = true) }
                val sensitivity = when {
                    group == MetaGroup.LOCATION -> Sensitivity.LOCATION
                    personal -> Sensitivity.PERSONAL
                    else -> Sensitivity.NONE
                }
                fields += MetaField("tag.$dirName.${tag.tagType}", group, tag.tagName, value.take(MAX_VALUE_CHARS), sensitivity = sensitivity, source = dirName)
            }
            directory.errors.firstOrNull()?.let { fields += MetaField("tag.$dirName.error", MetaGroup.TAGS, "Note", it.take(200), source = dirName) }
        }
    }
    private val PERSONAL_TAG_WORDS = listOf("Artist", "Author", "Copyright", "Owner", "Serial", "By-line", "Creator", "Contact", "Credit", "Unique ID", "User Comment", "Writer", "Caption Writer", "Person")

    private fun readAudio(file: File, format: DetectedFormat, fields: MutableList<MetaField>): MetadataAbilities {
        readMedia(file, fields, video = false)
        val ext = file.extension.lowercase()
        val canTag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && ext in AUDIO_TAG_EXTENSIONS
        if (!canTag) {
            return MetadataAbilities(readOnlyReason = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O && ext in AUDIO_TAG_EXTENSIONS) R.string.meta_readonly_audio_old_android else R.string.meta_readonly_audio_format)
        }
        quietTagger()
        val audio = runCatching { org.jaudiotagger.audio.AudioFileIO.read(file) }.getOrNull()
            ?: return MetadataAbilities(readOnlyReason = R.string.meta_readonly_unreadable)
        // The tagger's view replaces Android's: it knows every tag, Android only the common ones.
        fields.removeAll { it.group == MetaGroup.AUDIO && it.id.startsWith("media.tag.") }
        runCatching {
            val header = audio.audioHeader
            fields += MetaField("audio.encoding", MetaGroup.AUDIO, "Encoding", header.encodingType.orEmpty(), source = "Audio")
            fields += MetaField("audio.bitrate", MetaGroup.AUDIO, "Bitrate", "${header.bitRate} kbps${if (header.isVariableBitRate) " (variable)" else ""}", source = "Audio")
            fields += MetaField("audio.sampleRate", MetaGroup.AUDIO, "Sample rate", "${header.sampleRate} Hz", source = "Audio")
            fields += MetaField("audio.channels", MetaGroup.AUDIO, "Channels", header.channels.orEmpty(), source = "Audio")
            if (header.bitsPerSample > 0) fields += MetaField("audio.bits", MetaGroup.AUDIO, "Bits per sample", header.bitsPerSample.toString(), source = "Audio")
            fields += MetaField("audio.lossless", MetaGroup.AUDIO, "Lossless", if (header.isLossless) "Yes" else "No", source = "Audio")
        }
        val tag = audio.tag
        AUDIO_FIELDS.forEach { (key, label) ->
            val v = runCatching { tag?.getFirst(org.jaudiotagger.tag.FieldKey.valueOf(key)) }.getOrNull()?.trim().orEmpty()
            fields += MetaField("audio.tag.$key", MetaGroup.AUDIO, label, v.take(MAX_VALUE_CHARS), editable = true,
                sensitivity = if (v.isNotEmpty() && key in AUDIO_PERSONAL) Sensitivity.PERSONAL else Sensitivity.NONE, source = tag?.javaClass?.simpleName ?: "Tags")
        }
        runCatching { tag?.artworkList?.size ?: 0 }.getOrDefault(0).takeIf { it > 0 }?.let {
            fields += MetaField("audio.artwork", MetaGroup.AUDIO, "Cover pictures", it.toString(), source = "Tags")
        }
        return MetadataAbilities(canEdit = true, canRemovePersonal = true, canRemoveAll = true)
    }

    private fun quietTagger() {
        // jaudiotagger logs every frame it reads; nothing of it is wanted in logcat.
        java.util.logging.Logger.getLogger("org.jaudiotagger").level = java.util.logging.Level.OFF
    }

    /** What Android's media reader says of a sound or film: length, quality, tracks and codecs, and the common tags. */
    private fun readMedia(file: File, fields: MutableList<MetaField>, video: Boolean) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            fun get(key: Int) = runCatching { retriever.extractMetadata(key) }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
            val group = if (video) MetaGroup.VIDEO else MetaGroup.AUDIO
            get(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let { ms ->
                val s = ms / 1000
                fields += MetaField("media.duration", group, "Length", if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60))
            }
            get(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()?.let { fields += MetaField("media.bitrate", group, "Overall bitrate", "${it / 1000} kbps") }
            if (video) {
                val w = get(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                val h = get(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                if (w != null && h != null) fields += MetaField("video.size", MetaGroup.VIDEO, "Dimensions", "$w × $h px")
                get(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.takeIf { it != "0" }?.let { fields += MetaField("video.rotation", MetaGroup.VIDEO, "Turned", "$it°") }
                get(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.let { fields += MetaField("video.captureRate", MetaGroup.VIDEO, "Capture frame rate", "$it fps") }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) get(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.let { fields += MetaField("video.frames", MetaGroup.VIDEO, "Frames", it) }
                get(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)?.let { fields += MetaField("video.hasAudio", MetaGroup.VIDEO, "Sound", if (it == "yes") "Yes" else "No") }
            }
            get(MediaMetadataRetriever.METADATA_KEY_LOCATION)?.let { fields += MetaField("location.media", MetaGroup.LOCATION, "Recorded at", it, sensitivity = Sensitivity.LOCATION, source = "Media") }
            listOf(
                MediaMetadataRetriever.METADATA_KEY_TITLE to "Title", MediaMetadataRetriever.METADATA_KEY_ARTIST to "Artist",
                MediaMetadataRetriever.METADATA_KEY_ALBUM to "Album", MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST to "Album artist",
                MediaMetadataRetriever.METADATA_KEY_AUTHOR to "Author", MediaMetadataRetriever.METADATA_KEY_COMPOSER to "Composer",
                MediaMetadataRetriever.METADATA_KEY_GENRE to "Genre", MediaMetadataRetriever.METADATA_KEY_YEAR to "Year",
                MediaMetadataRetriever.METADATA_KEY_DATE to "Date", MediaMetadataRetriever.METADATA_KEY_WRITER to "Writer"
            ).forEach { (key, label) ->
                get(key)?.let { fields += MetaField("media.tag.$key", group, label, it.take(MAX_VALUE_CHARS), sensitivity = if (key == MediaMetadataRetriever.METADATA_KEY_AUTHOR || key == MediaMetadataRetriever.METADATA_KEY_WRITER) Sensitivity.PERSONAL else Sensitivity.NONE, source = "Media") }
            }
        } catch (_: Exception) {
            // Not a format Android can open: the other readers may still say something.
        } finally {
            runCatching { retriever.release() }
        }
        readTracks(file, fields)
    }

    /** Each track's codec and its own figures (sample rate, channels, size, language). */
    private fun readTracks(file: File, fields: MutableList<MetaField>) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                val parts = mutableListOf(mime)
                fun int(key: String) = if (f.containsKey(key)) runCatching { f.getInteger(key) }.getOrNull() else null
                int(MediaFormat.KEY_WIDTH)?.let { w -> int(MediaFormat.KEY_HEIGHT)?.let { h -> parts += "$w × $h" } }
                int(MediaFormat.KEY_FRAME_RATE)?.let { parts += "$it fps" }
                int(MediaFormat.KEY_SAMPLE_RATE)?.let { parts += "$it Hz" }
                int(MediaFormat.KEY_CHANNEL_COUNT)?.let { parts += "$it ch" }
                int(MediaFormat.KEY_BIT_RATE)?.let { parts += "${it / 1000} kbps" }
                if (f.containsKey(MediaFormat.KEY_LANGUAGE)) f.getString(MediaFormat.KEY_LANGUAGE)?.takeIf { it != "und" }?.let { parts += it }
                val group = if (mime.startsWith("video/")) MetaGroup.VIDEO else MetaGroup.AUDIO
                fields += MetaField("track.$i", group, "Track ${i + 1}", parts.joinToString(" · "), source = "Codecs")
            }
        } catch (_: Exception) {
        } finally {
            runCatching { extractor.release() }
        }
    }

    private fun readText(file: File, format: DetectedFormat, fields: MutableList<MetaField>) {
        if (file.length() > MAX_TEXT_BYTES) {
            fields += MetaField("text.tooLarge", MetaGroup.CONTENTS, "Counted", "No (over 20 MB)")
            return
        }
        val bytes = file.readBytes()
        val bom = bytes.size >= 3 && (bytes[0].toInt() and 0xFF) == 0xEF && (bytes[1].toInt() and 0xFF) == 0xBB && (bytes[2].toInt() and 0xFF) == 0xBF
        val utf8 = FileFormat.looksLikeText(bytes)
        fields += MetaField("text.encoding", MetaGroup.CONTENTS, "Encoding", when { bom -> "UTF-8 with BOM"; utf8 -> "UTF-8"; else -> "Not UTF-8 (another code page)" })
        val text = String(bytes, if (utf8 || bom) Charsets.UTF_8 else Charsets.ISO_8859_1)
        val lines = if (text.isEmpty()) 0 else text.count { it == '\n' } + if (text.endsWith('\n')) 0 else 1
        fields += MetaField("text.lines", MetaGroup.CONTENTS, "Lines", lines.toString())
        fields += MetaField("text.words", MetaGroup.CONTENTS, "Words", text.split(Regex("\\s+")).count { it.isNotEmpty() }.toString())
        fields += MetaField("text.characters", MetaGroup.CONTENTS, "Characters", text.length.toString())
        fields += MetaField("text.lineEnds", MetaGroup.CONTENTS, "Line endings", when { text.contains("\r\n") -> "Windows (CRLF)"; text.contains('\r') -> "Old Mac (CR)"; else -> "Unix (LF)" })
        if (format.name == "CSV" || format.name == "TSV") {
            val sep = if (format.name == "TSV") '\t' else listOf(',', ';', '\t').maxBy { c -> text.lineSequence().firstOrNull().orEmpty().count { it == c } }
            fields += MetaField("csv.columns", MetaGroup.CONTENTS, "Columns", (text.lineSequence().firstOrNull().orEmpty().count { it == sep } + 1).toString())
            fields += MetaField("csv.rows", MetaGroup.CONTENTS, "Rows (with header)", text.lineSequence().count { it.isNotBlank() }.toString())
            fields += MetaField("csv.separator", MetaGroup.CONTENTS, "Separator", when (sep) { '\t' -> "Tab"; else -> sep.toString() })
        }
        if (format.name.startsWith("Rich text")) {
            // RTF keeps its document properties in an \info group: {\title ...} {\author ...}.
            listOf("title" to "Title", "subject" to "Subject", "author" to "Author", "operator" to "Last modified by", "company" to "Company", "keywords" to "Keywords").forEach { (key, label) ->
                Regex("\\{\\\\$key\\s+([^}]*)\\}").find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    fields += MetaField("rtf.$key", MetaGroup.DOCUMENT, label, it.take(MAX_VALUE_CHARS), sensitivity = if (key == "author" || key == "operator" || key == "company") Sensitivity.PERSONAL else Sensitivity.NONE, source = "RTF")
                }
            }
        }
        if (format.name == "HTML") Regex("<title[^>]*>([^<]*)</title>", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }?.let {
            fields += MetaField("html.title", MetaGroup.DOCUMENT, "Title", it.take(MAX_VALUE_CHARS), source = "HTML")
        }
    }

    private fun readSvg(file: File, format: DetectedFormat, fields: MutableList<MetaField>) {
        if (format.name != "SVG") return
        val text = file.inputStream().use { String(it.readNBytesCompat(256 * 1024), Charsets.UTF_8) }
        val svg = Regex("<svg\\b[^>]*>", RegexOption.IGNORE_CASE).find(text)?.value.orEmpty()
        fun attr(name: String) = Regex("\\b$name\\s*=\\s*\"([^\"]*)\"").find(svg)?.groupValues?.get(1)
        attr("width")?.let { fields += MetaField("svg.width", MetaGroup.IMAGE, "Width", it, source = "SVG") }
        attr("height")?.let { fields += MetaField("svg.height", MetaGroup.IMAGE, "Height", it, source = "SVG") }
        attr("viewBox")?.let { fields += MetaField("svg.viewBox", MetaGroup.IMAGE, "View box", it, source = "SVG") }
        Regex("<title[^>]*>([^<]*)</title>").find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }?.let { fields += MetaField("svg.title", MetaGroup.DOCUMENT, "Title", it, source = "SVG") }
        Regex("<desc[^>]*>([^<]*)</desc>").find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }?.let { fields += MetaField("svg.desc", MetaGroup.DOCUMENT, "Description", it.take(MAX_VALUE_CHARS), source = "SVG") }
        if (text.contains("<metadata", ignoreCase = true)) fields += MetaField("svg.metadata", MetaGroup.TAGS, "Metadata block", "Present", sensitivity = Sensitivity.PERSONAL, source = "SVG")
    }

    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (out.size() < limit) {
            val n = read(buffer, 0, minOf(buffer.size, limit - out.size()))
            if (n < 0) break
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Writing
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * Writes a copy of the file at [uri] with [edit] applied, to [output] (which must carry the file's extension).
     * [meta] is what was read, so only what the screen offered is written. The original is never changed.
     */
    suspend fun write(context: Context, uri: Uri, meta: FileMetadata, edit: MetadataEdit, output: File): PdfToolResult = withContext(BackgroundWork.tools) {
        userRequire(!edit.isEmpty, R.string.meta_err_nothing_to_save)
        val picked = pick(context, uri)
        try {
            output.parentFile?.mkdirs()
            when {
                meta.format.family == FileFamily.PDF -> writePdf(picked.file, output, edit)
                meta.format.family == FileFamily.IMAGE && meta.format.name in EXIF_WRITABLE -> writeImage(picked.file, output, meta.format, edit)
                meta.format.family == FileFamily.OFFICE -> OfficeMetadata.writeOoxml(picked.file, output, edit)
                meta.format.family == FileFamily.AUDIO -> writeAudio(picked.file, output, edit)
                else -> error("Read only")
            }
            PdfToolResult(
                outputFile = output,
                pageCount = 1,
                originalSizeBytes = picked.file.length(),
                outputSizeBytes = output.length(),
                message = AppStrings.get(
                    when {
                        edit.removeAll -> R.string.meta_done_removed_all
                        edit.removeLocation -> R.string.meta_done_removed_location
                        edit.removePersonal -> R.string.meta_done_removed_personal
                        else -> R.string.meta_done_saved
                    }
                )
            )
        } catch (error: Throwable) {
            output.delete()
            throw error
        } finally {
            picked.file.delete()
        }
    }

    private fun writePdf(source: File, output: File, edit: MetadataEdit) {
        PdfToolsEngine.loadPdf(source).use { doc ->
            if (edit.removeAll) {
                doc.documentInformation = PDDocumentInformation()
                doc.documentCatalog.metadata = null
                for (page in doc.pages) page.metadata = null
            } else {
                val info = doc.documentInformation
                fun value(id: String, current: String?): String? = if (id in edit.values) edit.values[id]?.trim()?.ifEmpty { null } else current
                info.title = value("pdf.title", info.title)
                info.author = if (edit.removePersonal) null else value("pdf.author", info.author)
                info.subject = value("pdf.subject", info.subject)
                info.keywords = value("pdf.keywords", info.keywords)
                info.creator = value("pdf.creator", info.creator)
                info.producer = value("pdf.producer", info.producer)
                if (edit.removePersonal) {
                    info.metadataKeys.filter { it !in setOf("Title", "Subject", "Keywords", "Creator", "Producer", "CreationDate", "ModDate", "Trapped") }
                        .forEach { info.setCustomMetadataValue(it, null) }
                }
                info.modificationDate = java.util.Calendar.getInstance()
                doc.documentInformation = info
                // The XMP copy would still say the old title and author: it goes, as in the PDF info tool before.
                doc.documentCatalog.metadata = null
            }
            doc.save(output)
        }
    }

    private fun writeImage(source: File, output: File, format: DetectedFormat, edit: MetadataEdit) {
        val original = source.readBytes()
        val orientation = runCatching { ExifInterface(source).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
            .getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        if (edit.removeAll) {
            val clean = when (format.name) {
                "JPEG" -> MetadataStrip.jpeg(original)
                "PNG" -> MetadataStrip.png(original)
                else -> MetadataStrip.webp(original)
            }
            output.writeBytes(clean)
            // The way the picture is meant to be shown was in EXIF too: put back alone, so it does not turn sideways.
            if (orientation != ExifInterface.ORIENTATION_NORMAL && orientation != ExifInterface.ORIENTATION_UNDEFINED) {
                ExifInterface(output).apply { setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString()); saveAttributes() }
            }
            return
        }
        output.writeBytes(if (edit.removeLocation || edit.removePersonal) MetadataStrip.withoutXmpAndIptc(original) else original)
        val exif = ExifInterface(output)
        EXIF_FIELDS.forEach { f ->
            if (f.id in edit.values) exif.setAttribute(f.tag, edit.values[f.id]?.trim()?.ifEmpty { null })
        }
        if (edit.removeLocation || edit.removePersonal) EXIF_GPS_TAGS.forEach { exif.setAttribute(it, null) }
        if (edit.removePersonal) {
            EXIF_FIELDS.filter { it.sensitivity == Sensitivity.PERSONAL }.forEach { exif.setAttribute(it.tag, null) }
            EXIF_PERSONAL_EXTRA.forEach { exif.setAttribute(it, null) }
        }
        exif.saveAttributes()
    }

    private fun writeAudio(source: File, output: File, edit: MetadataEdit) {
        require(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { "Audio tags need Android 8" }
        quietTagger()
        source.copyTo(output, overwrite = true)
        val audio = org.jaudiotagger.audio.AudioFileIO.read(output)
        if (edit.removeAll) {
            org.jaudiotagger.audio.AudioFileIO.delete(audio)
            return
        }
        val tag = audio.tagOrCreateAndSetDefault
        edit.values.forEach { (id, value) ->
            val key = id.removePrefix("audio.tag.").takeIf { id.startsWith("audio.tag.") }?.let { runCatching { org.jaudiotagger.tag.FieldKey.valueOf(it) }.getOrNull() } ?: return@forEach
            if (value.isNullOrBlank()) runCatching { tag.deleteField(key) } else tag.setField(key, value.trim())
        }
        if (edit.removePersonal) AUDIO_PERSONAL.forEach { k -> runCatching { tag.deleteField(org.jaudiotagger.tag.FieldKey.valueOf(k)) } }
        audio.commit()
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Export
    // ---------------------------------------------------------------------------------------------------------------

    /** Writes the details as JSON or CSV to [output]. */
    suspend fun export(meta: FileMetadata, json: Boolean, output: File): PdfToolResult = withContext(BackgroundWork.tools) {
        output.parentFile?.mkdirs()
        output.writeText(if (json) MetadataExport.json(meta) else MetadataExport.csv(meta), Charsets.UTF_8)
        PdfToolResult(
            outputFile = output,
            pageCount = 1,
            originalSizeBytes = meta.sizeBytes,
            outputSizeBytes = output.length(),
            message = AppStrings.get(R.string.meta_done_exported, meta.fields.size)
        )
    }
}
