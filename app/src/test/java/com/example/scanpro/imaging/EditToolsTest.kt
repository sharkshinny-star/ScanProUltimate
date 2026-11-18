package com.example.scanpro.imaging

import android.app.Application
import android.content.Context
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.scanpro.feature.tools.passwordBits
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSObject
import com.tom_roush.pdfbox.cos.COSStream
import com.tom_roush.pdfbox.cos.COSString
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotation
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationSquareCircle
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationText
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitDestination
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.util.Matrix
import java.io.File
import java.util.Collections
import java.util.IdentityHashMap
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The edit and secure tools (F194): Redact's search (case, accents, line breaks, hyphenation, rotated pages, comments)
 * and, above all, that a redacted file holds nothing of the redacted words anywhere, not in the text, not in an unused
 * resource, a link's target page or a comment; Flatten's preview; Header and footer's shrink-to-fit; the password
 * strength sum.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class EditToolsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var dir: File

    @Before
    fun setUp() {
        PDFBoxResourceLoader.init(context)
        dir = File(context.cacheDir, "edit-tools-test").apply { deleteRecursively(); mkdirs() }
    }

    private fun line(doc: PDDocument, page: PDPage, text: String, x: Float, y: Float, append: Boolean = true) {
        val mode = if (append) PDPageContentStream.AppendMode.APPEND else PDPageContentStream.AppendMode.OVERWRITE
        PDPageContentStream(doc, page, mode, true).use {
            it.beginText(); it.setFont(PDType1Font.HELVETICA, 12f); it.newLineAtOffset(x, y); it.showText(text); it.endText()
        }
    }

    // ---- Search ----

    @Test
    fun searchIgnoresCaseAndAccentsAndFindsWordsOverLineBreaks() {
        PDDocument().use { doc ->
            val page = PDPage(PDRectangle(400f, 300f)).also { doc.addPage(it) }
            line(doc, page, "Contact JOSÉ Pérez today", 40f, 250f)
            line(doc, page, "Our client Alice", 40f, 220f)
            line(doc, page, "Smith sent the confiden-", 40f, 200f)
            line(doc, page, "tial papers.", 40f, 180f)
            val index = PdfRedactTools.buildIndex(doc)
            assertTrue(index.hasText)

            assertEquals(1, PdfRedactTools.search(index, listOf("jose perez"), matchCase = false).occurrences)
            assertEquals(1, PdfRedactTools.search(index, listOf("JOSÉ"), matchCase = true).occurrences)
            assertEquals(0, PdfRedactTools.search(index, listOf("jose"), matchCase = true).occurrences)

            // A name broken over two lines is one match with a box on each line, not one box over both lines.
            val name = PdfRedactTools.search(index, listOf("alice smith"), matchCase = false)
            assertEquals(1, name.occurrences)
            assertEquals(2, name.matches.size)
            val (a, b) = name.matches.map { it.rect }.sortedBy { it.top }
            assertTrue("one box per line", b.top >= a.bottom - 4f)

            // A hyphenated word is found whole.
            val word = PdfRedactTools.search(index, listOf("Confidential"), matchCase = false)
            assertEquals(1, word.occurrences)
            assertEquals(2, word.matches.size)

            // Each term counts on its own, and a typo shows as zero.
            val counts = PdfRedactTools.search(index, listOf("alice", "nobody", "alice"), matchCase = false)
            assertEquals(listOf(1, 0), counts.perTerm)
            assertEquals(1, counts.pages)
        }
    }

    @Test
    fun boxesCoverDescendersAndLandOnRotatedPagesWhereTheWordIsSeen() {
        PDDocument().use { doc ->
            // Upright page: the box reaches below the baseline (g, p, y) as well as up to the capitals.
            val upright = PDPage(PDRectangle(400f, 300f)).also { doc.addPage(it) }
            line(doc, upright, "Typography", 40f, 200f)
            // A page turned 90 degrees, with its text written upwards so it reads upright on screen.
            val turned = PDPage(PDRectangle(400f, 300f)).apply { rotation = 90 }.also { doc.addPage(it) }
            PDPageContentStream(doc, turned).use {
                it.beginText(); it.setFont(PDType1Font.HELVETICA, 14f)
                it.setTextMatrix(Matrix(0f, 1f, -1f, 0f, 100f, 50f))
                it.showText("Secret"); it.endText()
            }
            val index = PdfRedactTools.buildIndex(doc)

            val box = PdfRedactTools.search(index, listOf("typography"), false).boxesOn(0).single()
            // Baseline 100 pt from the top of the 300 pt page; descenders go about 3 pt below it.
            assertTrue("bottom ${box.bottom}", box.bottom >= 102f)
            assertTrue("top ${box.top}", box.top <= 92f)

            // Shown turned, the page is 300 wide and 400 tall; the word starts 50 pt from the left with its baseline
            // 100 pt down (the page's x becomes the display's y).
            val seen = PdfRedactTools.search(index, listOf("secret"), false).boxesOn(1).single()
            assertTrue("left ${seen.left}", seen.left in 44f..52f)
            assertTrue("top ${seen.top}", seen.top in 80f..95f)
            assertTrue("bottom ${seen.bottom}", seen.bottom in 100f..110f)
            assertTrue("right ${seen.right}", seen.right in 90f..140f)
        }
    }

    // ---- The redacted file ----

    /**
     * Page 1 draws a form that holds the words; page 2 shares page 1's resource list (so the form is listed there too)
     * and links to page 1; page 3 has a sticky note with the name in it.
     */
    private fun leakyDocument(): File {
        val file = File(dir, "leaky.pdf")
        PDDocument().use { doc ->
            val form = PDAppearanceStream(doc).apply { bBox = PDRectangle(0f, 0f, 300f, 40f); resources = PDResources() }
            PDPageContentStream(doc, form).use {
                it.beginText(); it.setFont(PDType1Font.HELVETICA, 12f); it.newLineAtOffset(10f, 10f); it.showText("Alice Secret"); it.endText()
            }
            val shared = PDResources()
            val first = PDPage(PDRectangle(400f, 300f)).apply { resources = shared }.also { doc.addPage(it) }
            val second = PDPage(PDRectangle(400f, 300f)).apply { resources = shared }.also { doc.addPage(it) }
            val third = PDPage(PDRectangle(400f, 300f)).also { doc.addPage(it) }
            PDPageContentStream(doc, first).use {
                it.saveGraphicsState(); it.transform(Matrix.getTranslateInstance(40f, 200f)); it.drawForm(form); it.restoreGraphicsState()
                it.beginText(); it.setFont(PDType1Font.HELVETICA, 12f); it.newLineAtOffset(40f, 150f); it.showText("Invoice"); it.endText()
            }
            // Page 2 never draws the form, but its resource list (shared with page 1) names it.
            PDPageContentStream(doc, second).use {
                it.beginText(); it.setFont(PDType1Font.HELVETICA, 12f); it.newLineAtOffset(40f, 200f); it.showText("Nothing secret here."); it.endText()
            }
            assertTrue(second.resources.xObjectNames.iterator().hasNext())
            val link = PDAnnotationLink().apply {
                rectangle = PDRectangle(40f, 100f, 100f, 20f)
                page = second
                destination = PDPageFitDestination().apply { page = first }
            }
            second.annotations = listOf<PDAnnotation>(link)
            line(doc, third, "Third page", 40f, 200f)
            val note = PDAnnotationText().apply {
                rectangle = PDRectangle(300f, 250f, 20f, 20f)
                contents = "Call Alice back"
                page = third
            }
            third.annotations = listOf<PDAnnotation>(note)
            doc.save(file)
        }
        return file
    }

    /** Every string and every decoded stream reachable from the file's trailer: everything a reader could dig out. */
    private fun everythingIn(doc: PDDocument): String {
        val seen: MutableSet<COSBase> = Collections.newSetFromMap(IdentityHashMap())
        val out = StringBuilder()
        val stack = ArrayDeque<COSBase>()
        stack.addLast(doc.document.trailer)
        while (stack.isNotEmpty()) {
            var node: COSBase = stack.removeLast()
            if (node is COSObject) node = node.getObject() ?: continue
            if (!seen.add(node)) continue
            when (node) {
                is COSString -> out.append(node.string).append('\n')
                is COSStream -> {
                    // Pictures cannot hold text strings; everything else is decoded and read.
                    if (node.getCOSName(COSName.SUBTYPE) != COSName.IMAGE) {
                        out.append(String(node.createInputStream().use { it.readBytes() }, Charsets.ISO_8859_1)).append('\n')
                    }
                    node.values.forEach { stack.addLast(it) }
                }
                is COSDictionary -> node.values.forEach { stack.addLast(it) }
                is COSArray -> for (i in 0 until node.size()) stack.addLast(node.get(i))
                else -> Unit
            }
        }
        return out.toString()
    }

    @Test
    fun aRedactedFileHoldsNothingOfTheWordsAnywhere() = runBlocking {
        val out = File(dir, "redacted.pdf")
        PDDocument.load(leakyDocument()).use { doc ->
            // The leak is real before redaction: the words are in the file.
            assertTrue(everythingIn(doc).contains("Alice"))
            val found = PdfRedactTools.search(PdfRedactTools.buildIndex(doc), listOf("alice"), matchCase = false)
            // Once in page 1's form, once in page 3's note.
            assertEquals(2, found.occurrences)
            assertEquals(2, found.pages)
            PdfRedactTools.writeRedacted(
                source = doc,
                matches = found.matches,
                pageSize = { 400f to 300f },
                draw = { _, target -> target.eraseColor(Color.WHITE) },
                outputFile = out
            )
        }
        PDDocument.load(out).use { doc ->
            assertEquals(3, doc.numberOfPages)
            val text = PDFTextStripper().getText(doc)
            assertFalse(text.contains("alice", ignoreCase = true))
            assertTrue("pages without a match keep their text", text.contains("Nothing secret here."))
            // Not in an unused form, not behind the link's target, not in the note: nowhere in the file.
            assertFalse(everythingIn(doc).contains("alice", ignoreCase = true))
            // The link on page 2 still works and opens the new page 1.
            val link = doc.getPage(1).annotations.single() as PDAnnotationLink
            val target = (link.destination as PDPageDestination).page
            assertSame(doc.getPage(0).cosObject, target.cosObject)
            // The note's page is a picture now, with no comments left on it.
            assertTrue(doc.getPage(2).annotations.isEmpty())
        }
    }

    @Test
    fun aPageWithInheritedResourcesKeepsItsFontWhenCopied() = runBlocking {
        val source = File(dir, "inherited.pdf")
        PDDocument().use { doc ->
            val page = PDPage(PDRectangle(400f, 300f)).also { doc.addPage(it) }
            line(doc, page, "Kept text", 40f, 200f, append = false)
            // Move the page's resources up to the page tree, where only inheritance finds them.
            val resources = page.cosObject.getDictionaryObject(COSName.RESOURCES)
            page.cosObject.removeItem(COSName.RESOURCES)
            doc.pages.cosObject.setItem(COSName.RESOURCES, resources)
            val other = PDPage(PDRectangle(400f, 300f)).also { doc.addPage(it) }
            line(doc, other, "Remove me", 40f, 200f)
            doc.save(source)
        }
        val out = File(dir, "inherited-out.pdf")
        PDDocument.load(source).use { doc ->
            val found = PdfRedactTools.search(PdfRedactTools.buildIndex(doc), listOf("remove"), false)
            PdfRedactTools.writeRedacted(doc, found.matches, { 400f to 300f }, { _, t -> t.eraseColor(Color.WHITE) }, out)
        }
        PDDocument.load(out).use { doc ->
            // Inherited resources do not travel with a copied page: they must now be on the page itself.
            assertTrue(doc.getPage(0).cosObject.containsKey(COSName.RESOURCES))
            val first = PDFTextStripper().apply { startPage = 1; endPage = 1 }.getText(doc)
            assertTrue(first, first.contains("Kept text"))
        }
    }

    // ---- Flatten preview ----

    @Test
    fun flattenPreviewCountsWhatWillBeDrawnIn() {
        PDDocument().use { doc ->
            val page = PDPage(PDRectangle.A4).also { doc.addPage(it) }
            val appearance = PDAppearanceStream(doc).apply { bBox = PDRectangle(0f, 0f, 50f, 50f); resources = PDResources() }
            PDPageContentStream(doc, appearance).use { it.addRect(0f, 0f, 50f, 50f); it.fill() }
            val square = PDAnnotationSquareCircle(PDAnnotationSquareCircle.SUB_TYPE_SQUARE).apply {
                rectangle = PDRectangle(300f, 300f, 50f, 50f)
                this.appearance = PDAppearanceDictionary().apply { setNormalAppearance(appearance) }
            }
            val link = PDAnnotationLink().apply { rectangle = PDRectangle(10f, 10f, 50f, 20f) }
            page.annotations = listOf<PDAnnotation>(square, link)
            val preview = PdfCleanupTools.flattenPreview(doc)
            assertEquals(0, preview.fields)
            assertEquals(1, preview.annotations)
            assertEquals(1, preview.links)
            // The square, seen from the top of the 842 pt page: 300..350 across, 492..542 down.
            val area = preview.firstPageAreas.single()
            assertEquals(300f, area.left, 0.5f)
            assertEquals(842f - 350f, area.top, 0.5f)
        }
    }

    // ---- Header and footer, password strength ----

    @Test
    fun headerTextShrinksToFitBetweenTheMargins() {
        assertEquals(10f, PdfHeaderFooterTools.fittedFontSize(10f, 100f, 300f), 0.001f)
        assertEquals(7.5f, PdfHeaderFooterTools.fittedFontSize(10f, 400f, 300f), 0.001f)
        assertEquals(PdfHeaderFooterTools.MIN_FONT_SIZE_PT, PdfHeaderFooterTools.fittedFontSize(10f, 6000f, 300f), 0.001f)
    }

    @Test
    fun displayRectanglesTurnWithThePage() {
        // A 300 x 400 crop box: a rectangle 10..60 across and 20..40 up.
        val upright = PdfCropTools.userRectToDisplay(10f, 20f, 60f, 40f, 0, 300f, 400f)
        assertEquals(listOf(10f, 360f, 60f, 380f), listOf(upright.left, upright.top, upright.right, upright.bottom))
        // Turned 90: the page's x runs down the display, its y across.
        val turned = PdfCropTools.userRectToDisplay(10f, 20f, 60f, 40f, 90, 300f, 400f)
        assertEquals(listOf(20f, 10f, 40f, 60f), listOf(turned.left, turned.top, turned.right, turned.bottom))
        // And back: display to page space and to the display again is the same rectangle.
        for (rotation in listOf(0, 90, 180, 270)) {
            val shape = PdfCropTools.PageShape(PDRectangle(0f, 0f, 300f, 400f), rotation)
            val user = PdfCropTools.displayRectToUserSpace(shape, 15f, 25f, 75f, 95f)
            val back = PdfCropTools.userRectToDisplay(user.lowerLeftX, user.lowerLeftY, user.upperRightX, user.upperRightY, rotation, 300f, 400f)
            assertEquals("rotation $rotation", listOf(15f, 25f, 75f, 95f), listOf(back.left, back.top, back.right, back.bottom).map { Math.round(it).toFloat() })
        }
    }

    @Test
    fun passwordStrengthGrowsWithLengthAndVariety() {
        assertEquals(0, passwordBits(""))
        // Eight lower-case letters: 8 x log2(26), about 37 bits.
        assertEquals(37, passwordBits("abcdefgh"))
        // The same character over and over is one guess per length.
        assertTrue(passwordBits("aaaaaaaaaaaa") < 10)
        assertTrue(passwordBits("Tr4in-Station-Lamp!") > 80)
    }
}
