package com.example.scanpro.imaging

import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Writes tables as an Excel workbook (.xlsx), one sheet per page. Like the Word export it is a zip of a few XML
 * parts, so no library is needed; Excel, Google Sheets, WPS and LibreOffice all open it. Plain numbers are
 * stored as numbers so they can be added up; anything else (amounts with a currency sign, dates) stays text.
 */
object XlsxExporter {
    data class Sheet(val name: String, val rows: List<List<String>>)

    fun export(sheets: List<Sheet>, destination: File) {
        destination.parentFile?.mkdirs()
        destination.outputStream().use { write(sheets, it) }
    }

    fun write(sheets: List<Sheet>, output: OutputStream) {
        require(sheets.isNotEmpty()) { "Nothing to export" }
        val names = uniqueNames(sheets.map { it.name })
        ZipOutputStream(output).use { zip ->
            zip.part("[Content_Types].xml", contentTypes(sheets.size))
            zip.part("_rels/.rels", ROOT_RELS)
            zip.part("xl/workbook.xml", workbook(names))
            zip.part("xl/_rels/workbook.xml.rels", workbookRels(sheets.size))
            zip.part("xl/styles.xml", STYLES)
            sheets.forEachIndexed { index, sheet -> zip.part("xl/worksheets/sheet${index + 1}.xml", sheetXml(sheet.rows)) }
        }
    }

    internal fun sheetXml(rows: List<List<String>>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
        rows.forEachIndexed { r, row ->
            append("""<row r="${r + 1}">""")
            row.forEachIndexed { c, value ->
                val text = value.trim()
                if (text.isEmpty()) return@forEachIndexed
                val ref = columnName(c) + (r + 1)
                val number = numberOrNull(text)
                if (number != null) append("""<c r="$ref"><v>$number</v></c>""")
                else append("""<c r="$ref" t="inlineStr"><is><t xml:space="preserve">""").append(DocxExporter.escape(text)).append("</t></is></c>")
            }
            append("</row>")
        }
        append("</sheetData></worksheet>")
    }

    /** 0 → A, 25 → Z, 26 → AA. */
    internal fun columnName(index: Int): String {
        var n = index + 1
        val out = StringBuilder()
        while (n > 0) {
            val rem = (n - 1) % 26
            out.insert(0, 'A' + rem)
            n = (n - 1) / 26
        }
        return out.toString()
    }

    /** "1,234.50" → "1234.50"; text that isn't a plain number (or has a leading zero like a code) → null. */
    internal fun numberOrNull(text: String): String? {
        if (!NUMBER.matches(text)) return null
        val digits = text.replace(",", "")
        if (digits.trimStart('-').let { it.length > 1 && it.startsWith("0") && !it.startsWith("0.") }) return null
        if (digits.count { it.isDigit() } > 15) return null
        return digits
    }

    /** Excel sheet names: at most 31 characters, none of []:*?/\ and no two the same. */
    internal fun uniqueNames(names: List<String>): List<String> {
        val used = mutableSetOf<String>()
        return names.mapIndexed { index, raw ->
            val base = raw.replace(Regex("[\\[\\]:*?/\\\\]"), " ").trim().take(31).ifEmpty { "Sheet ${index + 1}" }
            var name = base
            var n = 2
            while (!used.add(name.lowercase())) name = base.take(27) + " (${n++})"
            name
        }
    }

    private fun workbook(names: List<String>) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""")
        names.forEachIndexed { index, name ->
            append("""<sheet name="${DocxExporter.escape(name)}" sheetId="${index + 1}" r:id="rId${index + 1}"/>""")
        }
        append("</sheets></workbook>")
    }

    private fun workbookRels(count: Int) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        for (i in 1..count) {
            append("""<Relationship Id="rId$i" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet$i.xml"/>""")
        }
        append("""<Relationship Id="rId${count + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
        append("</Relationships>")
    }

    private fun contentTypes(count: Int) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        append("""<Default Extension="xml" ContentType="application/xml"/>""")
        append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
        append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
        for (i in 1..count) {
            append("""<Override PartName="/xl/worksheets/sheet$i.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
        }
        append("</Types>")
    }

    private fun ZipOutputStream.part(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private val NUMBER = Regex("^-?(\\d{1,3}(,\\d{3})+|\\d+)(\\.\\d+)?$")

    private const val ROOT_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""

    private const val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><fonts count="1"><font><sz val="11"/><name val="Calibri"/></font></fonts><fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills><borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/></cellXfs></styleSheet>"""
}
