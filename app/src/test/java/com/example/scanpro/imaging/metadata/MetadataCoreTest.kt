package com.example.scanpro.imaging.metadata

import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * File info and metadata (F243): format detection, export, the lossless strippers and Office properties. Plain JVM, no
 * Android; the same checks were run with kotlinc in the cloud session that wrote them.
 */
class MetadataCoreTest {
    private fun eq(a: Any?, b: Any?, m: String) = assertEquals(m, b, a)
    private fun ok(c: Boolean, m: String) = assertTrue(m, c)
    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }
    private fun ascii(s: String) = s.toByteArray(Charsets.ISO_8859_1)
    private fun pad(b: ByteArray, n: Int = 600) = b + ByteArray(maxOf(0, n - b.size))
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun formatsAreRecognisedByTheirBytes() {
    fun d(head: ByteArray, name: String?, mime: String? = null) = FileFormat.detect(pad(head), name, mime)
    eq(FileFamily.PDF, d(ascii("%PDF-1.7"), "x.bin").family, "pdf by bytes")
    eq("JPEG", d(bytes(0xFF, 0xD8, 0xFF, 0xE0), "photo.png").name, "renamed jpeg")
    eq("WebP", d(ascii("RIFF\u0000\u0000\u0000\u0000WEBPVP8 "), null).name, "webp")
    eq("WAV", d(ascii("RIFF\u0000\u0000\u0000\u0000WAVEfmt "), null).name, "wav")
    eq("HEIC", d(ascii("\u0000\u0000\u0000\u0018ftypheic"), "IMG.HEIC").name, "heic")
    eq("QuickTime (MOV)", d(ascii("\u0000\u0000\u0000\u0014ftypqt  "), "a.mov").name, "mov")
    eq("M4A", d(ascii("\u0000\u0000\u0000 ftypM4A "), "song.m4a").name, "m4a")
    eq("MP4", d(ascii("\u0000\u0000\u0000 ftypisom"), "clip").name, "mp4")
    eq(FileFamily.RAW_IMAGE, d(bytes(0x49, 0x49, 0x2A, 0x00), "DSC.NEF").family, "nef raw")
    eq("TIFF", d(bytes(0x4D, 0x4D, 0x00, 0x2A), "scan.tif").name, "tiff")
    eq("MP3", d(ascii("ID3\u0004"), null).name, "mp3 id3")
    eq("Matroska (MKV)", d(bytes(0x1A, 0x45, 0xDF, 0xA3), "film.mkv").name, "mkv")
    eq("WebM", d(bytes(0x1A, 0x45, 0xDF, 0xA3), "film.webm").name, "webm")
    eq("RAR", d(ascii("Rar!\u001A\u0007\u0001"), null).name, "rar")
    eq("7-Zip", d(bytes(0x37, 0x7A, 0xBC, 0xAF, 0x27, 0x1C), null).name, "7z")
    eq("Word 97-2003 (DOC)", d(bytes(0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1), "old.doc").name, "doc")
    eq("Excel 97-2003 (XLS)", d(bytes(0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1), "old.xls").name, "xls")
    val epubHead = ascii("PK\u0003\u0004") + ByteArray(26) + ascii("mimetypeapplication/epub+zip")
    eq("EPUB", d(epubHead, "book.zip").name, "epub by mimetype entry")
    eq("Word (DOCX)", d(ascii("PK\u0003\u0004") + ByteArray(26) + ascii("word/document.xml"), "noext").name, "docx by content")
    eq("Excel (XLSX)", d(ascii("PK\u0003\u0004") + ByteArray(26) + ascii("[Content_Types].xml"), "sheet.xlsx").name, "xlsx by name")
    eq("ZIP", d(ascii("PK\u0003\u0004") + ByteArray(26) + ascii("photos/a.jpg"), "a.zip").name, "zip")
    eq("SVG", d(ascii("<?xml version=\"1.0\"?>\n<svg xmlns=\"http://www.w3.org/2000/svg\">"), "x").name, "svg")
    eq(FileFamily.TEXT, FileFormat.detect("hello, wörld\nline two".toByteArray(), "notes", null).family, "utf-8 text")
    eq("CSV", FileFormat.detect("a,b\n1,2".toByteArray(), "data.csv", null).name, "csv")
    eq(FileFamily.OTHER, FileFormat.detect(bytes(0x00, 0x01, 0x02, 0x03), "blob.qqq", null).family, "unknown binary")
    eq("Rich text (RTF)", d(ascii("{\\rtf1\\ansi"), null).name, "rtf")
    ok(FileFormat.knownExtensionCount >= 100, "100+ extensions (${FileFormat.knownExtensionCount})")
    ok(!FileFormat.looksLikeText(bytes(0x48, 0x00, 0x49)), "nul is not text")
    }

    @Test
    fun exportsEscapeAndGuardFormulas() {
    val meta = FileMetadata("a,b.jpg", DetectedFormat(FileFamily.IMAGE, "JPEG", "jpg", "image/jpeg"), 10,
        listOf(MetaField("exif.artist", MetaGroup.CAMERA, "Artist", "=HYPERLINK(\"x\")", editable = true, sensitivity = Sensitivity.PERSONAL),
               MetaField("note", MetaGroup.FILE, "Note", "line1\nline \"2\"")), MetadataAbilities())
    val csv = MetadataExport.csv(meta)
    ok(csv.contains("'=HYPERLINK(\"\"x\"\")"), "csv formula guarded: $csv")
    ok(csv.contains("\"line1\nline \"\"2\"\"\""), "csv quoting")
    val json = MetadataExport.json(meta)
    ok(json.contains("\"value\": \"line1\\nline \\\"2\\\"\""), "json escaping: $json")
    eq(1, meta.search("artist").size, "search by name"); eq(1, meta.search("LINE1").size, "search by value"); eq(2, meta.search(" ").size, "blank search")
    ok(meta.hasPersonal && !meta.hasLocation, "sensitivity flags")
    }

    @Test
    fun jpegLosesItsBlocksButNotItsPicture() {
    fun seg(marker: Int, payload: ByteArray) = bytes(0xFF, marker, (payload.size + 2) shr 8, (payload.size + 2) and 0xFF) + payload
    val scan = bytes(0xFF, 0xDA, 0x00, 0x08, 1, 2, 3, 4, 5, 6, 0x11, 0x22, 0xFF, 0x00, 0x33, 0xFF, 0xD9)
    val jpeg = bytes(0xFF, 0xD8) + seg(0xE0, ascii("JFIF\u0000xx")) + seg(0xE1, ascii("Exif\u0000\u0000GPS-SECRET")) +
        seg(0xE2, ascii("ICC_PROFILE\u0000colour")) + seg(0xE2, ascii("FPXR\u0000x")) + seg(0xED, ascii("Photoshop 3.0 IPTC")) +
        seg(0xFE, ascii("comment by Bob")) + seg(0xDB, ByteArray(65) { 1 }) + seg(0xC0, ByteArray(15) { 2 }) + scan
    val clean = MetadataStrip.jpeg(jpeg)
    val text = String(clean, Charsets.ISO_8859_1)
    ok(!text.contains("GPS-SECRET") && !text.contains("IPTC") && !text.contains("Bob") && !text.contains("FPXR"), "jpeg personal blocks gone")
    ok(text.contains("JFIF") && text.contains("ICC_PROFILE"), "jpeg colour kept")
    ok(clean.copyOfRange(clean.size - scan.size, clean.size).contentEquals(scan), "jpeg picture bytes unchanged")
    eq(jpeg.size - (seg(0xE1, ascii("Exif\u0000\u0000GPS-SECRET")).size + seg(0xE2, ascii("FPXR\u0000x")).size + seg(0xED, ascii("Photoshop 3.0 IPTC")).size + seg(0xFE, ascii("comment by Bob")).size), clean.size, "jpeg size")
    }

    @Test
    fun pngLosesTextChunksButNotItsPicture() {
    fun chunk(type: String, data: ByteArray) = bytes(data.size shr 24, data.size shr 16, data.size shr 8, data.size and 0xFF) + ascii(type) + data + bytes(0, 0, 0, 0)
    val png = bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + chunk("IHDR", ByteArray(13)) + chunk("iCCP", ascii("icc")) +
        chunk("tEXt", ascii("Author\u0000Bob")) + chunk("eXIf", ascii("MM*GPS")) + chunk("pHYs", ByteArray(9)) + chunk("IDAT", ascii("PIXELS")) +
        chunk("tIME", ByteArray(7)) + chunk("IEND", ByteArray(0))
    val pclean = String(MetadataStrip.png(png), Charsets.ISO_8859_1)
    ok(!pclean.contains("tEXt") && !pclean.contains("eXIf") && !pclean.contains("tIME") && !pclean.contains("Bob"), "png text gone")
    ok(pclean.contains("IHDR") && pclean.contains("iCCP") && pclean.contains("pHYs") && pclean.contains("PIXELS") && pclean.contains("IEND"), "png picture kept")
    }

    @Test
    fun webpLosesExifAndXmpAndStaysValid() {
    fun rchunk(type: String, data: ByteArray): ByteArray { val n = data.size; return ascii(type) + bytes(n and 0xFF, (n shr 8) and 0xFF, (n shr 16) and 0xFF, n shr 24) + data + (if (n % 2 == 1) ByteArray(1) else ByteArray(0)) }
    val body = ascii("WEBP") + rchunk("VP8X", bytes(0x0C, 0, 0, 0, 1, 0, 0, 1, 0, 0)) + rchunk("VP8 ", ascii("FRAME")) + rchunk("EXIF", ascii("GPSDATA")) + rchunk("XMP ", ascii("<x>Bob</x>"))
    val webp = ascii("RIFF") + bytes(body.size and 0xFF, (body.size shr 8) and 0xFF, 0, 0) + body
    val w = MetadataStrip.webp(webp)
    val ws = String(w, Charsets.ISO_8859_1)
    ok(!ws.contains("GPSDATA") && !ws.contains("Bob") && ws.contains("FRAME"), "webp chunks")
    eq(0, w[20].toInt() and 0x0C, "webp vp8x flags cleared")
    eq(w.size - 8, (w[4].toInt() and 0xFF) or ((w[5].toInt() and 0xFF) shl 8), "webp riff size")
    }

    @Test
    fun officePropertiesAreReadAndWrittenWithoutTouchingTheDocument() {
    val dir = tmp.newFolder("ooxml")
    val docx = File(dir, "a.docx")
    val documentXml = "<w:document xmlns:w=\"x\"><w:body>Hello</w:body></w:document>".toByteArray()
    val coreXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:dcterms="http://purl.org/dc/terms/"><dc:title>Old title</dc:title><dc:creator>Bob Smith</dc:creator><cp:lastModifiedBy>Bob</cp:lastModifiedBy><dcterms:created>2024-01-01T00:00:00Z</dcterms:created></cp:coreProperties>"""
    val appXml = """<Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties"><Application>Microsoft Office Word</Application><Company>Acme Ltd</Company><Pages>3</Pages></Properties>"""
    val customXml = """<Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/custom-properties" xmlns:vt="v"><property fmtid="x" pid="2" name="Client"><vt:lpwstr>Globex</vt:lpwstr></property></Properties>"""
    ZipOutputStream(docx.outputStream()).use { z ->
        val stored = "stored-bytes".toByteArray()
        z.putNextEntry(ZipEntry("mimetype-like").apply { method = ZipEntry.STORED; size = stored.size.toLong(); compressedSize = size; crc = CRC32().apply { update(stored) }.value }); z.write(stored); z.closeEntry()
        listOf("[Content_Types].xml" to "<Types/>".toByteArray(), "word/document.xml" to documentXml, "docProps/core.xml" to coreXml.toByteArray(),
            "docProps/app.xml" to appXml.toByteArray(), "docProps/custom.xml" to customXml.toByteArray()).forEach { (n, b) -> z.putNextEntry(ZipEntry(n)); z.write(b); z.closeEntry() }
    }
    val (fields, writable) = OfficeMetadata.readOoxml(docx)
    ok(writable, "core present")
    fun v(fs: List<MetaField>, id: String) = fs.firstOrNull { it.id == id }?.value
    eq("Old title", v(fields, "office.title"), "title read"); eq("Bob Smith", v(fields, "office.creator"), "author read")
    eq("Acme Ltd", v(fields, "office.company"), "company read"); eq("3", v(fields, "office.pages"), "pages read"); eq("Globex", v(fields, "office.custom.Client"), "custom read")
    ok(fields.first { it.id == "office.created" }.editable.not(), "created read-only")
    val edited = File(dir, "b.docx")
    OfficeMetadata.writeOoxml(docx, edited, MetadataEdit(values = mapOf("office.title" to "New title", "office.keywords" to "plans, 2026", "office.subject" to null), removePersonal = true))
    val (after, _) = OfficeMetadata.readOoxml(edited)
    eq("New title", v(after, "office.title"), "title written"); eq("plans, 2026", v(after, "office.keywords"), "keywords added")
    eq("", v(after, "office.creator"), "author removed"); eq("", v(after, "office.lastModifiedBy"), "last modified by removed"); eq("", v(after, "office.company"), "company removed")
    eq(null, v(after, "office.custom.Client"), "custom removed with personal"); eq("3", v(after, "office.pages"), "statistics kept")
    eq("2024-01-01T00:00:00Z", v(after, "office.created"), "dates kept unless remove all")
    ZipFile(edited).use { z ->
        ok(z.getInputStream(z.getEntry("word/document.xml")).readBytes().contentEquals(documentXml), "document content unchanged")
        eq(ZipEntry.STORED, z.getEntry("mimetype-like").method, "stored entry kept stored")
        eq("mimetype-like", z.entries().nextElement().name, "entry order kept")
    }
    val bare = File(dir, "c.docx")
    OfficeMetadata.writeOoxml(docx, bare, MetadataEdit(removeAll = true))
    val (none, _) = OfficeMetadata.readOoxml(bare)
    ok(none.filter { it.source == "Office core" }.all { it.value.isEmpty() }, "remove all clears core")
    eq("Microsoft Office Word", v(none, "office.application"), "application kept"); eq("", v(none, "office.company"), "company gone")
    // zip summary
    val zfs = OfficeMetadata.readZip(docx); eq("6", v(zfs, "zip.entries"), "zip count")
    }

    @Test
    fun locationAndPersonalRemovalTakesOutXmpAndIptcButKeepsExif() {
    fun seg(marker: Int, payload: ByteArray) = bytes(0xFF, marker, (payload.size + 2) shr 8, (payload.size + 2) and 0xFF) + payload
    val jpeg = bytes(0xFF, 0xD8) + seg(0xE1, ascii("Exif\u0000\u0000KEEPEXIF")) + seg(0xE1, ascii("http://ns.adobe.com/xap/1.0/\u0000<GPS>")) + seg(0xED, ascii("Photoshop IPTC")) + bytes(0xFF, 0xDA, 0, 4, 1, 2, 9, 9, 0xFF, 0xD9)
    val j = String(MetadataStrip.withoutXmpAndIptc(jpeg), Charsets.ISO_8859_1)
    ok(j.contains("KEEPEXIF") && !j.contains("<GPS>") && !j.contains("IPTC"), "xmp and iptc gone, exif kept")
    }

    @Test
    fun hostileInputsAreRefusedNotFollowed() {
        // A PNG chunk length near 2 GB must not wrap round to a small end.
        fun u32(n: Long) = bytes(((n shr 24) and 0xFF).toInt(), ((n shr 16) and 0xFF).toInt(), ((n shr 8) and 0xFF).toInt(), (n and 0xFF).toInt())
        val png = bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + u32(0x7FFFFFF0L) + ascii("tEXt") + ByteArray(64)
        val refused = runCatching { MetadataStrip.png(png) }.exceptionOrNull()
        ok(refused is IllegalArgumentException, "png length wrap refused: $refused")

        val dir = tmp.newFolder("hostile")
        fun zipWith(name: String, entries: List<Pair<String, ByteArray>>): File = File(dir, name).also { f ->
            ZipOutputStream(f.outputStream()).use { z -> entries.forEach { (n, b) -> z.putNextEntry(ZipEntry(n)); z.write(b); z.closeEntry() } }
        }
        // A DOCTYPE in the properties part is never followed: the part is skipped, the file reports no properties.
        val evil = """<?xml version="1.0"?><!DOCTYPE x [<!ENTITY a "aaaaaaaaaa">]><cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>&a;</dc:title></cp:coreProperties>"""
        val bomb = zipWith("bomb.docx", listOf("docProps/core.xml" to evil.toByteArray()))
        val outcome = runCatching { OfficeMetadata.readOoxml(bomb) }
        ok(outcome.isFailure, "a part with a DOCTYPE is refused")

        // A part over the size limit is not read into memory.
        val huge = zipWith("huge.docx", listOf("docProps/core.xml" to ByteArray(5 * 1024 * 1024) { ' '.code.toByte() }))
        val (fields, writable) = OfficeMetadata.readOoxml(huge)
        ok(!writable && fields.isEmpty(), "an oversized part is skipped")
    }

    @Test
    fun aStoredEntryIsCopiedWithoutBeingReadIntoMemory() {
        val dir = tmp.newFolder("stored")
        val big = ByteArray(3 * 1024 * 1024) { (it % 251).toByte() }
        val src = File(dir, "src.docx")
        ZipOutputStream(src.outputStream()).use { z ->
            z.putNextEntry(ZipEntry("media/video.bin").apply { method = ZipEntry.STORED; size = big.size.toLong(); compressedSize = size; crc = CRC32().apply { update(big) }.value })
            z.write(big); z.closeEntry()
            z.putNextEntry(ZipEntry("docProps/core.xml")); z.write("""<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>T</dc:title></cp:coreProperties>""".toByteArray()); z.closeEntry()
        }
        val out = File(dir, "out.docx")
        OfficeMetadata.writeOoxml(src, out, MetadataEdit(values = mapOf("office.title" to "New")))
        ZipFile(out).use { z ->
            val e = z.getEntry("media/video.bin")
            eq(ZipEntry.STORED, e.method, "still stored")
            ok(z.getInputStream(e).readBytes().contentEquals(big), "bytes identical")
        }
    }
}
