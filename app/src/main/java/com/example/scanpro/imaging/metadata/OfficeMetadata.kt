package com.example.scanpro.imaging.metadata

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.w3c.dom.Document
import org.w3c.dom.Element

/**
 * Details inside ZIP-based files: Office Open XML (DOCX, XLSX, PPTX) document properties, read and written; OpenDocument
 * and EPUB properties and a plain ZIP's contents, read. Only Java's zip and XML, so it is unit tested on the desktop.
 * Writing copies every other entry byte for byte and rewrites only the property parts, so the document is unchanged.
 */
object OfficeMetadata {
    private const val CP = "http://schemas.openxmlformats.org/package/2006/metadata/core-properties"
    private const val DC = "http://purl.org/dc/elements/1.1/"
    private const val DCTERMS = "http://purl.org/dc/terms/"
    private const val EP = "http://schemas.openxmlformats.org/officeDocument/2006/extended-properties"
    private const val CUSTOM = "http://schemas.openxmlformats.org/officeDocument/2006/custom-properties"
    private const val CORE_PART = "docProps/core.xml"
    private const val APP_PART = "docProps/app.xml"
    private const val CUSTOM_PART = "docProps/custom.xml"
    /** Property parts are a few KB; anything far larger is not read into memory. */
    private const val MAX_PART_BYTES = 4L * 1024 * 1024
    /** A ZIP's contents list shows this many entries. */
    private const val LISTED_ENTRIES = 40

    /** An Office property: its id, its name as Office calls it, its namespace and element, and what it says of a person. */
    private class OfficeProperty(val id: String, val name: String, val ns: String, val local: String, val editable: Boolean, val sensitivity: Sensitivity = Sensitivity.NONE)

    private val CORE = listOf(
        OfficeProperty("office.title", "Title", DC, "title", true),
        OfficeProperty("office.subject", "Subject", DC, "subject", true),
        OfficeProperty("office.creator", "Author", DC, "creator", true, Sensitivity.PERSONAL),
        OfficeProperty("office.keywords", "Keywords", CP, "keywords", true),
        OfficeProperty("office.description", "Comments", DC, "description", true),
        OfficeProperty("office.category", "Category", CP, "category", true),
        OfficeProperty("office.contentStatus", "Status", CP, "contentStatus", true),
        OfficeProperty("office.lastModifiedBy", "Last modified by", CP, "lastModifiedBy", true, Sensitivity.PERSONAL),
        OfficeProperty("office.revision", "Revision", CP, "revision", false),
        OfficeProperty("office.created", "Created", DCTERMS, "created", false),
        OfficeProperty("office.modified", "Modified", DCTERMS, "modified", false),
        OfficeProperty("office.lastPrinted", "Last printed", CP, "lastPrinted", false),
        OfficeProperty("office.language", "Language", DC, "language", false),
        OfficeProperty("office.identifier", "Identifier", DC, "identifier", false),
        OfficeProperty("office.version", "Version", CP, "version", false),
    )
    private val APP = listOf(
        OfficeProperty("office.company", "Company", EP, "Company", true, Sensitivity.PERSONAL),
        OfficeProperty("office.manager", "Manager", EP, "Manager", true, Sensitivity.PERSONAL),
        OfficeProperty("office.application", "Application", EP, "Application", false),
        OfficeProperty("office.appVersion", "Application version", EP, "AppVersion", false),
        OfficeProperty("office.template", "Template", EP, "Template", false, Sensitivity.PERSONAL),
        OfficeProperty("office.hyperlinkBase", "Hyperlink base", EP, "HyperlinkBase", false, Sensitivity.PERSONAL),
        OfficeProperty("office.totalTime", "Editing time (minutes)", EP, "TotalTime", false, Sensitivity.PERSONAL),
        OfficeProperty("office.pages", "Pages", EP, "Pages", false),
        OfficeProperty("office.words", "Words", EP, "Words", false),
        OfficeProperty("office.characters", "Characters", EP, "Characters", false),
        OfficeProperty("office.lines", "Lines", EP, "Lines", false),
        OfficeProperty("office.paragraphs", "Paragraphs", EP, "Paragraphs", false),
        OfficeProperty("office.slides", "Slides", EP, "Slides", false),
        OfficeProperty("office.notes", "Notes", EP, "Notes", false),
        OfficeProperty("office.hiddenSlides", "Hidden slides", EP, "HiddenSlides", false),
    )
    /** Taken out by "Remove all": everything a person or an organisation could be traced by, and the history. */
    private val APP_REMOVED_BY_ALL = setOf("Company", "Manager", "Template", "HyperlinkBase", "TotalTime")

    /** Ids of the Office properties that can be written. */
    val EDITABLE_IDS: Set<String> = (CORE + APP).filter { it.editable }.map { it.id }.toSet()

    private fun secureFactory() = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        isExpandEntityReferences = false
        runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
    }

    /**
     * Parses a property part. Parts of Office, OpenDocument and EPUB files never have a DOCTYPE or an entity, so one is
     * refused outright: Android's XML parser may ignore the feature switches above, and entities are how a "billion
     * laughs" part blows up in memory.
     */
    private fun parse(bytes: ByteArray): Document {
        val head = String(bytes, 0, minOf(bytes.size, 4096), Charsets.ISO_8859_1)
        require(!head.contains("<!DOCTYPE", ignoreCase = true) && !head.contains("<!ENTITY", ignoreCase = true)) { "Unexpected DOCTYPE" }
        return secureFactory().newDocumentBuilder().parse(bytes.inputStream())
    }

    /**
     * A part's bytes, or null when it is missing or larger than [MAX_PART_BYTES]. The size in the zip's directory is
     * only a claim, so the read itself is capped: a part that says it is small and unpacks to gigabytes is refused too.
     */
    private fun ZipFile.part(name: String): ByteArray? {
        val entry = getEntry(name) ?: return null
        if (entry.size > MAX_PART_BYTES) return null
        // InputStream.readNBytes needs Android 13; this reads in pieces and stops one byte past the limit.
        val out = ByteArrayOutputStream()
        getInputStream(entry).use { input ->
            val buffer = ByteArray(16 * 1024)
            while (out.size() <= MAX_PART_BYTES) {
                val n = input.read(buffer)
                if (n < 0) break
                out.write(buffer, 0, n)
            }
        }
        return if (out.size() > MAX_PART_BYTES) null else out.toByteArray()
    }

    private fun Document.first(ns: String, local: String): Element? = getElementsByTagNameNS(ns, local).item(0) as? Element

    /** Office Open XML properties: core, extended (app) and custom. True in the pair when they can be written. */
    fun readOoxml(file: File): Pair<List<MetaField>, Boolean> = ZipFile(file).use { zip ->
        val fields = mutableListOf<MetaField>()
        val core = zip.part(CORE_PART)?.let(::parse)
        core?.let { doc ->
            CORE.forEach { p ->
                val value = doc.first(p.ns, p.local)?.textContent?.trim().orEmpty()
                if (value.isNotEmpty() || p.editable) fields += MetaField(p.id, MetaGroup.DOCUMENT, p.name, value, editable = p.editable, sensitivity = if (value.isEmpty()) Sensitivity.NONE else p.sensitivity, source = "Office core")
            }
        }
        zip.part(APP_PART)?.let(::parse)?.let { doc ->
            APP.forEach { p ->
                val value = doc.first(p.ns, p.local)?.textContent?.trim().orEmpty()
                if (value.isNotEmpty() || p.editable) fields += MetaField(p.id, MetaGroup.DOCUMENT, p.name, value, editable = p.editable && core != null, sensitivity = if (value.isEmpty()) Sensitivity.NONE else p.sensitivity, source = "Office app")
            }
        }
        zip.part(CUSTOM_PART)?.let(::parse)?.let { doc ->
            val props = doc.getElementsByTagNameNS(CUSTOM, "property")
            for (i in 0 until props.length) {
                val e = props.item(i) as? Element ?: continue
                val name = e.getAttribute("name")
                fields += MetaField("office.custom.$name", MetaGroup.DOCUMENT, name, e.textContent.trim(), source = "Office custom", sensitivity = Sensitivity.PERSONAL)
            }
        }
        fields to (core != null)
    }

    /**
     * Writes [source] to [target] with [edit] applied to its Office properties. Fails if the file has no core properties
     * part to write into (the screen offers no editing then).
     */
    fun writeOoxml(source: File, target: File, edit: MetadataEdit) {
        ZipFile(source).use { zip ->
            val core = requireNotNull(zip.part(CORE_PART)?.let(::parse)) { "No document properties" }
            val app = zip.part(APP_PART)?.let(::parse)
            val custom = zip.part(CUSTOM_PART)?.let(::parse)
            fun apply(doc: Document, props: List<OfficeProperty>) {
                val root = doc.documentElement
                props.forEach { p ->
                    val existing = doc.first(p.ns, p.local)
                    val remove = edit.removeAll && (p.ns != EP || p.local in APP_REMOVED_BY_ALL) ||
                        edit.removePersonal && p.sensitivity == Sensitivity.PERSONAL
                    when {
                        remove -> existing?.let { root.removeChild(it) }
                        p.id in edit.values && p.editable -> {
                            val value = edit.values[p.id]
                            if (value.isNullOrEmpty()) existing?.let { root.removeChild(it) }
                            else (existing ?: doc.createElementNS(p.ns, prefixFor(p.ns) + p.local).also { root.appendChild(it) }).textContent = value
                        }
                    }
                }
            }
            apply(core, CORE)
            app?.let { apply(it, APP) }
            if (edit.removeAll || edit.removePersonal) custom?.let { doc ->
                val props = doc.getElementsByTagNameNS(CUSTOM, "property")
                (0 until props.length).mapNotNull { props.item(it) }.forEach { it.parentNode.removeChild(it) }
            }
            val replaced = buildMap {
                put(CORE_PART, serialise(core))
                app?.let { put(APP_PART, serialise(it)) }
                custom?.let { put(CUSTOM_PART, serialise(it)) }
            }
            copyZip(zip, target, replaced)
        }
    }

    private fun prefixFor(ns: String) = when (ns) {
        DC -> "dc:"
        DCTERMS -> "dcterms:"
        CP -> "cp:"
        else -> ""
    }

    private fun serialise(doc: Document): ByteArray {
        val out = ByteArrayOutputStream()
        TransformerFactory.newInstance().newTransformer().apply {
            setOutputProperty(OutputKeys.ENCODING, "UTF-8")
            setOutputProperty(OutputKeys.STANDALONE, "yes")
        }.transform(DOMSource(doc), StreamResult(out))
        return out.toByteArray()
    }

    /** Copies [zip] to [target] entry by entry, in order and with each entry's own compression; [replaced] by name. */
    internal fun copyZip(zip: ZipFile, target: File, replaced: Map<String, ByteArray>) {
        ZipOutputStream(FileOutputStream(target).buffered(64 * 1024)).use { out ->
            for (entry in zip.entries().asSequence()) {
                val bytes = replaced[entry.name]
                val copy = ZipEntry(entry.name).apply { time = entry.time; comment = entry.comment }
                if (entry.method == ZipEntry.STORED) {
                    // Stored entries (EPUB's mimetype, pictures kept uncompressed) must say their size and checksum up
                    // first. A replaced one is small and measured; any other says so in the source's own directory,
                    // so it is streamed, not read into memory (a stored video in a PPTX can be hundreds of MB). If the
                    // directory lied, the writer refuses the entry rather than writing a damaged file.
                    if (bytes != null) {
                        copy.size = bytes.size.toLong()
                        copy.compressedSize = bytes.size.toLong()
                        copy.crc = CRC32().apply { update(bytes) }.value
                    } else {
                        copy.size = entry.size
                        copy.compressedSize = entry.size
                        copy.crc = entry.crc
                    }
                    copy.method = ZipEntry.STORED
                    out.putNextEntry(copy)
                    if (bytes != null) out.write(bytes) else zip.getInputStream(entry).use { it.copyTo(out, 64 * 1024) }
                } else {
                    copy.method = ZipEntry.DEFLATED
                    out.putNextEntry(copy)
                    if (bytes != null) out.write(bytes) else zip.getInputStream(entry).use { it.copyTo(out, 64 * 1024) }
                }
                out.closeEntry()
            }
            zip.comment?.let { out.setComment(it) }
        }
    }

    /** OpenDocument (ODT, ODS, ODP): the meta.xml properties and statistics. Read only. */
    fun readOpenDocument(file: File): List<MetaField> = ZipFile(file).use { zip ->
        val doc = zip.part("meta.xml")?.let(::parse) ?: return emptyList()
        val fields = mutableListOf<MetaField>()
        val meta = "urn:oasis:names:tc:opendocument:xmlns:meta:1.0"
        fun add(id: String, name: String, ns: String, local: String, sensitivity: Sensitivity = Sensitivity.NONE) {
            val value = doc.first(ns, local)?.textContent?.trim().orEmpty()
            if (value.isNotEmpty()) fields += MetaField(id, MetaGroup.DOCUMENT, name, value, sensitivity = sensitivity, source = "OpenDocument")
        }
        add("odf.title", "Title", DC, "title")
        add("odf.subject", "Subject", DC, "subject")
        add("odf.description", "Comments", DC, "description")
        add("odf.initialCreator", "Author", meta, "initial-creator", Sensitivity.PERSONAL)
        add("odf.creator", "Last modified by", DC, "creator", Sensitivity.PERSONAL)
        add("odf.created", "Created", meta, "creation-date")
        add("odf.modified", "Modified", DC, "date")
        add("odf.generator", "Application", meta, "generator")
        add("odf.language", "Language", DC, "language")
        add("odf.editingDuration", "Editing time", meta, "editing-duration", Sensitivity.PERSONAL)
        val keywords = doc.getElementsByTagNameNS(meta, "keyword")
        if (keywords.length > 0) fields += MetaField("odf.keywords", MetaGroup.DOCUMENT, "Keywords", (0 until keywords.length).joinToString(", ") { keywords.item(it).textContent.trim() }, source = "OpenDocument")
        (doc.first(meta, "document-statistic"))?.let { stats ->
            for (i in 0 until stats.attributes.length) {
                val a = stats.attributes.item(i)
                fields += MetaField("odf.stat.${a.localName}", MetaGroup.CONTENTS, a.localName.replace('-', ' ').replaceFirstChar { it.uppercase() }, a.nodeValue, source = "OpenDocument")
            }
        }
        fields
    }

    /** EPUB: the book's details from its package file (title, author, publisher, language, date...). Read only. */
    fun readEpub(file: File): List<MetaField> = ZipFile(file).use { zip ->
        val container = zip.part("META-INF/container.xml")?.let(::parse) ?: return emptyList()
        val rootFile = (container.getElementsByTagName("rootfile").item(0) as? Element)?.getAttribute("full-path") ?: return emptyList()
        val opf = zip.part(rootFile)?.let(::parse) ?: return emptyList()
        val names = listOf(
            "title" to "Title", "creator" to "Author", "contributor" to "Contributor", "publisher" to "Publisher",
            "language" to "Language", "date" to "Date", "identifier" to "Identifier", "subject" to "Subject",
            "description" to "Description", "rights" to "Rights", "source" to "Source"
        )
        names.flatMap { (local, name) ->
            val nodes = opf.getElementsByTagNameNS(DC, local)
            (0 until nodes.length).map { nodes.item(it).textContent.trim() }.filter { it.isNotEmpty() }.mapIndexed { i, value ->
                MetaField("epub.$local${if (i > 0) ".$i" else ""}", MetaGroup.DOCUMENT, name, value.take(2000),
                    sensitivity = if (local == "creator" || local == "contributor") Sensitivity.PERSONAL else Sensitivity.NONE, source = "EPUB")
            }
        }
    }

    /** A ZIP's contents: how many files, their total size, its comment, and the first entries. Read only. */
    fun readZip(file: File): List<MetaField> = ZipFile(file).use { zip ->
        // A crafted zip can list millions of entries: counted as they come, and only the first few are kept.
        var folders = 0
        var fileCount = 0
        var unpacked = 0L
        val firstFiles = ArrayList<ZipEntry>(LISTED_ENTRIES)
        for (e in zip.entries()) {
            if (e.isDirectory) { folders++; continue }
            fileCount++
            unpacked += e.size.coerceAtLeast(0)
            if (firstFiles.size < LISTED_ENTRIES) firstFiles += e
        }
        val fields = mutableListOf(
            MetaField("zip.entries", MetaGroup.CONTENTS, "Files", fileCount.toString(), source = "ZIP"),
            MetaField("zip.folders", MetaGroup.CONTENTS, "Folders", folders.toString(), source = "ZIP"),
            MetaField("zip.unpacked", MetaGroup.CONTENTS, "Size unpacked (bytes)", unpacked.toString(), source = "ZIP"),
        )
        zip.comment?.takeIf { it.isNotBlank() }?.let { fields += MetaField("zip.comment", MetaGroup.CONTENTS, "Comment", it, source = "ZIP") }
        firstFiles.forEachIndexed { i, e ->
            fields += MetaField("zip.entry.$i", MetaGroup.CONTENTS, e.name, "${e.size.coerceAtLeast(0)} bytes", source = "ZIP contents")
        }
        fields
    }
}
