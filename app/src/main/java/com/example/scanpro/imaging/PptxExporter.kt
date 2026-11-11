package com.example.scanpro.imaging

import com.example.scanpro.R
import com.example.scanpro.core.text.userRequire
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Writes a PowerPoint file (.pptx) with one slide per picture (PDF to PowerPoint: each PDF page becomes a slide).
 * Like the Word and Excel writers it is a zip of a few XML parts, so no library is needed. It carries everything
 * PowerPoint insists on (a slide master, a layout and a theme), and each picture is fitted inside the slide without
 * stretching. The slide size follows the first page's shape, so A4 pages make portrait slides.
 */
object PptxExporter {
    /** One slide: a JPEG and its size in pixels (used only for its shape). */
    class Slide(val jpeg: ByteArray, val widthPx: Int, val heightPx: Int)

    /** 10 inches, PowerPoint's usual slide width, in EMU (914400 per inch). */
    internal const val SLIDE_WIDTH_EMU = 9_144_000L
    private const val MIN_EMU = 914_400L
    private const val MAX_EMU = 51_206_400L

    fun export(slides: List<Slide>, destination: File) {
        destination.parentFile?.mkdirs()
        destination.outputStream().use { write(slides, it) }
    }

    fun write(slides: List<Slide>, output: OutputStream) {
        userRequire(slides.isNotEmpty(), R.string.export_err_nothing)
        val (cx, cy) = slideSize(slides.first())
        ZipOutputStream(output).use { zip ->
            zip.part("[Content_Types].xml", contentTypes(slides.size))
            zip.part("_rels/.rels", ROOT_RELS)
            zip.part("ppt/presentation.xml", presentation(slides.size, cx, cy))
            zip.part("ppt/_rels/presentation.xml.rels", presentationRels(slides.size))
            zip.part("ppt/slideMasters/slideMaster1.xml", MASTER)
            zip.part("ppt/slideMasters/_rels/slideMaster1.xml.rels", MASTER_RELS)
            zip.part("ppt/slideLayouts/slideLayout1.xml", LAYOUT)
            zip.part("ppt/slideLayouts/_rels/slideLayout1.xml.rels", LAYOUT_RELS)
            zip.part("ppt/theme/theme1.xml", THEME)
            slides.forEachIndexed { index, slide ->
                val n = index + 1
                zip.part("ppt/slides/slide$n.xml", slideXml(n, slide, cx, cy))
                zip.part("ppt/slides/_rels/slide$n.xml.rels", slideRels(n))
                zip.putNextEntry(ZipEntry("ppt/media/image$n.jpeg"))
                zip.write(slide.jpeg)
                zip.closeEntry()
            }
        }
    }

    /** Slide size in EMU: 10 inches wide, height from the first page's shape, within PowerPoint's limits. */
    internal fun slideSize(first: Slide): Pair<Long, Long> {
        val ratio = first.heightPx.toDouble() / first.widthPx.coerceAtLeast(1)
        return SLIDE_WIDTH_EMU to (SLIDE_WIDTH_EMU * ratio).toLong().coerceIn(MIN_EMU, MAX_EMU)
    }

    /** Where a picture sits on a cx-by-cy slide: as large as fits, centred, never stretched. */
    internal fun fit(slide: Slide, cx: Long, cy: Long): LongArray {
        val scale = minOf(cx.toDouble() / slide.widthPx.coerceAtLeast(1), cy.toDouble() / slide.heightPx.coerceAtLeast(1))
        val w = (slide.widthPx * scale).toLong()
        val h = (slide.heightPx * scale).toLong()
        return longArrayOf((cx - w) / 2, (cy - h) / 2, w, h)
    }

    private fun ZipOutputStream.part(name: String, text: String) {
        putNextEntry(ZipEntry(name))
        write(text.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun contentTypes(count: Int) = buildString {
        append(XML)
        append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        append("""<Default Extension="xml" ContentType="application/xml"/>""")
        append("""<Default Extension="jpeg" ContentType="image/jpeg"/>""")
        append("""<Override PartName="/ppt/presentation.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml"/>""")
        append("""<Override PartName="/ppt/slideMasters/slideMaster1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slideMaster+xml"/>""")
        append("""<Override PartName="/ppt/slideLayouts/slideLayout1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slideLayout+xml"/>""")
        append("""<Override PartName="/ppt/theme/theme1.xml" ContentType="application/vnd.openxmlformats-officedocument.theme+xml"/>""")
        for (n in 1..count) append("""<Override PartName="/ppt/slides/slide$n.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/>""")
        append("</Types>")
    }

    private fun presentation(count: Int, cx: Long, cy: Long) = buildString {
        append(XML)
        append("""<p:presentation $NS saveSubsetFonts="1">""")
        append("""<p:sldMasterIdLst><p:sldMasterId id="2147483648" r:id="rId1"/></p:sldMasterIdLst>""")
        append("<p:sldIdLst>")
        for (n in 1..count) append("""<p:sldId id="${255 + n}" r:id="rId${n + 2}"/>""")
        append("</p:sldIdLst>")
        append("""<p:sldSz cx="$cx" cy="$cy"/><p:notesSz cx="6858000" cy="9144000"/>""")
        append("</p:presentation>")
    }

    private fun presentationRels(count: Int) = buildString {
        append(XML)
        append("""<Relationships xmlns="$PKG_REL">""")
        append("""<Relationship Id="rId1" Type="$REL/slideMaster" Target="slideMasters/slideMaster1.xml"/>""")
        append("""<Relationship Id="rId2" Type="$REL/theme" Target="theme/theme1.xml"/>""")
        for (n in 1..count) append("""<Relationship Id="rId${n + 2}" Type="$REL/slide" Target="slides/slide$n.xml"/>""")
        append("</Relationships>")
    }

    private fun slideXml(n: Int, slide: Slide, cx: Long, cy: Long): String {
        val (x, y, w, h) = fit(slide, cx, cy).let { listOf(it[0], it[1], it[2], it[3]) }
        return XML + """<p:sld $NS><p:cSld><p:spTree>$GROUP_PROPS""" +
            """<p:pic><p:nvPicPr><p:cNvPr id="2" name="Page $n"/><p:cNvPicPr><a:picLocks noChangeAspect="1"/></p:cNvPicPr><p:nvPr/></p:nvPicPr>""" +
            """<p:blipFill><a:blip r:embed="rId2"/><a:stretch><a:fillRect/></a:stretch></p:blipFill>""" +
            """<p:spPr><a:xfrm><a:off x="$x" y="$y"/><a:ext cx="$w" cy="$h"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></p:spPr></p:pic>""" +
            """</p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sld>"""
    }

    private fun slideRels(n: Int) = XML + """<Relationships xmlns="$PKG_REL">""" +
        """<Relationship Id="rId1" Type="$REL/slideLayout" Target="../slideLayouts/slideLayout1.xml"/>""" +
        """<Relationship Id="rId2" Type="$REL/image" Target="../media/image$n.jpeg"/></Relationships>"""

    private const val XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>"""
    private const val REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val PKG_REL = "http://schemas.openxmlformats.org/package/2006/relationships"
    private const val NS = """xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main""""
    private const val GROUP_PROPS = """<p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="0" cy="0"/><a:chOff x="0" y="0"/><a:chExt cx="0" cy="0"/></a:xfrm></p:grpSpPr>"""

    private const val ROOT_RELS = XML + """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
        """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="ppt/presentation.xml"/></Relationships>"""

    private const val MASTER = XML + """<p:sldMaster $NS><p:cSld><p:bg><p:bgRef idx="1001"><a:schemeClr val="bg1"/></p:bgRef></p:bg><p:spTree>$GROUP_PROPS</p:spTree></p:cSld>""" +
        """<p:clrMap bg1="lt1" tx1="dk1" bg2="lt2" tx2="dk2" accent1="accent1" accent2="accent2" accent3="accent3" accent4="accent4" accent5="accent5" accent6="accent6" hlink="hlink" folHlink="folHlink"/>""" +
        """<p:sldLayoutIdLst><p:sldLayoutId id="2147483649" r:id="rId1"/></p:sldLayoutIdLst></p:sldMaster>"""

    private const val MASTER_RELS = XML + """<Relationships xmlns="$PKG_REL">""" +
        """<Relationship Id="rId1" Type="$REL/slideLayout" Target="../slideLayouts/slideLayout1.xml"/>""" +
        """<Relationship Id="rId2" Type="$REL/theme" Target="../theme/theme1.xml"/></Relationships>"""

    private const val LAYOUT = XML + """<p:sldLayout $NS type="blank" preserve="1"><p:cSld name="Blank"><p:spTree>$GROUP_PROPS</p:spTree></p:cSld>""" +
        """<p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sldLayout>"""

    private const val LAYOUT_RELS = XML + """<Relationships xmlns="$PKG_REL"><Relationship Id="rId1" Type="$REL/slideMaster" Target="../slideMasters/slideMaster1.xml"/></Relationships>"""

    private const val SOLID = """<a:solidFill><a:schemeClr val="phClr"/></a:solidFill>"""
    private const val THEME = XML + """<a:theme xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" name="ScanPro"><a:themeElements>""" +
        """<a:clrScheme name="ScanPro"><a:dk1><a:srgbClr val="000000"/></a:dk1><a:lt1><a:srgbClr val="FFFFFF"/></a:lt1><a:dk2><a:srgbClr val="1F2937"/></a:dk2><a:lt2><a:srgbClr val="F3F4F6"/></a:lt2>""" +
        """<a:accent1><a:srgbClr val="3346D3"/></a:accent1><a:accent2><a:srgbClr val="2BD4A4"/></a:accent2><a:accent3><a:srgbClr val="F59E0B"/></a:accent3>""" +
        """<a:accent4><a:srgbClr val="EF4444"/></a:accent4><a:accent5><a:srgbClr val="0EA5E9"/></a:accent5><a:accent6><a:srgbClr val="8B5CF6"/></a:accent6>""" +
        """<a:hlink><a:srgbClr val="1A4FA0"/></a:hlink><a:folHlink><a:srgbClr val="6B21A8"/></a:folHlink></a:clrScheme>""" +
        """<a:fontScheme name="ScanPro"><a:majorFont><a:latin typeface="Calibri"/><a:ea typeface=""/><a:cs typeface=""/></a:majorFont>""" +
        """<a:minorFont><a:latin typeface="Calibri"/><a:ea typeface=""/><a:cs typeface=""/></a:minorFont></a:fontScheme>""" +
        """<a:fmtScheme name="ScanPro"><a:fillStyleLst>$SOLID$SOLID$SOLID</a:fillStyleLst>""" +
        """<a:lnStyleLst><a:ln w="6350">$SOLID</a:ln><a:ln w="12700">$SOLID</a:ln><a:ln w="19050">$SOLID</a:ln></a:lnStyleLst>""" +
        """<a:effectStyleLst><a:effectStyle><a:effectLst/></a:effectStyle><a:effectStyle><a:effectLst/></a:effectStyle><a:effectStyle><a:effectLst/></a:effectStyle></a:effectStyleLst>""" +
        """<a:bgFillStyleLst>$SOLID$SOLID$SOLID</a:bgFillStyleLst></a:fmtScheme></a:themeElements></a:theme>"""
}
