package com.example.scanpro.imaging.metadata

/** Where a field is shown; also the section heading on the screen. */
enum class MetaGroup { FILE, DOCUMENT, IMAGE, CAMERA, LOCATION, AUDIO, VIDEO, CONTENTS, TAGS }

/** What a field says about a person, so the privacy actions know what to take out. */
enum class Sensitivity { NONE, PERSONAL, LOCATION }

/**
 * One detail of a file. [id] is stable and not translated (it is the key in exports and edits); [label] is a string
 * resource for the details the app names itself, 0 for a tag named by its format ([name] is shown then: "Exposure
 * Time", "TPE1"). [editable] when the app can write it back for this file's format.
 */
data class MetaField(
    val id: String,
    val group: MetaGroup,
    val name: String,
    val value: String,
    val label: Int = 0,
    val editable: Boolean = false,
    val sensitivity: Sensitivity = Sensitivity.NONE,
    /** Under which heading a format tag belongs ("Exif IFD0", "IPTC", "XMP", "ID3v2"); empty for the app's own fields. */
    val source: String = "",
)

/** What can be done to this file's details as a whole. */
data class MetadataAbilities(
    /** Fields marked editable can be changed and saved. */
    val canEdit: Boolean = false,
    val canRemoveLocation: Boolean = false,
    val canRemovePersonal: Boolean = false,
    val canRemoveAll: Boolean = false,
    /** Why nothing can be changed, as a string resource; 0 when something can. */
    val readOnlyReason: Int = 0,
)

/** Everything read from one file. */
data class FileMetadata(
    val fileName: String,
    val format: DetectedFormat,
    val sizeBytes: Long,
    val fields: List<MetaField>,
    val abilities: MetadataAbilities,
) {
    val hasLocation: Boolean get() = fields.any { it.sensitivity == Sensitivity.LOCATION }
    val hasPersonal: Boolean get() = fields.any { it.sensitivity == Sensitivity.PERSONAL }

    /** The fields whose name, source or value holds [query] (any case); all of them for a blank query. */
    fun search(query: String): List<MetaField> {
        val q = query.trim()
        if (q.isEmpty()) return fields
        return fields.filter { it.name.contains(q, true) || it.value.contains(q, true) || it.source.contains(q, true) || it.id.contains(q, true) }
    }
}

/** A change to a file's details: [values] by field id (null removes the field), and the privacy actions. */
data class MetadataEdit(
    val values: Map<String, String?> = emptyMap(),
    val removeLocation: Boolean = false,
    val removePersonal: Boolean = false,
    val removeAll: Boolean = false,
) {
    val isEmpty: Boolean get() = values.isEmpty() && !removeLocation && !removePersonal && !removeAll
}

/** The details as JSON and CSV, for "Export". Pure Kotlin: no JSON library, so it is unit tested on the desktop. */
object MetadataExport {
    fun json(meta: FileMetadata): String = buildString {
        append("{\n")
        append("  \"file\": ").append(quote(meta.fileName)).append(",\n")
        append("  \"format\": ").append(quote(meta.format.name)).append(",\n")
        append("  \"mimeType\": ").append(quote(meta.format.mime)).append(",\n")
        append("  \"sizeBytes\": ").append(meta.sizeBytes).append(",\n")
        append("  \"fields\": [")
        meta.fields.forEachIndexed { i, f ->
            append(if (i == 0) "\n" else ",\n")
            append("    {\"group\": ").append(quote(f.group.name.lowercase()))
            append(", \"source\": ").append(quote(f.source))
            append(", \"id\": ").append(quote(f.id))
            append(", \"name\": ").append(quote(f.name))
            append(", \"value\": ").append(quote(f.value))
            append(", \"editable\": ").append(f.editable)
            append("}")
        }
        append(if (meta.fields.isEmpty()) "]\n" else "\n  ]\n")
        append("}\n")
    }

    fun csv(meta: FileMetadata): String = buildString {
        append("group,source,id,name,value,editable\r\n")
        meta.fields.forEach { f ->
            append(listOf(f.group.name.lowercase(), f.source, f.id, f.name, f.value, f.editable.toString()).joinToString(",") { cell(it) })
            append("\r\n")
        }
    }

    internal fun quote(text: String): String = buildString {
        append('"')
        for (c in text) {
            when {
                c == '"' -> append("\\\"")
                c == '\\' -> append("\\\\")
                c == '\n' -> append("\\n")
                c == '\r' -> append("\\r")
                c == '\t' -> append("\\t")
                c < ' ' -> append("\\u%04x".format(c.code))
                else -> append(c)
            }
        }
        append('"')
    }

    /**
     * One CSV cell: quoted when it holds a comma, quote or line break; a cell starting with = + - or @ gets a leading
     * apostrophe, so a spreadsheet does not run a tag value as a formula (CSV injection).
     */
    internal fun cell(text: String): String {
        val safe = if (text.isNotEmpty() && text[0] in "=+-@") "'$text" else text
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }
}
